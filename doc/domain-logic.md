# Domain logic (the parts you cannot guess from the code structure)

## 1. Statement import pipeline

`StatementService` is the orchestrator. The backend is **stateless**: there is no
draft table and no draft cache. The wizard's state lives in the browser.

```
preview(userId, bytes, screenshot)      → StatementPreviewResponse (rows only)
categorize(userId, rows)                → rows with re-applied categories
confirm(userId, ConfirmRequest)         → StatementImportResponse
```

### 1.1 preview — `prepare()`

1. Pick the parser: `screenshot ? screenshotParser : pdfBoxStatementParser`
   (both implement `StatementParser`; the PDF one is injected by concrete type
   because two `@Component`s implement the same interface).
2. Parse → `ParsedStatement` (metadata + `ParsedRow`s + `unrecognizedRows`).
3. Per-row validation:
   - exactly one of `expense`/`income` must be set (XOR) and `amount > 0`,
     otherwise the row goes to `unrecognizedRows`;
   - a `date` outside `period_from..period_to`, or a non-monotonic
     `row_number`, produces a **warning** (row is kept);
   - a `null` date (screenshot above the first header) → unrecognized.
4. Card is **not** created at preview. An overlap check runs against existing
   statements of the same `last4` (`CardService.findByLast4`, read-only) and
   produces a warning if periods intersect. When the parser could not recover a
   card number at all (typical for OCR on a screenshot), `cardNumber` is `null`;
   the wizard then asks the user to pick one of their existing cards or enter a
   new one, and step 3 is blocked until a card number is supplied. The number is
   only resolved into a `Card` at `confirm` (step 1.3).
5. Categorize each transient `Transaction` with `CategorizationEngine`.
6. Return rows: `PreviewTransaction(rowNumber, date, operation, purpose, amount,
   type, categoryId, categoryName, categoryManual)`.

### 1.2 categorize

Re-runs the engine on all rows whose `categoryManual == false`. Manual
assignments are preserved. Used by the "Применить правила" button after the user
creates/changes a rule mid-import.

### 1.3 confirm

1. `CardService.findOrCreateByNumber` (match by last 4; create if absent).
2. Build `Transaction`s: `setCardId`, `setSource` (`screenshot` when
   `parserVersion` starts with `screenshot`, else `statement`), category =
   manual pick or engine result, `setCategoryManual`, `fingerprint(...)`.
3. Dedup: `findExistingFingerprints` (includes soft-deleted rows) → skip
   duplicates, count `skippedDuplicates`.
4. **Only when at least one row is new**: save `Statement` (`txn_count` =
   rows actually stored, not rows in the file) + point the rows at it +
   `saveAll(transactions)`. A re-import that is entirely duplicates creates
   no statement row — it would pollute the statement list, the overlap
   warning and the gap detector while contributing nothing.
5. `StatementGapService.detectGaps(cardId)`.
6. Build `StatementImportResponse(statementId, status, rowCountRaw,
   importedCount, skippedDuplicates, warnings, unrecognizedRows)`;
   `statementId` is `null` when nothing was imported.

`status = unrecognizedRows.isEmpty() ? "OK" : "PARTIAL"`.

### 1.4 Dedup / fingerprint / tombstone

```
fingerprint = SHA-256( userId | cardId | date | amount.toPlainString() | type | purposeNorm )
purposeNorm = purpose.trim().toLowerCase().replaceAll("\\s+", " ")
```

- `TransactionRepository.findExistingFingerprints` deliberately includes
  soft-deleted rows: a transaction the user deleted by hand must **not** be
  resurrected by a re-import.
- `deleteStatement` is the exception: it **hard-deletes** all transactions of a
  statement (including tombstones), otherwise re-uploading the same file would
  skip everything as duplicates.
- The single "Удалить" action in the transaction feed soft-deletes (tombstone).
- The fingerprint is an **import identity**, not a content hash: editing a
  transaction (operation/amount/date, in the feed or in the import preview of a
  saved row) does **not** recompute it, so the same statement row can never be
  imported twice.

### 1.5 Manual card management ("Карты" page)

Cards are still created automatically on confirm, but the "Карты" page adds
explicit CRUD on top:

- **create** — `cardNumber` is required (≥ 4 digits) and its `last4` must not
  already belong to one of the user's cards (a second card with the same last4
  would make `findOrCreateByNumber` ambiguous);
- **update** — `cardNumber` optional; when present the `last4` is re-derived and
  the same uniqueness check runs (excluding the card itself). The statements
  page's inline rename sends only `displayName`;
- **delete** — a hard delete that relies on the schema's `ON DELETE CASCADE`:
  the card's statements, transactions and statement gaps go with it, so
  re-importing the same statement afterwards works.

## 2. Categorization

`CategorizationEngine.categorize(Transaction, List<Rule>)` — rules are pre-sorted
by `priority DESC, id ASC`; the first matching rule wins; no match ⇒
`categoryId = null` (rendered "Без категории" / excluded category id `0`).

Matching is flat AND. Every present key must match:

| key | matches |
|---|---|
| `operation_contains` | `TextNormalizer.normalize(operation).contains(normalize(value))` |
| `operation_regex` | `Pattern.compile(value, CASE_INSENSITIVE|UNICODE_CASE).matcher(operation).find()` |
| `purpose_contains` | `normalize(purpose).contains(normalize(value))` |
| `purpose_regex` | case-insensitive regex on the raw `purpose` |
| `min` / `max` | inclusive amount bounds (either may be absent) |
| `day_from` / `day_to` | inclusive day-of-month range |

`TextNormalizer.normalize` = trim + lowercase(ROOT) + collapse whitespace.
There is **no** prefix stripping and no stored `merchant_normalized` — the
merchant name lives inside the «Операция» column of this bank's feed, and the
user was explicit that automatic normalization was confusing (V7).

A rule with **no recognized condition keys** never matches — this prevents a
stale rule from a previous schema (`merchant_contains`) from matching every row.

`sources of categorization`:
- import (preview + confirm),
- `TransactionService.reCategorizeAll(userId)` — called on rule create/update/delete
  and by `POST /api/rules/apply`; **skips rows with `category_manual = true`**,
- `TransactionService.updateComment` does not touch category.

## 3. Excluded transactions ("исключено")

- A transaction is excluded from analytics when its category is the system
  **"исключено"** category (`Category.isExcluded == true`), assigned by a rule
  or by hand. `AnalyticsService` drops those rows via
  `!excludedCategoryIds.contains(categoryId)`.
- The legacy `transactions.is_transfer` flag (from the removed "это перевод"
  button) is still honoured by the analytics filter for old data, but the
  primary mechanism is now the category.
- "исключено" is seeded once per user (`CategoryService.ensureExcludedCategory`)
  and cannot be deleted.

## 4. Gap detection

`StatementGapService.detectGaps(cardId)`:
- sort the card's statements by `period_from`;
- for each consecutive pair: `DAYS.between(prev.period_to, next.period_from) >
  gapDays` ⇒ record `gap_from = prev.period_to + 1`, `gap_to =
  next.period_from - 1`;
- `gapDays` = `app.gap.days` (default 7);
- idempotent: skip if `(card_id, gap_from, gap_to)` already exists;
- called after every successful confirm and after a statement delete.

`GET /api/statement-gaps` returns all gaps of the current user.

## 5. Parsers

Both implement `StatementParser`:

```java
ParsedStatement parse(byte[] bytes) throws StatementParseException;
default String version();
```

### 5.1 PdfBoxStatementParser (`.pdf`, version `pdfbox-1.0`)

- `PDFTextStripper` subclass `TokenCollector` captures every shown string with
  its `(x, y, page)`.
- Words are grouped into visual lines **per page** (`LINE_TOLERANCE = 5f`);
  page tracking is essential — page-relative `y` collides across pages and
  merges rows (this was a real bug: 19 rows parsed as 11).
- A row starts with a token matching `^\d{1,4}$` **in the row-number column**
  (`ROW_NUMBER_TOLERANCE = 30f`) — this also filters the footer total.
- Amounts: the token matching `^\d+(?:[.,]\d{2})$` nearest to the `Расход`
  (debit) / `Приход` (credit) header x (`COLUMN_TOLERANCE = 60f`).
- Header regexes: `За период с: … по: …`, `Карта: …`, `Договор: …`, `Счет: …`.
- Multi-line «Назначение платежа»: continuation lines whose every token is at
  `x >= purposeColumn - PURPOSE_TOLERANCE` are appended to the previous row.

### 5.2 ScreenshotParser (`.png/.jpg`, version `screenshot-1.0`)

The screenshot is a mobile-bank feed: each operation is a **2-line block**
(title + right-aligned amount; details/category below; optional cashback `+N`
on the details line). Date headers ("Сегодня"/"Вчера"/"29 октября") and
"Вы потратили N ₽" summaries separate groups.

Because Tesseract's own line segmentation merges adjacent visual lines on this
feed (two operations collapse into one), the parser does **geometric blank-space
segmentation** and OCRs **each operation separately**:

1. `sharpen(img)` → grayscale + 3×3 sharpen kernel (crisper glyphs).
2. `segmentUnits(gray)`:
   - ink profile over the **full width** — no column is excluded, because the
     text does not start at a fixed fraction of the image (some feeds have an
     icon gutter, the test fixture has none);
   - a row is empty when `ink <= 0.4% · W`; a block = run of non-empty rows;
   - blocks closer than `3% · W` are joined into one crop (title + details).
     Merging is only an OCR-cost optimisation, never required for correctness:
     the gap between two operations is always much larger, so two operations can
     never land in one crop.
3. For each unit: crop `[y0-3 .. y1+3]`, upscale ×2 (at 540 px wide Tesseract
   regularly mangles the small `₽` glyph), run
   `tesseract <crop> stdout -l rus+eng --psm 6 tsv`, `parseTsv` → `OcrLine`s,
   shift `y` back to full-image space.
4. `parseLines(lines, today)` — the pure, unit-testable state machine. Vertical
   tolerances are **multiples of the measured line pitch** (median distance
   between consecutive lines), never fractions of the image height: on a
   12 000-px feed "1 % of the height" is several lines and would merge rows from
   different operations. `rowTol = 0.6 · pitch` (same visual line),
   `detailsMax = 1.5 · pitch` (details sit one line below the title, the next
   operation two lines below):
   - `EXPECT_TITLE`: a row with an amount opens an operation;
   - `EXPECT_DETAILS`: a text-only row within `detailsMax` becomes the
     operation's details; a positive amount close to the title is **cashback**
     (amount discarded, text kept); a negative amount or a distant positive
     amount opens a new operation;
   - amount-only rows (card balance, "Вы потратили … ₽" summary) are skipped;
   - a line with text but **no amount** becomes an operation with amount `0`:
     deleting a wrong row in the wizard is one click, re-typing a missing
     operation is much more work. `StatementService` keeps such rows and adds a
     warning ("сумма не распознана (0 ₽) — проверьте"); the wizard paints the
     amount amber (`.amount-suspect`). Only text above the first date header
     (`CHROME_TOP = 3 %` of the height) and the bottom nav bar stay out;
   - orphans become `unrecognizedRows`;
   - `operation = cleanOperation(merchant + details)` — keeps only letters,
     digits and `.`, everything else becomes a space;
   - `purpose` is always empty (the screenshot has no purpose field).
5. Date headers: `Сегодня` = today, `Вчера` = today−1, `N <month>` resolved
   against the current year (rolled back one year if it would be in the future).

**Amount anchoring:** the number immediately left of the rightmost currency
marker (`₽` / `руб` / attached `…₽` / `…р` / `…p`) is the amount; everything left
of it is text. A signed token terminates the walk, so "Магазин № 24" is never
part of the amount. This replaced a hardcoded column x that broke on long
merchant names. All dash variants the OCR may emit (`−` U+2212, `-`, `–`
U+2013, `—` U+2014, `―` U+2015) count as a sign — the em dash appears in real
feeds and used to abort the walk, silently dropping the amount. When no marker
survives at all, a number standing right of `0.55 · W` is still taken as the
amount (headers with "карта"/"баланс" are excluded so a card number never turns
into an operation).

**Performance:** each unit = one Tesseract process. A ~90-operation screenshot
means ~100+ processes (~1–2 min). That is why nginx has
`proxy_read_timeout 300s`.

### 5.3 Tests

`PdfBoxStatementParserTest` uses the sanitized fixture
`src/test/resources/ТЕСТОВАЯ_выписка_отп.pdf` (3 pages, 19 rows) — it checks
multi-page parsing and the multi-line purpose join.

`ScreenshotParserTest` tests `parseTsv`/`parseLines`/`segmentUnits` as pure
functions (no OCR binary needed) plus one end-to-end test guarded by
`assumeTrue(tesseractAvailable())` and the sanitized fixture
`screenshot_test.png` (2-line blocks, 4 date groups, cashback). **Never put
real personal data into tests** — see AGENTS.md.

## 6. Analytics

`GET /api/analytics?from&to&exclude=`:
- active, non-transfer, non-excluded transactions in `[from, to]`, minus rows
  whose category is `is_excluded` (the "исключено" category);
- `income` = sum of credits, `expense` = sum of debits, `balance = income − expense`;
- `monthly[]` = every month of the window, zero-filled;
- `categories[]` = credit-only? **no** — expenses grouped by category, sorted
  desc; `null` category becomes `{categoryId: null, categoryName: "Без категории"}`;
- `exclude` = category ids to hide (`0` = uncategorized).

## 7. Transactions outside imports

- Manual entry (`POST /api/transactions`): `source` = `card` (requires
  `cardId`), `cash`, or `other`; `operation` is auto-filled with
  `"добавлено вручную"`; `purpose` is what the user types; row is categorized
  immediately unless a category is picked (`category_manual = true`).
- Delete (feed) = soft delete; delete (statement) = hard delete (see §1.4).
- Comment: `PUT /api/transactions/{id}/comment` (`null`/blank clears).
- Category: `POST /api/transactions/{id}/category` marks `category_manual`.

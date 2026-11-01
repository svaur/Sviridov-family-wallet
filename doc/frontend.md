# Frontend (React 18 + TypeScript + Vite)

Dev entry: `src/main.tsx` → `App.tsx`. Backend is reached through Vite's proxy
(`/api`, `/actuator` → `http://localhost:8080`) in dev, and through nginx in
production.

## Routing (`src/App.tsx`)

```
/login                          LoginPage (standalone, no shell)
/                               RequireAuth → Layout
  index                         DashboardPage
  /dashboard                    → Navigate to "/"
  /transactions                 TransactionsPage
  /statements                   StatementsPage
  /cards                        CardsPage
  /import                       ImportPage (?kind=screenshot for OCR mode)
  /rules                        RulesPage
  *                             → Navigate to "/"
```

`RequireAuth` shows "Загрузка…" while `AuthContext` restores the session
(`GET /api/auth/me`) and redirects to `/login` with `state.from` when anonymous.

## Shell

`components/Layout.tsx` — dark sidebar `#151238` (240px) with:
Дашборд `/`, Операции `/transactions`, Выписки `/statements`, Карты `/cards`,
Правила `/rules`; at the bottom an avatar (first letter of the username) + logout.

## Pages

| Page | What it does |
|---|---|
| `DashboardPage` | period picker "с"/"по" (two `<input type="date">`, default trailing 30 days; drives every widget); 4 stat cards (Доход/Расход/Баланс/Карты) with trends vs the previous period of the same length; Recharts AreaChart (monthly, zero-filled); top-categories bars; "Самые крупные покупки" (debits, non-excluded, in period, sorted desc, top 5) with inline comment editing; category-exclude checkboxes (querystring `?exclude=`) |
| `TransactionsPage` | feed with client-side filters (search/type/category/source); category pill with icon; "исключено" (muted) when the category is the system excluded one; expanded row: CategoryPicker, edit rule, delete; "+ Добавить операцию" manual form (hidden behind a button) |
| `StatementsPage` | "+ Загрузить выписку" / "+ Загрузить скрин ленты"; upload-history table with inline card rename, status badges, delete statement (hard delete); "Пропуски в выписках" table |
| `CardsPage` | card CRUD: list (номер/название/валюта/дата), "+ Добавить карту", inline edit form (number + name), delete with a confirm that warns about the cascade |
| `ImportPage` | 3-step stateless wizard: choose file → preview/edit rows → confirm. Holds rows in React state; edits/deletes/adds are local; "Применить правила" calls `POST /api/statements/categorize`; import calls `POST /api/statements/confirm`. Screenshot mode (`?kind=screenshot`) shows a red warning banner and accepts images. If the card number was not recognized, step 2 shows a card picker (existing cards from `GET /api/cards` + "＋ Новая карта" manual entry) and "Далее" is disabled until a card is chosen |
| `RulesPage` | categories as chips (click to edit, × to delete, icon picker); rules table; "Создать правило" form with flat conditions (Назначение платежа / Операция with regex auto-detect, sum range, day range); "Применить правила" button (`POST /api/rules/apply`) |
| `LoginPage` | centered card, brand, placeholders, full-width submit, note |

## Components

| Component | Purpose |
|---|---|
| `RequireAuth` | auth guard |
| `CategoryPicker` | "Назначить категорию вручную" → select (+ "создать новую") → assign |
| `CategorySelect` | `<select>` with an inline "＋ Создать новую категорию" form |
| `CommentField` | inline comment editor ("＋ Комментарий" / "💬 …") |
| `RuleQuickForm` | compact inline rule form (prefilled from a transaction); shows an inline mismatch warning instead of `window.confirm` |

## Helpers

- `api.ts` — `api.get/post/put/del/upload`; `HttpError` with `status` +
  `fieldErrors`; JSON error body parsing.
- `format.ts` — `fmtDate` (`DD.MM.YYYY`), `fmtInstant` (`Europe/Moscow`),
  `fmtMoney` (`1 234,56 ₽`, `Intl.NumberFormat ru-RU`).
- `ruleHelpers.ts` — `isRegex`, `normalizeCore`, `regexFind`,
  `buildRuleParams`, `ruleMatchesTemplate` (used by `RuleQuickForm` to warn when
  a rule would not match the row it was built from).
- `icons.ts` — `CATEGORY_ICONS` (23 emoji) + `DEFAULT_ICON = '📦'`.

## Design system (`src/index.css`)

CSS variables: `--sidebar #151238`, `--sidebar-active #221d4f`, `--bg #f6f7fb`,
`--card #fff`, `--border #e7e9f2`, `--text #1c1b2e`, `--muted #8a8ba3`,
`--accent #6366f1` (hover `#4f46e5`), `--green #10b981`, `--red #ef4444`.
Cards: radius 14px; buttons/inputs: radius 9px; badges: pill 999px.

Keep class names stable across pages (`.page`, `.card`, `.card-head`, `.table`,
`.pill`, `.badge-*`, `.amount`, `.tx-row.open`, `.rule-quick-form`,
`.category-filter`, `.icon-picker`, …).

## Conventions

- No global state library — `useState` + `useEffect` per page, `AuthContext` for
  the session.
- All money via `fmtMoney`, all dates via `fmtDate`/`fmtInstant`.
- Every fetch error is an `HttpError`; show `err.message` and, when present,
  `err.fieldErrors`.

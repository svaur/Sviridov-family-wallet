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
  /settings                     SettingsPage
  /change-password              ForcePasswordChangePage (forced after an admin reset)
  *                             → Navigate to "/"
```

`RequireAuth` shows "Загрузка…" while `AuthContext` restores the session
(`GET /api/me`) and redirects to `/login` with `state.from` when anonymous. Any
`401` from `api.ts` dispatches `wallet:unauthorized`; `AuthProvider` clears the
user on it, so a session killed elsewhere (password change, server restart)
lands on the login page instead of leaving error banners on every screen.
`RequireAuth` also redirects to `/change-password` when `user.mustChangePassword`
is true (set after an admin password reset).

## Shell

`components/Layout.tsx` — dark sidebar `#151238` (240px) with:
Дашборд `/`, Операции `/transactions`, Выписки `/statements`, Карты `/cards`,
Правила `/rules`, Настройки `/settings`; at the bottom an avatar (initial of
`firstName`, else of the login — clicking it opens the settings) and
`displayName(user)`: "Иван Свиридов", falling back to the login when no name is
set. The name is shown **only** here — the dashboard has no greeting.

## Pages

| Page | What it does |
|---|---|
| `DashboardPage` | period picker "с"/"по" (two `<input type="date">`, default trailing 30 days; drives every widget); 4 stat cards (Доход/Расход/Баланс/Карты) with trends vs the previous period of the same length; Recharts donut PieChart of expense categories (top 5 + "Прочее"); top-categories bars; "Самые крупные покупки" (debits, non-excluded, in period, sorted desc, top 5) with inline comment editing; category-exclude checkboxes (querystring `?exclude=`) |
| `TransactionsPage` | feed with client-side filters (search/type/category/source); category pill with icon; "исключено" (muted) when the category is the system excluded one; expanded row: CategoryPicker, inline edit of operation/amount/date, rule form, delete; "+ Добавить операцию" manual form (hidden behind a button) |
| `StatementsPage` | "+ Загрузить выписку" / "+ Загрузить скрин ленты"; upload-history table with inline card rename, status badges, delete statement (hard delete); "Пропуски в выписках" table |
| `SettingsPage` | two cards: «Профиль» (Имя/Фамилия, save disabled until changed, inline "Сохранено", shows the resulting `displayName`) and «Безопасность» (current/new/repeat password; client checks length ≥ 8, mismatch and "same as current"; on success logs out and lands on `/login` with "Пароль изменён. Войдите снова.") |
| `ForcePasswordChangePage` | standalone change-password card (no shell) shown when `mustChangePassword`; temp+new+repeat with client checks, then `PUT /api/me/password` → logout → `/login` |
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

- `http.ts` — the shared fetch core: `createApi({loginPath, unauthorizedEvent})`
  returns a typed `HttpClient` and dispatches a custom event on 401 outside the
  login path. `api.ts` (wallet) and `admin/api.ts` (admin) are thin wrappers.
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

## Admin SPA (`src/admin/`)

A separate bundle and entry (`admin.html` → `src/admin/main.tsx`), served at
`/admin` by nginx and built alongside the wallet (`vite.config.ts` declares two
`build.rollupOptions.input` entries). It uses a `HashRouter` so all sub-routes
are client-side and nginx only needs one `/admin → admin.html` mapping.

- Auth is independent: `admin/api.ts` = `createApi({ loginPath:
  '/api/admin/auth/login', unauthorizedEvent: 'admin:unauthorized' })`; state
  lives in `AdminAuthContext.tsx` (`AdminUser{id, username, mustChangePassword}`),
  never shared with the wallet `AuthContext`.
- Routes (`AdminApp.tsx`): `/login` → `AdminLoginPage`; nested `/` under
  `RequireAdmin` + `AdminLayout` with `index` → `AdminHomePage` («Обзор»),
  `users` → `AdminUsersPage`, `audit` → `AdminAuditPage`, `server` →
  `AdminServerPage`.
- `AdminLayout.tsx`: sidebar brand «Админ», group «Управление» with «Обзор» /
  «👥 Пользователи» / «📋 Аудит» / «🖥 Сервер», bottom avatar + username + logout.
- `AdminUsersPage.tsx`: user table (Логин/Имя/Статус/Последний вход/Действия),
  create form, ban/unban, reset-password (shows the one-time temp password in a
  `.notice`), force-logout, two-step inline delete confirm.
- `AdminAuditPage.tsx`: audit table (Время/Действие/Объект/IP) with «← Назад» /
  «Вперёд →» pagination over `GET /api/admin/audit`.
- `AdminServerPage.tsx`: «Состояние» card (`.kv` grid: версия, БД, аптайм,
  последний импорт, JVM, диск) + «Данные» card (`.stats` grid of counts),
  «Обновить» button.

## Conventions

- No global state library — `useState` + `useEffect` per page, `AuthContext` for
  the session.
- All money via `fmtMoney`, all dates via `fmtDate`/`fmtInstant`.
- Every fetch error is an `HttpError`; show `err.message` and, when present,
  `err.fieldErrors`.

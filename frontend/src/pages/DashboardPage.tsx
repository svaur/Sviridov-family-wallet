import { useEffect, useMemo, useState } from 'react'
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { api, HttpError } from '../api'
import { CommentField } from '../components/CommentField'
import { fmtMoney } from '../format'
import { DEFAULT_ICON } from '../icons'

interface MonthlyPoint {
  month: string
  income: number
  expense: number
}

interface CategoryPoint {
  categoryId: number | null
  categoryName: string
  amount: number
}

interface Analytics {
  from: string
  to: string
  income: number
  expense: number
  balance: number
  monthly: MonthlyPoint[]
  categories: CategoryPoint[]
}

interface Category {
  id: number
  name: string
  icon: string
}

interface Card {
  id: number
  cardNumber: string
  displayName: string
  last4: string
  archived: boolean
}

interface Transaction {
  id: number
  date: string
  operation: string | null
  purpose: string | null
  amount: number
  type: string
  categoryId: number | null
  categoryName: string | null
  source: string
  isTransfer: boolean
  comment: string | null
}

const CAT_COLORS = ['#6366f1', '#a855f7', '#10b981', '#f59e0b', '#ec4899', '#64748b']

/** Sentinel id used by the analytics API for the "uncategorized" bucket. */
const UNCATEGORIZED = 0

function iso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function defaultMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function shortMonth(month: string): string {
  const [, m] = month.split('-')
  const names = ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек']
  return names[Number(m) - 1]
}

/** start (month), end (last day), yearStart (11 months before start). */
function monthRange(m: string) {
  const [y, mo] = m.split('-').map(Number)
  const start = new Date(y, mo - 1, 1)
  const end = new Date(y, mo, 0)
  const yearStart = new Date(y, mo - 12, 1)
  return { monthStart: iso(start), monthEnd: iso(end), yearStart: iso(yearStart) }
}

function pct(cur: number, prev: number): string | null {
  if (prev === 0) return null
  const d = ((cur - prev) / prev) * 100
  const sign = d >= 0 ? '+' : '−'
  return `${sign}${Math.abs(d).toFixed(1)}%`
}

export function DashboardPage() {
  const [month, setMonth] = useState(defaultMonth())
  const [yearData, setYearData] = useState<Analytics | null>(null)
  const [monthData, setMonthData] = useState<Analytics | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [excluded, setExcluded] = useState<Set<number>>(new Set())
  const [showFilter, setShowFilter] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const iconById = useMemo(() => new Map(categories.map((c) => [c.id, c.icon])), [categories])

  async function load(m: string, ex: Set<number>) {
    setLoading(true)
    setError(null)
    const { monthStart, monthEnd, yearStart } = monthRange(m)
    const q = ex.size > 0 ? `&exclude=${Array.from(ex).join(',')}` : ''
    try {
      const [yr, mo, cs, tx] = await Promise.all([
        api.get<Analytics>(`/api/analytics?from=${yearStart}&to=${monthEnd}${q}`),
        api.get<Analytics>(`/api/analytics?from=${monthStart}&to=${monthEnd}${q}`),
        api.get<Card[]>('/api/cards'),
        api.get<Transaction[]>('/api/transactions'),
      ])
      setYearData(yr)
      setMonthData(mo)
      setCards(cs)
      setTransactions(tx)
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить данные')
    } finally {
      setLoading(false)
    }
  }

  async function loadCategories() {
    try {
      setCategories(await api.get<Category[]>('/api/categories'))
    } catch {
      // Non-fatal: the filter and icons simply stay empty.
    }
  }

  useEffect(() => {
    load(defaultMonth(), new Set())
    loadCategories()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function toggleCategory(id: number) {
    const next = new Set(excluded)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    setExcluded(next)
    load(month, next)
  }

  function onMonthChange(e: React.ChangeEvent<HTMLInputElement>) {
    const m = e.target.value
    setMonth(m)
    if (m) load(m, excluded)
  }

  const monthly = (yearData?.monthly ?? []).map((p) => ({
    month: shortMonth(p.month),
    income: p.income,
    expense: p.expense,
  }))

  const n = (yearData?.monthly ?? []).length
  const cur = n > 0 ? yearData!.monthly[n - 1] : null
  const prev = n > 1 ? yearData!.monthly[n - 2] : null
  const balance = cur ? cur.income - cur.expense : 0
  const prevBalance = prev ? prev.income - prev.expense : 0
  const balanceDelta = prev ? balance - prevBalance : 0

  const topCategories = (monthData?.categories ?? []).slice(0, 6)
  const maxCat = topCategories.length > 0 ? topCategories[0].amount : 0

  const activeCards = cards.filter((c) => !c.archived)

  const topPurchases = useMemo(() => {
    return transactions
      .filter((t) => t.type === 'debit' && !t.isTransfer)
      .sort((a, b) => Number(b.amount) - Number(a.amount))
      .slice(0, 5)
  }, [transactions])

  function updateCommentLocal(id: number, comment: string | null) {
    setTransactions((prev) => prev.map((t) => (t.id === id ? { ...t, comment } : t)))
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>Обзор финансов</h1>
        <input
          type="month"
          className="month-input"
          value={month}
          onChange={onMonthChange}
          aria-label="Период"
        />
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <p>Загрузка…</p>
      ) : (
        <>
          <div className="stats">
            <div className="stat">
              <div className="lbl"><span>⬇</span>Доход</div>
              <div className="val pos">{fmtMoney(cur?.income ?? 0)}</div>
              {cur && prev && (
                <div className={`trend ${(cur.income - prev.income) >= 0 ? 'up' : 'down'}`}>
                  {(cur.income - prev.income) >= 0 ? '▲' : '▼'} {pct(cur.income, prev.income)}
                </div>
              )}
            </div>
            <div className="stat">
              <div className="lbl"><span>⬆</span>Расход</div>
              <div className="val neg">{fmtMoney(cur?.expense ?? 0)}</div>
              {cur && prev && (
                <div className={`trend ${(cur.expense - prev.expense) <= 0 ? 'up' : 'down'}`}>
                  {(cur.expense - prev.expense) <= 0 ? '▼' : '▲'} {pct(cur.expense, prev.expense)}
                </div>
              )}
            </div>
            <div className="stat">
              <div className="lbl"><span>≈</span>Баланс</div>
              <div className={`val ${balance >= 0 ? 'pos' : 'neg'}`}>{fmtMoney(balance)}</div>
              {prev && (
                <div className={`trend ${balanceDelta >= 0 ? 'up' : 'down'}`}>
                  {balanceDelta >= 0 ? '▲' : '▼'} {balanceDelta >= 0 ? '+' : '−'}{fmtMoney(Math.abs(balanceDelta))}
                </div>
              )}
            </div>
            <div className="stat">
              <div className="lbl"><span>◔</span>Карты</div>
              <div className="val">{activeCards.length}</div>
              <div className="trend">
                {activeCards.length > 0 ? activeCards.map((c) => `· ${c.last4}`).join(' ') : 'нет карт'}
              </div>
            </div>
          </div>

          <div className="grid">
            <section className="card">
              <h2>Динамика за год</h2>
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={monthly}>
                  <defs>
                    <linearGradient id="incGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stopColor="#6366f1" stopOpacity={0.25} />
                      <stop offset="1" stopColor="#6366f1" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="expGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stopColor="#a855f7" stopOpacity={0.2} />
                      <stop offset="1" stopColor="#a855f7" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef0f6" />
                  <XAxis dataKey="month" />
                  <YAxis />
                  <Tooltip formatter={(value) => fmtMoney(Number(value))} />
                  <Area type="monotone" dataKey="income" name="Доход" stroke="#6366f1" fill="url(#incGrad)" strokeWidth={2.5} />
                  <Area type="monotone" dataKey="expense" name="Расход" stroke="#a855f7" fill="url(#expGrad)" strokeWidth={2.5} />
                </AreaChart>
              </ResponsiveContainer>
            </section>

            <section className="card">
              <div className="card-head">
                <h2>Топ категорий</h2>
                <button type="button" className="btn-ghost btn-sm" onClick={() => setShowFilter(!showFilter)}>
                  ⚙ Настроить
                </button>
              </div>
              {showFilter && (
                <div className="category-filter" style={{ marginBottom: 14 }}>
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={!excluded.has(UNCATEGORIZED)}
                      onChange={() => toggleCategory(UNCATEGORIZED)}
                    />
                    <span>Без категории</span>
                  </label>
                  {categories.map((c) => (
                    <label key={c.id} className="checkbox">
                      <input
                        type="checkbox"
                        checked={!excluded.has(c.id)}
                        onChange={() => toggleCategory(c.id)}
                      />
                      <span>{c.icon} {c.name}</span>
                    </label>
                  ))}
                  {categories.length === 0 && <span className="muted">Категорий пока нет</span>}
                </div>
              )}
              {topCategories.length === 0 ? (
                <p className="muted">Нет расходов за период</p>
              ) : (
                <div className="cat-list">
                  {topCategories.map((c, i) => (
                    <div className="cat-row" key={c.categoryId ?? 'uncategorized'}>
                      <div className="top">
                        <span className="nm">{iconById.get(c.categoryId ?? 0) ?? DEFAULT_ICON} {c.categoryName}</span>
                        <span className="sm">{fmtMoney(c.amount)}</span>
                      </div>
                      <div className="track">
                        <div
                          className="fill"
                          style={{ width: maxCat > 0 ? `${(c.amount / maxCat) * 100}%` : '0%', background: CAT_COLORS[i % CAT_COLORS.length] }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>

          <section className="card">
            <h2>Самые крупные покупки</h2>
            {topPurchases.length === 0 ? (
              <p className="muted">Покупок пока нет</p>
            ) : (
              <div>
                {topPurchases.map((t) => (
                  <div className="tx" key={t.id}>
                    <div className="ic" style={{ background: '#eef2ff' }}>
                      {iconById.get(t.categoryId ?? 0) ?? DEFAULT_ICON}
                    </div>
                    <div className="info">
                      <div className="t">{t.operation ?? t.purpose ?? 'Покупка'}</div>
                      <div className="d">{t.date} · {t.categoryName ?? 'без категории'}</div>
                    </div>
                    <div className="comment-col">
                      <CommentField
                        id={t.id}
                        comment={t.comment}
                        onSaved={(c) => updateCommentLocal(t.id, c)}
                      />
                    </div>
                    <div className="amt minus">−{fmtMoney(t.amount)}</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}

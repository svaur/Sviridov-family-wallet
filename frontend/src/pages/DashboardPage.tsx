import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'
import { fmtMoney } from '../format'

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
}

const PIE_COLORS = ['#2563eb', '#16a34a', '#dc2626', '#d97706', '#7c3aed', '#0891b2', '#db2777', '#65a30d']

/** Sentinel id used by the analytics API for the "uncategorized" bucket. */
const UNCATEGORIZED = 0

function iso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function defaultRange(): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to.getFullYear(), to.getMonth() - 11, 1)
  return { from: iso(from), to: iso(to) }
}

function monthLabel(month: string): string {
  const [y, m] = month.split('-')
  return `${m}.${y}`
}

export function DashboardPage() {
  const { user, logout } = useAuth()
  const [from, setFrom] = useState(defaultRange().from)
  const [to, setTo] = useState(defaultRange().to)
  const [data, setData] = useState<Analytics | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [excluded, setExcluded] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load(f: string, t: string, ex: Set<number>) {
    setLoading(true)
    setError(null)
    const q = ex.size > 0 ? `&exclude=${Array.from(ex).join(',')}` : ''
    try {
      setData(await api.get<Analytics>(`/api/analytics?from=${f}&to=${t}${q}`))
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить аналитику')
    } finally {
      setLoading(false)
    }
  }

  async function loadCategories() {
    try {
      setCategories(await api.get<Category[]>('/api/categories'))
    } catch {
      // Non-fatal: the filter simply stays empty.
    }
  }

  useEffect(() => {
    const r = defaultRange()
    load(r.from, r.to, new Set())
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
    load(from, to, next)
  }

  const monthly = (data?.monthly ?? []).map((p) => ({
    month: monthLabel(p.month),
    Доход: p.income,
    Расход: p.expense,
  }))

  const categoriesData = (data?.categories ?? []).map((c) => ({
    name: c.categoryName,
    value: c.amount,
  }))

  return (
    <div className="page">
      <header className="topbar">
        <h1>Дашборд</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/statements">Выписки</Link>
          <Link className="link-btn" to="/rules">Правила</Link>
          <Link className="link-btn" to="/transactions">Операции</Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      <div className="filter-bar">
        <label className="field">
          <span>С</span>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="field">
          <span>По</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button onClick={() => load(from, to, excluded)}>Показать</button>
      </div>

      <section className="card">
        <h2>Категории на дашборде</h2>
        <div className="category-filter">
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
              <span>{c.name}</span>
            </label>
          ))}
          {categories.length === 0 && <span className="muted">Категорий пока нет</span>}
        </div>
      </section>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <p>Загрузка…</p>
      ) : (
        data && (
          <>
            <div className="kpi-grid">
              <div className="card">
                <div className="muted">Доход</div>
                <div className="kpi-value pos">{fmtMoney(data.income)}</div>
              </div>
              <div className="card">
                <div className="muted">Расход</div>
                <div className="kpi-value neg">{fmtMoney(data.expense)}</div>
              </div>
              <div className="card">
                <div className="muted">Баланс</div>
                <div className={`kpi-value ${data.balance >= 0 ? 'pos' : 'neg'}`}>
                  {fmtMoney(data.balance)}
                </div>
              </div>
            </div>

            <section className="card">
              <h2>Доходы и расходы по месяцам</h2>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={monthly}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="month" />
                  <YAxis />
                  <Tooltip formatter={(value) => fmtMoney(Number(value))} />
                  <Legend />
                  <Bar dataKey="Доход" fill="#16a34a" />
                  <Bar dataKey="Расход" fill="#dc2626" />
                </BarChart>
              </ResponsiveContainer>
            </section>

            <section className="card">
              <h2>Расходы по категориям</h2>
              {categoriesData.length === 0 ? (
                <p className="muted">Нет расходов за период</p>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie data={categoriesData} dataKey="value" nameKey="name" outerRadius={100}>
                      {categoriesData.map((_, i) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value) => fmtMoney(Number(value))} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </section>
          </>
        )
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'
import { fmtDate, fmtMoney } from '../format'

interface Transaction {
  id: number
  date: string
  operation: string
  purpose: string | null
  merchantNormalized: string | null
  amount: number | string
  type: string
  categoryId: number | null
  categoryName: string | null
}

export function TransactionsPage() {
  const { user, logout } = useAuth()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    setLoading(true)
    try {
      setTransactions(await api.get<Transaction[]>('/api/transactions'))
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить операции')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <div className="page">
      <header className="topbar">
        <h1>Операции</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/">← Карты</Link>
          <Link className="link-btn" to="/rules">Правила</Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section>
        {loading ? (
          <p>Загрузка…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Дата</th>
                <th>Операция</th>
                <th>Назначение платежа</th>
                <th>Категория</th>
                <th style={{ textAlign: 'right' }}>Сумма</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.id}>
                  <td>{fmtDate(t.date)}</td>
                  <td>{t.operation}</td>
                  <td>{t.purpose}</td>
                  <td>{t.categoryName ?? '—'}</td>
                  <td className={t.type === 'debit' ? 'amount-debit' : 'amount-credit'}>
                    {t.type === 'debit' ? '− ' : '+ '}
                    {fmtMoney(t.amount)}
                  </td>
                </tr>
              ))}
              {transactions.length === 0 && (
                <tr>
                  <td colSpan={5}>Операций пока нет</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

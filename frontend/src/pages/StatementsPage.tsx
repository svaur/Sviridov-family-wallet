import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'
import { fmtDate, fmtInstant } from '../format'

interface Card {
  id: number
  displayName: string
  bank: string | null
  last4: string | null
  currency: string
  archived: boolean
}

interface Statement {
  id: number
  cardId: number
  uploadedAt: string
  periodFrom: string | null
  periodTo: string | null
  parserVersion: string
  status: string
  txnCount: number
  rowCountRaw: number
  cardNumberExtracted: string | null
  contract: string | null
  account: string | null
}

export function StatementsPage() {
  const { cardId } = useParams()
  const { user, logout } = useAuth()
  const [card, setCard] = useState<Card | null>(null)
  const [statements, setStatements] = useState<Statement[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const id = Number(cardId)

  async function load() {
    setLoading(true)
    try {
      const cards = await api.get<Card[]>('/api/cards')
      setCard(cards.find((c) => c.id === id) ?? null)
      setStatements(await api.get<Statement[]>(`/api/cards/${id}/statements`))
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить данные')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [id])

  return (
    <div className="page">
      <header className="topbar">
        <h1>Выписки — {card?.displayName ?? `карта #${id}`}</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/cards">
            ← Карты
          </Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>Загрузить выписку</h2>
        <p className="muted">PDF до 10 МБ. Загрузка проходит в три шага: файл → проверка категоризации → подтверждение.</p>
        <Link className="link-btn" to={`/cards/${id}/import`}>
          Загрузить выписку
        </Link>
      </section>

      <section>
        {loading ? (
          <p>Загрузка…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Загружена</th>
                <th>Период</th>
                <th>Строк</th>
                <th>Статус</th>
                <th>Карта в выписке</th>
              </tr>
            </thead>
            <tbody>
              {statements.map((s) => (
                <tr key={s.id}>
                  <td>{fmtInstant(s.uploadedAt)}</td>
                  <td>
                    {fmtDate(s.periodFrom)} — {fmtDate(s.periodTo)}
                  </td>
                  <td>
                    {s.txnCount} / {s.rowCountRaw}
                  </td>
                  <td>{s.status}</td>
                  <td>{s.cardNumberExtracted ?? '—'}</td>
                </tr>
              ))}
              {statements.length === 0 && (
                <tr>
                  <td colSpan={5}>Выписок пока нет</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'

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

interface ImportResult {
  statementId: number
  status: string
  rowCountRaw: number
  importedCount: number
  skippedDuplicates: number
  warnings: string[]
  unrecognizedRows: string[]
}

function fmtDate(d: string | null) {
  if (!d) return '—'
  const [y, m, dd] = d.split('-')
  return dd && m && y ? `${dd}.${m}.${y}` : d
}

function fmtInstant(iso: string) {
  return new Date(iso).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
}

export function StatementsPage() {
  const { cardId } = useParams()
  const { user, logout } = useAuth()
  const [card, setCard] = useState<Card | null>(null)
  const [statements, setStatements] = useState<Statement[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [mismatch, setMismatch] = useState<string | null>(null)

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

  async function onUpload(e: FormEvent) {
    e.preventDefault()
    const input = e.currentTarget.querySelector('input[type="file"]') as HTMLInputElement
    const file = input.files?.[0]
    if (!file) {
      setError('Выберите файл')
      return
    }
    setUploading(true)
    setError(null)
    setResult(null)
    setMismatch(null)
    try {
      setResult(await api.upload<ImportResult>(`/api/cards/${id}/statements`, file))
      input.value = ''
      await load()
    } catch (err) {
      if (err instanceof HttpError && err.status === 409) {
        setMismatch(err.message)
      } else {
        setError(err instanceof HttpError ? err.message : 'Не удалось загрузить выписку')
      }
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="page">
      <header className="topbar">
        <h1>Выписки — {card?.displayName ?? `карта #${id}`}</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/">
            ← Карты
          </Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>Загрузить выписку (PDF, до 10 МБ)</h2>
        <form onSubmit={onUpload}>
          <div className="row">
            <input type="file" accept="application/pdf" />
            <button type="submit" disabled={uploading}>
              {uploading ? 'Загрузка…' : 'Загрузить'}
            </button>
          </div>
        </form>

        {mismatch && (
          <div className="warning">
            <p>{mismatch}</p>
            <Link className="link-btn" to="/">
              Создать карту
            </Link>
          </div>
        )}

        {result && (
          <div className="import-result">
            <p>
              Статус: <strong>{result.status}</strong> · распознано строк {result.rowCountRaw} ·
              импортировано {result.importedCount} · пропущено дубликатов {result.skippedDuplicates}
            </p>
            {result.warnings.length > 0 && (
              <ul>
                {result.warnings.map((w, i) => (
                  <li key={i} className="warning">
                    {w}
                  </li>
                ))}
              </ul>
            )}
            {result.unrecognizedRows.length > 0 && (
              <ul>
                {result.unrecognizedRows.map((r, i) => (
                  <li key={i} className="error">
                    {r}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
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

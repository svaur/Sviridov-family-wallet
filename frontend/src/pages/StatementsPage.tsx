import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { fmtDate, fmtInstant } from '../format'

interface Card {
  id: number
  cardNumber: string
  displayName: string
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

interface Gap {
  id: number
  cardId: number
  gapFrom: string
  gapTo: string
  detectedAt: string
}

export function StatementsPage() {
  const [cards, setCards] = useState<Card[]>([])
  const [statements, setStatements] = useState<Statement[]>([])
  const [cardFilter, setCardFilter] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [renamingCardId, setRenamingCardId] = useState<number | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [gaps, setGaps] = useState<Gap[]>([])

  async function load() {
    setLoading(true)
    try {
      const [allCards, allStatements, allGaps] = await Promise.all([
        api.get<Card[]>('/api/cards'),
        api.get<Statement[]>('/api/statements'),
        api.get<Gap[]>('/api/statement-gaps'),
      ])
      setCards(allCards)
      setStatements(allStatements)
      setGaps(allGaps)
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить данные')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  function cardOf(id: number): Card | undefined {
    return cards.find((c) => c.id === id)
  }

  function startRename(card: Card) {
    setRenamingCardId(card.id)
    setRenameValue(card.displayName)
  }

  async function saveRename() {
    if (renamingCardId === null) return
    setError(null)
    try {
      await api.put<Card>(`/api/cards/${renamingCardId}`, { displayName: renameValue })
      setRenamingCardId(null)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось переименовать карту')
    }
  }

  const visible = cardFilter
    ? statements.filter((s) => s.cardId === Number(cardFilter))
    : statements

  const lastGap = gaps.length > 0 ? gaps[gaps.length - 1] : null

  function statusBadge(status: string): string {
    if (status === 'OK') return 'badge-ok'
    if (status === 'PARTIAL') return 'badge-partial'
    return 'badge-other'
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>Выписки</h1>
        <Link className="link-btn filled" to="/import">+ Загрузить выписку</Link>
      </div>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>Загрузка выписки из PDF</h2>
        <div className="steps">
          <span>1. <b>Выбрать файл</b></span>
          <span>→</span>
          <span>2. <b>Проверить категории</b></span>
          <span>→</span>
          <span>3. <b>Подтвердить импорт</b></span>
        </div>
        <p className="muted">PDF до 10 МБ. Карта создаётся автоматически по номеру из выписки, сам файл не хранится.</p>
      </section>

      {lastGap && (
        <div className="warning">
          ⚠ Обнаружен пропуск по карте {cardOf(lastGap.cardId)?.displayName ?? '—'}: {fmtDate(lastGap.gapFrom)} — {fmtDate(lastGap.gapTo)}
          {gaps.length > 1 ? ` (ещё ${gaps.length - 1})` : ''}
        </div>
      )}

      <section className="card">
        <div className="card-head">
          <h2>История выгрузок</h2>
          <label className="field" style={{ marginBottom: 0 }}>
            <span className="muted">Карта</span>
            <select value={cardFilter} onChange={(e) => setCardFilter(e.target.value)}>
              <option value="">Все карты</option>
              {cards.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName}
                </option>
              ))}
            </select>
          </label>
        </div>

        {loading ? (
          <p>Загрузка…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Загружена</th>
                <th>Период</th>
                <th>Карта</th>
                <th>Строк</th>
                <th>Статус</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((s) => {
                const card = cardOf(s.cardId)
                return (
                  <tr key={s.id}>
                    <td>{fmtInstant(s.uploadedAt)}</td>
                    <td>
                      {fmtDate(s.periodFrom)} — {fmtDate(s.periodTo)}
                    </td>
                    <td>
                      {renamingCardId === s.cardId ? (
                        <div className="row">
                          <input
                            value={renameValue}
                            onChange={(e) => setRenameValue(e.target.value)}
                            autoFocus
                          />
                          <button className="btn-sm" onClick={saveRename}>Сохранить</button>
                          <button className="btn-secondary btn-sm" onClick={() => setRenamingCardId(null)}>Отмена</button>
                        </div>
                      ) : (
                        <div className="row">
                          <span>{card?.displayName ?? '—'}</span>
                          {card && (
                            <button className="text-link" onClick={() => startRename(card)}>
                              переименовать
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                    <td>
                      {s.txnCount} / {s.rowCountRaw}
                    </td>
                    <td>
                      <span className={`badge ${statusBadge(s.status)}`}>{s.status}</span>
                    </td>
                  </tr>
                )
              })}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={5}>Выписок пока нет</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>

      {gaps.length > 0 && (
        <section className="card">
          <h2>Пропуски в выписках</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Карта</th>
                <th>Период пропуска</th>
                <th>Обнаружен</th>
              </tr>
            </thead>
            <tbody>
              {gaps.map((g) => (
                <tr key={g.id}>
                  <td>{cardOf(g.cardId)?.displayName ?? '—'}</td>
                  <td>
                    <span className="gap-tag">{fmtDate(g.gapFrom)} — {fmtDate(g.gapTo)}</span>
                  </td>
                  <td>{fmtInstant(g.detectedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}

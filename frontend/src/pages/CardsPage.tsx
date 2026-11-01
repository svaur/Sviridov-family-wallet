import { useEffect, useState, type FormEvent } from 'react'
import { api, HttpError } from '../api'
import { fmtInstant } from '../format'

interface Card {
  id: number
  cardNumber: string | null
  displayName: string
  last4: string
  currency: string
  archived: boolean
  createdAt: string
}

function numberLabel(card: Card): string {
  return card.cardNumber || (card.last4 ? `•••• ${card.last4}` : '—')
}

export function CardsPage() {
  const [cards, setCards] = useState<Card[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [cardNumber, setCardNumber] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  async function load() {
    setLoading(true)
    try {
      setCards(await api.get<Card[]>('/api/cards'))
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить карты')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  function startCreate() {
    setEditId(null)
    setCardNumber('')
    setDisplayName('')
    setShowForm(true)
    setError(null)
  }

  function startEdit(card: Card) {
    setEditId(card.id)
    setCardNumber(card.cardNumber ?? '')
    setDisplayName(card.displayName)
    setShowForm(true)
    setError(null)
  }

  function closeForm() {
    setShowForm(false)
    setEditId(null)
    setCardNumber('')
    setDisplayName('')
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      if (editId === null) {
        await api.post<Card>('/api/cards', { cardNumber, displayName })
        setMsg('Карта добавлена')
      } else {
        await api.put<Card>(`/api/cards/${editId}`, { cardNumber, displayName })
        setMsg('Карта сохранена')
      }
      closeForm()
      await load()
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось сохранить карту')
    } finally {
      setSaving(false)
    }
  }

  async function remove(card: Card) {
    const ok = window.confirm(
      `Удалить карту «${card.displayName}»? Вместе с ней будут удалены все её выписки, операции и пропуски.`,
    )
    if (!ok) return
    setError(null)
    setMsg(null)
    setDeletingId(card.id)
    try {
      await api.del<void>(`/api/cards/${card.id}`)
      setMsg(`Карта «${card.displayName}» удалена`)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось удалить карту')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>Карты</h1>
        <div className="group">
          <button className="btn-secondary" onClick={startCreate}>
            + Добавить карту
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}
      {msg && <div className="success">{msg}</div>}

      {showForm && (
        <section className="card">
          <div className="card-head">
            <h2>{editId === null ? 'Новая карта' : 'Изменить карту'}</h2>
          </div>
          <form onSubmit={save}>
            <div className="form-grid">
              <label className="field">
                <span>Номер карты</span>
                <input
                  type="text"
                  value={cardNumber}
                  onChange={(e) => setCardNumber(e.target.value)}
                  placeholder="напр. 2202XXXXXXXX4242"
                />
              </label>
              <label className="field">
                <span>Название</span>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="напр. Зарплатная"
                  required
                />
              </label>
            </div>
            <p className="muted">
              Достаточно последних четырёх цифр — по ним карта сопоставляется с выписками и скриншотами.
            </p>
            <div className="actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Сохранение…' : editId === null ? 'Добавить' : 'Сохранить'}
              </button>
              <button type="button" className="btn-secondary" onClick={closeForm}>
                Отмена
              </button>
            </div>
          </form>
        </section>
      )}

      <section className="card">
        <div className="card-head">
          <h2>Мои карты</h2>
          <span className="muted">{cards.length}</span>
        </div>

        {loading ? (
          <p>Загрузка…</p>
        ) : cards.length === 0 ? (
          <p className="muted">Карт пока нет. Добавьте карту вручную или загрузите выписку — карта создастся автоматически.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Номер</th>
                <th>Название</th>
                <th>Валюта</th>
                <th>Добавлена</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {cards.map((card) => (
                <tr key={card.id}>
                  <td>{numberLabel(card)}</td>
                  <td>{card.displayName}</td>
                  <td>{card.currency}</td>
                  <td className="muted">{fmtInstant(card.createdAt)}</td>
                  <td>
                    <div className="row">
                      <button className="btn-ghost btn-sm" onClick={() => startEdit(card)}>
                        Изменить
                      </button>
                      <button
                        className="btn-danger btn-sm"
                        onClick={() => remove(card)}
                        disabled={deletingId === card.id}
                      >
                        {deletingId === card.id ? 'Удаление…' : 'Удалить'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

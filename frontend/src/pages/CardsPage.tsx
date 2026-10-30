import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'

interface Card {
  id: number
  displayName: string
  bank: string | null
  last4: string | null
  currency: string
  archived: boolean
  createdAt: string
}

interface CardForm {
  displayName: string
  bank: string
  last4: string
  currency: string
}

const emptyForm: CardForm = { displayName: '', bank: '', last4: '', currency: 'RUB' }

export function CardsPage() {
  const { user, logout } = useAuth()
  const [cards, setCards] = useState<Card[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<CardForm>(emptyForm)
  const [formError, setFormError] = useState<string | null>(null)

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
    setEditingId(null)
    setForm(emptyForm)
    setFormError(null)
  }

  function startEdit(card: Card) {
    setEditingId(card.id)
    setForm({
      displayName: card.displayName,
      bank: card.bank ?? '',
      last4: card.last4 ?? '',
      currency: card.currency,
    })
    setFormError(null)
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    try {
      const payload = {
        displayName: form.displayName,
        bank: form.bank || null,
        last4: form.last4 || null,
        currency: form.currency,
      }
      if (editingId === null) {
        await api.post<Card>('/api/cards', payload)
      } else {
        await api.put<Card>(`/api/cards/${editingId}`, payload)
      }
      startCreate()
      await load()
    } catch (err) {
      if (err instanceof HttpError && err.fieldErrors.length > 0) {
        setFormError(err.fieldErrors.map((f) => f.message).join('; '))
      } else {
        setFormError(err instanceof HttpError ? err.message : 'Ошибка сохранения')
      }
    }
  }

  async function archiveCard(card: Card) {
    if (!window.confirm(`Архивировать карту «${card.displayName}»?`)) return
    try {
      await api.del<Card>(`/api/cards/${card.id}`)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось архивировать')
    }
  }

  return (
    <div className="page">
      <header className="topbar">
        <h1>Карты</h1>
        <div className="topbar-right">
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>{editingId === null ? 'Новая карта' : 'Редактировать карту'}</h2>
        <form className="form-grid" onSubmit={onSubmit}>
          <label className="field">
            <span>Название</span>
            <input
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              required
            />
          </label>
          <label className="field">
            <span>Банк</span>
            <input value={form.bank} onChange={(e) => setForm({ ...form, bank: e.target.value })} />
          </label>
          <label className="field">
            <span>Последние 4 цифры</span>
            <input
              value={form.last4}
              onChange={(e) => setForm({ ...form, last4: e.target.value })}
              maxLength={4}
            />
          </label>
          <label className="field">
            <span>Валюта</span>
            <input
              value={form.currency}
              onChange={(e) => setForm({ ...form, currency: e.target.value })}
              maxLength={3}
            />
          </label>
          {formError && <div className="error">{formError}</div>}
          <div className="row">
            <button type="submit">{editingId === null ? 'Добавить' : 'Сохранить'}</button>
            {editingId !== null && (
              <button type="button" onClick={startCreate}>
                Отмена
              </button>
            )}
          </div>
        </form>
      </section>

      <section>
        {loading ? (
          <p>Загрузка…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Название</th>
                <th>Банк</th>
                <th>…4</th>
                <th>Валюта</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {cards.map((card) => (
                <tr key={card.id}>
                  <td>{card.displayName}</td>
                  <td>{card.bank ?? '—'}</td>
                  <td>{card.last4 ? `•••• ${card.last4}` : '—'}</td>
                  <td>{card.currency}</td>
                  <td>{card.archived ? 'архивирована' : 'активна'}</td>
                  <td className="row">
                    <Link className="link-btn" to={`/cards/${card.id}/statements`}>
                      Выписки
                    </Link>
                    <button onClick={() => startEdit(card)}>Изменить</button>
                    {!card.archived && (
                      <button onClick={() => archiveCard(card)}>Архивировать</button>
                    )}
                  </td>
                </tr>
              ))}
              {cards.length === 0 && (
                <tr>
                  <td colSpan={6}>Карт пока нет</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

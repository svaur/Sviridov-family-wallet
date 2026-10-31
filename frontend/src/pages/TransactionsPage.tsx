import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'
import { RuleQuickForm, type RulePrefill } from '../components/RuleQuickForm'
import { fmtDate, fmtMoney } from '../format'

interface Transaction {
  id: number
  date: string
  operation: string | null
  purpose: string | null
  amount: number | string
  type: string
  categoryId: number | null
  categoryName: string | null
  source: string
  cardId: number | null
}

interface Category {
  id: number
  name: string
}

interface Card {
  id: number
  displayName: string
  last4: string | null
}

interface TxForm {
  source: string
  date: string
  type: 'debit' | 'credit'
  amount: string
  purpose: string
  categoryId: string
}

const emptyTx: TxForm = {
  source: '',
  date: '',
  type: 'debit',
  amount: '',
  purpose: '',
  categoryId: '',
}

export function TransactionsPage() {
  const { user, logout } = useAuth()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [txForm, setTxForm] = useState<TxForm>(emptyTx)
  const [txFormError, setTxFormError] = useState<string | null>(null)

  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [assignCat, setAssignCat] = useState('')
  const [rulePrefill, setRulePrefill] = useState<RulePrefill | null>(null)

  async function load() {
    setLoading(true)
    try {
      const [txs, cats, crds] = await Promise.all([
        api.get<Transaction[]>('/api/transactions'),
        api.get<Category[]>('/api/categories'),
        api.get<Card[]>('/api/cards'),
      ])
      setTransactions(txs)
      setCategories(cats)
      setCards(crds)
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить данные')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  function errMessage(err: unknown) {
    if (err instanceof HttpError && err.fieldErrors.length > 0) {
      return err.fieldErrors.map((f) => f.message).join('; ')
    }
    return err instanceof HttpError ? err.message : 'Ошибка сохранения'
  }

  async function saveTx(e: FormEvent) {
    e.preventDefault()
    setTxFormError(null)
    try {
      const srcVal = txForm.source
      let source: string
      let cardId: number | null
      if (srcVal.startsWith('card:')) {
        source = 'card'
        cardId = Number(srcVal.slice(5))
      } else {
        source = srcVal
        cardId = null
      }
      const payload = {
        source,
        cardId,
        date: txForm.date,
        type: txForm.type,
        amount: txForm.amount,
        purpose: txForm.purpose.trim() === '' ? null : txForm.purpose.trim(),
        categoryId: txForm.categoryId === '' ? null : Number(txForm.categoryId),
      }
      await api.post<Transaction>('/api/transactions', payload)
      setTxForm(emptyTx)
      await load()
    } catch (err) {
      setTxFormError(errMessage(err))
    }
  }

  function sourceLabel(t: Transaction): string {
    if (t.source === 'statement') return 'выписка'
    if (t.source === 'cash') return 'наличные'
    if (t.source === 'other') return 'другое'
    const c = cards.find((x) => x.id === t.cardId)
    return c && c.last4 ? `карта •••• ${c.last4}` : 'карта'
  }

  function badgeClass(source: string): string {
    if (source === 'statement') return 'badge-statement'
    if (source === 'card') return 'badge-card'
    if (source === 'cash') return 'badge-cash'
    return 'badge-other'
  }

  function toggleRow(id: number) {
    if (expandedId === id) {
      setExpandedId(null)
    } else {
      setExpandedId(id)
    }
    setAssignCat('')
    setRulePrefill(null)
  }

  async function deleteTx(t: Transaction) {
    if (!window.confirm('Удалить операцию?')) return
    try {
      await api.del(`/api/transactions/${t.id}`)
      setExpandedId(null)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось удалить операцию')
    }
  }

  function createRuleFrom(t: Transaction) {
    setRulePrefill({
      purpose: t.purpose ?? null,
      operation: t.operation ?? null,
      categoryId: t.categoryId,
    })
  }

  async function assignCategory(id: number) {
    if (assignCat === '') return
    try {
      await api.post<Transaction>(`/api/transactions/${id}/category`, { categoryId: Number(assignCat) })
      setExpandedId(null)
      setAssignCat('')
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось назначить категорию')
    }
  }

  return (
    <div className="page">
      <header className="topbar">
        <h1>Операции</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/">Дашборд</Link>
          <Link className="link-btn" to="/cards">Карты</Link>
          <Link className="link-btn" to="/rules">Правила</Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>Добавить операцию вручную</h2>
        <form className="form-grid" onSubmit={saveTx}>
          <label className="field">
            <span>Источник</span>
            <select value={txForm.source} onChange={(e) => setTxForm({ ...txForm, source: e.target.value })} required>
              <option value="">— выберите —</option>
              <optgroup label="Карты">
                {cards.map((c) => (
                  <option key={c.id} value={`card:${c.id}`}>
                    {c.last4 ? `${c.displayName} •••• ${c.last4}` : c.displayName}
                  </option>
                ))}
              </optgroup>
              <option value="cash">Наличные</option>
              <option value="other">Другое</option>
            </select>
          </label>
          <label className="field">
            <span>Дата</span>
            <input type="date" value={txForm.date} onChange={(e) => setTxForm({ ...txForm, date: e.target.value })} required />
          </label>
          <label className="field">
            <span>Тип</span>
            <select value={txForm.type} onChange={(e) => setTxForm({ ...txForm, type: e.target.value as 'debit' | 'credit' })}>
              <option value="debit">Расход</option>
              <option value="credit">Приход</option>
            </select>
          </label>
          <label className="field">
            <span>Сумма</span>
            <input type="number" step="0.01" min={0.01} value={txForm.amount} onChange={(e) => setTxForm({ ...txForm, amount: e.target.value })} required />
          </label>
          <label className="field">
            <span>Назначение платежа</span>
            <input value={txForm.purpose} onChange={(e) => setTxForm({ ...txForm, purpose: e.target.value })} placeholder="например: Покупка в Магните" />
          </label>
          <label className="field">
            <span>Категория</span>
            <select value={txForm.categoryId} onChange={(e) => setTxForm({ ...txForm, categoryId: e.target.value })}>
              <option value="">— авто (по правилам) —</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </label>
          {txFormError && <div className="error">{txFormError}</div>}
          <div className="row">
            <button type="submit">Добавить</button>
          </div>
        </form>
      </section>

      <section>
        {loading ? (
          <p>Загрузка…</p>
        ) : (
          <>
            <p className="muted">Нажмите на операцию, чтобы удалить её, назначить категорию или создать правило.</p>
            <table className="table">
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Операция</th>
                  <th>Назначение платежа</th>
                  <th>Категория</th>
                  <th>Источник</th>
                  <th style={{ textAlign: 'right' }}>Сумма</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((t) => (
                  <Fragment key={t.id}>
                    <tr className="tx-row" onClick={() => toggleRow(t.id)}>
                      <td>{fmtDate(t.date)}</td>
                      <td>{t.operation ?? '—'}</td>
                      <td>{t.purpose ?? '—'}</td>
                      <td>{t.categoryName ?? '—'}</td>
                      <td>
                        <span className={`badge ${badgeClass(t.source)}`}>{sourceLabel(t)}</span>
                      </td>
                      <td className={t.type === 'debit' ? 'amount-debit' : 'amount-credit'}>
                        {t.type === 'debit' ? '− ' : '+ '}
                        {fmtMoney(t.amount)}
                      </td>
                    </tr>
                    {expandedId === t.id && (
                      <tr>
                        <td colSpan={6}>
                          <div className="tx-actions">
                            <select
                              value={assignCat}
                              onChange={(e) => setAssignCat(e.target.value)}
                              aria-label="Категория вручную"
                            >
                              <option value="">— категория —</option>
                              {categories.map((c) => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                              ))}
                            </select>
                            <button onClick={() => assignCategory(t.id)} disabled={assignCat === ''}>
                              Назначить категорию
                            </button>
                            <button onClick={() => (rulePrefill ? setRulePrefill(null) : createRuleFrom(t))}>
                              {rulePrefill ? 'Скрыть форму' : 'Создать правило'}
                            </button>
                            <button onClick={() => deleteTx(t)}>Удалить</button>
                          </div>
                          {rulePrefill && (
                            <RuleQuickForm
                              categories={categories}
                              prefill={rulePrefill}
                              onClose={() => setRulePrefill(null)}
                              onCreated={() => {
                                setRulePrefill(null)
                                setExpandedId(null)
                                load()
                              }}
                            />
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
                {transactions.length === 0 && (
                  <tr>
                    <td colSpan={6}>Операций пока нет</td>
                  </tr>
                )}
              </tbody>
            </table>
          </>
        )}
      </section>
    </div>
  )
}

import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'
import { RuleQuickForm, type RulePrefill } from '../components/RuleQuickForm'
import { fmtDate, fmtMoney } from '../format'

interface Category {
  id: number
  name: string
}

interface PreviewTx {
  rowNumber: number
  date: string
  operation: string | null
  purpose: string | null
  amount: number
  type: string
  categoryId: number | null
  categoryName: string | null
}

interface Preview {
  draftId: string
  periodFrom: string | null
  periodTo: string | null
  cardNumber: string | null
  contract: string | null
  account: string | null
  rowCountRaw: number
  validCount: number
  skippedDuplicates: number
  warnings: string[]
  unrecognizedRows: string[]
  transactions: PreviewTx[]
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

export function ImportPage() {
  const { user, logout } = useAuth()

  const [step, setStep] = useState(1)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [categories, setCategories] = useState<Category[]>([])
  const [expandedRow, setExpandedRow] = useState<number | null>(null)
  const [assignCat, setAssignCat] = useState('')
  const [rulePrefill, setRulePrefill] = useState<RulePrefill | null>(null)

  useEffect(() => {
    api
      .get<Category[]>('/api/categories')
      .then(setCategories)
      .catch(() => {})
  }, [])

  async function onChooseFile(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!file) {
      setError('Выберите файл')
      return
    }
    setLoading(true)
    try {
      setPreview(await api.upload<Preview>('/api/statements/preview', file))
      setStep(2)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось разобрать выписку')
    } finally {
      setLoading(false)
    }
  }

  async function onRecategorize() {
    if (!preview) return
    setLoading(true)
    setError(null)
    try {
      setPreview(await api.post<Preview>(`/api/statements/preview/${preview.draftId}/recategorize`))
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось применить правила')
    } finally {
      setLoading(false)
    }
  }

  async function onConfirm() {
    if (!preview) return
    setLoading(true)
    setError(null)
    try {
      setResult(await api.post<ImportResult>(`/api/statements/preview/${preview.draftId}/confirm`))
      setStep(4)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось импортировать выписку')
    } finally {
      setLoading(false)
    }
  }

  function toggleRow(rowNumber: number) {
    if (expandedRow === rowNumber) {
      setExpandedRow(null)
    } else {
      setExpandedRow(rowNumber)
    }
    setAssignCat('')
    setRulePrefill(null)
  }

  async function assignCategory(tx: PreviewTx) {
    if (!preview || assignCat === '') return
    setError(null)
    try {
      setPreview(
        await api.post<Preview>(
          `/api/statements/preview/${preview.draftId}/transactions/${tx.rowNumber}/category`,
          { categoryId: Number(assignCat) },
        ),
      )
      setAssignCat('')
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось назначить категорию')
    }
  }

  async function deleteRow(tx: PreviewTx) {
    if (!preview) return
    if (!window.confirm('Удалить операцию из выгрузки?')) return
    setError(null)
    try {
      setPreview(
        await api.del<Preview>(`/api/statements/preview/${preview.draftId}/transactions/${tx.rowNumber}`),
      )
      setExpandedRow(null)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось удалить операцию')
    }
  }

  function createRuleFrom(tx: PreviewTx) {
    setRulePrefill({
      purpose: tx.purpose ?? null,
      operation: tx.operation ?? null,
      categoryId: tx.categoryId,
    })
  }

  return (
    <div className="page">
      <header className="topbar">
        <h1>Загрузка выписки</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/statements">
            ← Выписки
          </Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      <div className="stepper">
        <span className={step >= 1 ? 'step active' : 'step'}>1. Выбрать файл</span>
        <span className={step >= 2 ? 'step active' : 'step'}>2. Проверка правил</span>
        <span className={step >= 3 ? 'step active' : 'step'}>3. Подтверждение</span>
      </div>

      {error && <div className="error">{error}</div>}

      {step === 1 && (
        <section className="card">
          <h2>Шаг 1. Выберите файл</h2>
          <form onSubmit={onChooseFile}>
            <div className="row">
              <input type="file" accept="application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
              <button type="submit" disabled={loading}>
                {loading ? 'Разбор…' : 'Загрузить'}
              </button>
            </div>
          </form>
        </section>
      )}

      {step === 2 && preview && (
        <>
          <section className="card">
            <h2>Шаг 2. Проверка применения правил</h2>
            <div className="row">
              <span className="muted">
                Период: {fmtDate(preview.periodFrom)} — {fmtDate(preview.periodTo)}
              </span>
              <span className="muted">
                Распознано: {preview.validCount} / {preview.rowCountRaw}
              </span>
              <span className="muted">Дубликатов: {preview.skippedDuplicates}</span>
            </div>
            {preview.warnings.length > 0 && (
              <ul>
                {preview.warnings.map((w, i) => (
                  <li key={i} className="warning">
                    {w}
                  </li>
                ))}
              </ul>
            )}
            <div className="row">
              <button onClick={onRecategorize} disabled={loading}>
                Применить правила
              </button>
              <Link className="link-btn" to="/rules" target="_blank" rel="noreferrer">
                Создать / изменить правила
              </Link>
            </div>
          </section>

          <section>
            <p className="muted">
              Нажмите на операцию, чтобы назначить категорию вручную, создать правило по примеру или удалить её из
              выгрузки.
            </p>
            <table className="table">
              <thead>
                <tr>
                  <th>№</th>
                  <th>Дата</th>
                  <th>Операция</th>
                  <th>Назначение платежа</th>
                  <th>Категория</th>
                  <th>Сумма</th>
                </tr>
              </thead>
              <tbody>
                {preview.transactions.map((tx) => (
                  <Fragment key={tx.rowNumber}>
                    <tr className="tx-row" onClick={() => toggleRow(tx.rowNumber)}>
                      <td>{tx.rowNumber}</td>
                      <td>{fmtDate(tx.date)}</td>
                      <td>{tx.operation ?? '—'}</td>
                      <td>{tx.purpose ?? '—'}</td>
                      <td className={tx.categoryName ? undefined : 'uncategorized'}>
                        {tx.categoryName ?? 'Без категории'}
                      </td>
                      <td className={tx.type === 'debit' ? 'amount-debit' : 'amount-credit'}>
                        {tx.type === 'debit' ? '− ' : '+ '}
                        {fmtMoney(tx.amount)}
                      </td>
                    </tr>
                    {expandedRow === tx.rowNumber && (
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
                            <button onClick={() => assignCategory(tx)} disabled={assignCat === ''}>
                              Назначить категорию
                            </button>
                            <button onClick={() => (rulePrefill ? setRulePrefill(null) : createRuleFrom(tx))}>
                              {rulePrefill ? 'Скрыть форму' : 'Создать правило'}
                            </button>
                            <button onClick={() => deleteRow(tx)}>Удалить</button>
                          </div>
                          {rulePrefill && (
                            <RuleQuickForm
                              categories={categories}
                              prefill={rulePrefill}
                              onClose={() => setRulePrefill(null)}
                              onCreated={() => {
                                setRulePrefill(null)
                                setExpandedRow(null)
                                onRecategorize()
                              }}
                            />
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
                {preview.transactions.length === 0 && (
                  <tr>
                    <td colSpan={6}>Распознанных операций нет</td>
                  </tr>
                )}
              </tbody>
            </table>
          </section>

          <div className="row">
            <button onClick={() => setStep(1)}>Назад</button>
            <button onClick={() => setStep(3)}>Далее</button>
          </div>
        </>
      )}

      {step === 3 && preview && (
        <section className="card">
          <h2>Шаг 3. Подтверждение изменений</h2>
          <p>
            Будет импортировано <strong>{preview.validCount - preview.skippedDuplicates}</strong> операций
            (пропущено дубликатов: {preview.skippedDuplicates}).
          </p>
          {preview.unrecognizedRows.length > 0 && (
            <ul>
              {preview.unrecognizedRows.map((r, i) => (
                <li key={i} className="error">
                  {r}
                </li>
              ))}
            </ul>
          )}
          <div className="row">
            <button onClick={() => setStep(2)}>Назад</button>
            <button onClick={onConfirm} disabled={loading}>
              {loading ? 'Импорт…' : 'Импортировать'}
            </button>
          </div>
        </section>
      )}

      {step === 4 && result && (
        <section className="card">
          <h2>Готово</h2>
          <div className="import-result">
            <p>
              Статус: <strong>{result.status}</strong> · импортировано {result.importedCount} · пропущено
              дубликатов {result.skippedDuplicates}
            </p>
          </div>
          <Link className="link-btn" to="/statements">
            К списку выписок
          </Link>
        </section>
      )}
    </div>
  )
}

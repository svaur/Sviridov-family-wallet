import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { CategoryPicker } from '../components/CategoryPicker'
import { RuleQuickForm, type RulePrefill } from '../components/RuleQuickForm'
import { fmtDate, fmtMoney } from '../format'

interface Category {
  id: number
  name: string
  icon: string
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

function last4(cardNumber: string | null): string | null {
  if (!cardNumber) return null
  const digits = cardNumber.replace(/\D/g, '')
  return digits.length >= 4 ? digits.slice(-4) : digits
}

function accountShort(account: string | null): string | null {
  if (!account) return null
  if (account.length <= 12) return account
  return `${account.slice(0, 8)}…${account.slice(-4)}`
}

export function ImportPage() {
  const [step, setStep] = useState(1)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [categories, setCategories] = useState<Category[]>([])
  const [expandedRow, setExpandedRow] = useState<number | null>(null)
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
    setRulePrefill(null)
  }

  async function assignCategory(tx: PreviewTx, categoryId: number) {
    if (!preview) return
    setError(null)
    try {
      setPreview(
        await api.post<Preview>(
          `/api/statements/preview/${preview.draftId}/transactions/${tx.rowNumber}/category`,
          { categoryId },
        ),
      )
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
      amount: tx.amount,
      date: tx.date,
    })
  }

  const iconById = new Map(categories.map((c) => [c.id, c.icon]))
  const uncategorizedCount = preview ? preview.transactions.filter((t) => t.categoryId == null).length : 0

  return (
    <div className="page">
      <h1>Загрузка выписки</h1>

      <div className="stepper">
        <span className={`step ${step > 1 ? 'done' : step === 1 ? 'active' : ''}`}>
          <span className="n">{step > 1 ? '✓' : '1'}</span>Файл
        </span>
        <span className="line" />
        <span className={`step ${step > 2 ? 'done' : step === 2 ? 'active' : ''}`}>
          <span className="n">{step > 2 ? '✓' : '2'}</span>Проверка правил
        </span>
        <span className="line" />
        <span className={`step ${step === 4 ? 'done' : step === 3 ? 'active' : ''}`}>
          <span className="n">{step === 4 ? '✓' : '3'}</span>Подтверждение
        </span>
      </div>

      {error && <div className="error">{error}</div>}

      {step === 1 && (
        <section className="card">
          <h2>Выберите файл</h2>
          <p className="muted">
            PDF-выписка банка, до 10 МБ. Карта создаётся автоматически по номеру из выписки, сам файл не хранится.
          </p>
          <form onSubmit={onChooseFile}>
            <div className="row">
              <input type="file" accept="application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
              <button type="submit" disabled={loading || !file}>
                {loading ? 'Разбор…' : 'Загрузить'}
              </button>
            </div>
          </form>
        </section>
      )}

      {step === 2 && preview && (
        <>
          <section className="card">
            <div className="meta">
              <div className="m">
                <span>Карта</span>
                <b>{last4(preview.cardNumber) ? `•••• ${last4(preview.cardNumber)}` : '—'}</b>
              </div>
              <div className="m">
                <span>Период</span>
                <b>
                  {fmtDate(preview.periodFrom)} — {fmtDate(preview.periodTo)}
                </b>
              </div>
              <div className="m">
                <span>Счёт</span>
                <b>{accountShort(preview.account) ?? '—'}</b>
              </div>
              <div className="m">
                <span>Распознано</span>
                <b>{preview.validCount} строк</b>
              </div>
            </div>

            {uncategorizedCount > 0 && (
              <div className="warning">
                ⚠ {uncategorizedCount} операций без категории — можно назначить вручную или создать правило.
              </div>
            )}
            {preview.warnings.length > 0 && (
              <ul>
                {preview.warnings.map((w, i) => (
                  <li key={i} className="warning">
                    {w}
                  </li>
                ))}
              </ul>
            )}

            <h2>Предпросмотр операций</h2>
            <p className="muted">
              Нажмите на операцию, чтобы назначить категорию, создать правило по примеру или удалить её из выгрузки.
            </p>

            <table className="table">
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Операция</th>
                  <th>Назначение платежа</th>
                  <th>Категория</th>
                  <th className="amount">Сумма</th>
                </tr>
              </thead>
              <tbody>
                {preview.transactions.map((tx) => (
                  <Fragment key={tx.rowNumber}>
                    <tr
                      className={expandedRow === tx.rowNumber ? 'tx-row open' : 'tx-row'}
                      onClick={() => toggleRow(tx.rowNumber)}
                    >
                      <td>{fmtDate(tx.date)}</td>
                      <td>{tx.operation ?? '—'}</td>
                      <td className="muted" title={tx.purpose ?? undefined}>{tx.purpose ?? '—'}</td>
                      <td>
                        {tx.categoryName ? (
                          <span className="pill">
                            {iconById.get(tx.categoryId ?? -1) ?? ''} {tx.categoryName}
                          </span>
                        ) : (
                          <span className="uncategorized">Без категории</span>
                        )}
                      </td>
                      <td className={`amount ${tx.type === 'debit' ? 'amount-debit' : 'amount-credit'}`}>
                        {tx.type === 'debit' ? '− ' : '+ '}
                        {fmtMoney(tx.amount)}
                      </td>
                    </tr>
                    {expandedRow === tx.rowNumber && (
                      <tr>
                        <td colSpan={5}>
                          <div className="tx-actions">
                            <CategoryPicker
                              categories={categories}
                              onAssign={(cid) => assignCategory(tx, cid)}
                              onCategoriesChanged={setCategories}
                            />
                            <button
                              className="btn-secondary btn-sm"
                              onClick={() => (rulePrefill ? setRulePrefill(null) : createRuleFrom(tx))}
                            >
                              {rulePrefill ? 'Скрыть форму' : 'Создать правило'}
                            </button>
                            <button className="btn-danger btn-sm" onClick={() => deleteRow(tx)}>
                              Удалить
                            </button>
                          </div>
                          {rulePrefill && (
                            <RuleQuickForm
                              categories={categories}
                              prefill={rulePrefill}
                              onClose={() => setRulePrefill(null)}
                              onCategoriesChanged={setCategories}
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
                    <td colSpan={5}>Распознанных операций нет</td>
                  </tr>
                )}
              </tbody>
            </table>

            <div className="wizard-footer">
              <div className="group">
                <button className="btn-secondary" onClick={onRecategorize} disabled={loading}>
                  Применить правила
                </button>
                <Link className="link-btn" to="/rules" target="_blank" rel="noreferrer">
                  Создать / изменить правила
                </Link>
              </div>
              <div className="group">
                <button className="btn-secondary" onClick={() => setStep(1)}>
                  Назад
                </button>
                <button onClick={() => setStep(3)}>Далее →</button>
              </div>
            </div>
          </section>
        </>
      )}

      {step === 3 && preview && (
        <section className="card">
          <h2>Подтверждение изменений</h2>
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
          <div className="wizard-footer">
            <button className="btn-secondary" onClick={() => setStep(2)}>
              Назад
            </button>
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
              Статус: <strong>{result.status}</strong> · импортировано {result.importedCount} · пропущено дубликатов{' '}
              {result.skippedDuplicates}
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

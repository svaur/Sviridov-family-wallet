import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, HttpError } from '../api'
import { CategoryPicker } from '../components/CategoryPicker'
import { RuleQuickForm, type RulePrefill } from '../components/RuleQuickForm'
import { fmtDate, fmtMoney } from '../format'

interface Category {
  id: number
  name: string
  icon: string
}

interface Card {
  id: number
  cardNumber: string | null
  displayName: string
  last4: string
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
  categoryManual: boolean
}

interface Preview {
  parserVersion: string
  periodFrom: string | null
  periodTo: string | null
  cardNumber: string | null
  contract: string | null
  account: string | null
  rowCountRaw: number
  validCount: number
  warnings: string[]
  unrecognizedRows: string[]
  transactions: PreviewTx[]
}

interface ImportResult {
  statementId: number | null
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

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

function accountShort(account: string | null): string | null {
  if (!account) return null
  if (account.length <= 12) return account
  return `${account.slice(0, 8)}…${account.slice(-4)}`
}

function todayISO(): string {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function ImportPage() {
  const [searchParams] = useSearchParams()
  const isScreenshot = searchParams.get('kind') === 'screenshot'
  const [step, setStep] = useState(1)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [stage, setStage] = useState('')
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const [categories, setCategories] = useState<Category[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [expandedRow, setExpandedRow] = useState<number | null>(null)
  const [rulePrefill, setRulePrefill] = useState<RulePrefill | null>(null)
  const [cardSelect, setCardSelect] = useState('')
  const [cardInput, setCardInput] = useState('')
  const [editRow, setEditRow] = useState<number | null>(null)
  const [editOperation, setEditOperation] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [editDate, setEditDate] = useState('')
  const [addOpen, setAddOpen] = useState(false)
  const [addOperation, setAddOperation] = useState('')
  const [addAmount, setAddAmount] = useState('')
  const [addType, setAddType] = useState<'debit' | 'credit'>('debit')
  const [addDate, setAddDate] = useState(todayISO())

  useEffect(() => {
    api
      .get<Category[]>('/api/categories')
      .then(setCategories)
      .catch(() => {})
    api
      .get<Card[]>('/api/cards')
      .then(setCards)
      .catch(() => {})
  }, [])

  // Screenshot uploads sometimes lose the card number during OCR. Let the
  // user pick one of their existing cards instead of retyping it from memory.
  function onPickCard(value: string) {
    setCardSelect(value)
    if (value === '__new__') {
      setCardInput('')
      return
    }
    const card = cards.find((c) => String(c.id) === value)
    setCardInput(card ? (card.cardNumber ?? card.last4) : '')
  }

  useEffect(() => {
    if (!loading) {
      setElapsed(0)
      return
    }
    const startedAt = Date.now()
    const id = setInterval(() => setElapsed(Math.round((Date.now() - startedAt) / 1000)), 1000)
    return () => clearInterval(id)
  }, [loading])

  async function onChooseFile(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!file) {
      setError('Выберите файл')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError('Файл больше 10 МБ — уменьшите его или загрузите другой')
      return
    }
    setLoading(true)
    setStage(isScreenshot ? 'Разбивка скриншота и распознавание текста…' : 'Разбор выписки…')
    try {
      const kind = isScreenshot ? 'screenshot' : 'statement'
      setPreview(await api.upload<Preview>(`/api/statements/preview?kind=${kind}`, file))
      setStep(2)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось разобрать выписку')
    } finally {
      setLoading(false)
      setStage('')
    }
  }

  async function onRecategorize() {
    if (!preview) return
    setLoading(true)
    setStage('Применение правил…')
    setError(null)
    try {
      const rows = await api.post<PreviewTx[]>('/api/statements/categorize', preview.transactions)
      setPreview({ ...preview, transactions: rows })
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось применить правила')
    } finally {
      setLoading(false)
      setStage('')
    }
  }

  async function onConfirm() {
    if (!preview) return
    const resolvedCard = last4(preview.cardNumber) ? preview.cardNumber : cardInput.trim()
    if (!last4(resolvedCard)) {
      setError('Укажите номер карты')
      return
    }
    setLoading(true)
    setStage('Импорт…')
    setError(null)
    try {
      setResult(
        await api.post<ImportResult>('/api/statements/confirm', {
          cardNumber: resolvedCard,
          parserVersion: preview.parserVersion,
          periodFrom: preview.periodFrom,
          periodTo: preview.periodTo,
          contract: preview.contract,
          account: preview.account,
          rowCountRaw: preview.rowCountRaw,
          warnings: preview.warnings,
          unrecognizedRows: preview.unrecognizedRows,
          transactions: preview.transactions,
        }),
      )
      setStep(4)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось импортировать выписку')
    } finally {
      setLoading(false)
      setStage('')
    }
  }

  function toggleRow(rowNumber: number) {
    if (expandedRow === rowNumber) {
      setExpandedRow(null)
    } else {
      setExpandedRow(rowNumber)
    }
    setRulePrefill(null)
    setEditRow(null)
  }

  function startEdit(tx: PreviewTx) {
    setEditRow(tx.rowNumber)
    setEditOperation(tx.operation ?? '')
    setEditAmount(String(tx.amount))
    setEditDate(tx.date)
    setRulePrefill(null)
  }

  function saveEdit(tx: PreviewTx) {
    if (!preview) return
    setPreview({
      ...preview,
      transactions: preview.transactions.map((t) =>
        t.rowNumber === tx.rowNumber
          ? { ...t, operation: editOperation, amount: Number(editAmount), date: editDate }
          : t,
      ),
    })
    setEditRow(null)
  }

  function addRow() {
    if (!preview) return
    const nextNumber = preview.transactions.reduce((m, t) => Math.max(m, t.rowNumber), 0) + 1
    const newTx: PreviewTx = {
      rowNumber: nextNumber,
      date: addDate,
      operation: addOperation,
      purpose: null,
      amount: Number(addAmount),
      type: addType,
      categoryId: null,
      categoryName: null,
      categoryManual: false,
    }
    setPreview({
      ...preview,
      validCount: preview.validCount + 1,
      transactions: [...preview.transactions, newTx],
    })
    setAddOpen(false)
    setAddOperation('')
    setAddAmount('')
    setAddType('debit')
  }

  function assignCategory(tx: PreviewTx, categoryId: number) {
    if (!preview) return
    const cat = categories.find((c) => c.id === categoryId)
    setPreview({
      ...preview,
      transactions: preview.transactions.map((t) =>
        t.rowNumber === tx.rowNumber
          ? { ...t, categoryId, categoryName: cat?.name ?? null, categoryManual: true }
          : t,
      ),
    })
  }

  function deleteRow(tx: PreviewTx) {
    if (!preview) return
    if (!window.confirm('Удалить операцию из выгрузки?')) return
    setPreview({
      ...preview,
      validCount: preview.validCount - 1,
      transactions: preview.transactions.filter((t) => t.rowNumber !== tx.rowNumber),
    })
    setExpandedRow(null)
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
      <h1>{isScreenshot ? 'Загрузка скрина ленты операций' : 'Загрузка выписки'}</h1>

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

      {loading && (
        <div className="progress-line" role="status">
          <span className="spinner" aria-hidden="true" />
          {stage}
          {elapsed > 0 && <span className="muted"> · {elapsed} с</span>}
        </div>
      )}

      {isScreenshot && (
        <div className="warning">
          ⚠ При импорте скриншота данные загружаются не полностью, предпочтительнее загрузить выписку с полными данными.
        </div>
      )}

      {error && <div className="error">{error}</div>}

      {step === 1 && (
        <section className="card">
          <h2>Выберите файл</h2>
          <p className="muted">
            {isScreenshot
              ? 'Скриншот ленты операций мобильного банка (PNG или JPG), до 10 МБ. Распознаётся текст и суммы; поле «назначение платежа» останется пустым.'
              : 'PDF-выписка банка, до 10 МБ. Карта создаётся автоматически по номеру из выписки, сам файл не хранится.'}
          </p>
          <form onSubmit={onChooseFile}>
            <div className="row">
              <input
                type="file"
                accept={isScreenshot ? 'image/*' : 'application/pdf'}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
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
                {last4(preview.cardNumber) ? (
                  <b>•••• {last4(preview.cardNumber)}</b>
                ) : (
                  <div className="card-pick">
                    <select value={cardSelect} onChange={(e) => onPickCard(e.target.value)}>
                      <option value="">— выберите карту —</option>
                      {cards.map((c) => (
                        <option key={c.id} value={String(c.id)}>
                          •••• {c.last4} · {c.displayName}
                        </option>
                      ))}
                      <option value="__new__">＋ Новая карта</option>
                    </select>
                    {cardSelect === '__new__' && (
                      <input
                        type="text"
                        value={cardInput}
                        onChange={(e) => setCardInput(e.target.value)}
                        placeholder="номер карты, напр. 2202XXXXXXXX4242"
                      />
                    )}
                  </div>
                )}
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

            {preview.unrecognizedRows.length > 0 && (
              <>
                <p className="warning">Не распознано строк: {preview.unrecognizedRows.length}</p>
                <ul>
                  {preview.unrecognizedRows.map((r, i) => (
                    <li key={i} className="error">{r}</li>
                  ))}
                </ul>
              </>
            )}
            {!last4(preview.cardNumber) && !last4(cardInput) && (
              <div className="warning">
                ⚠ Не удалось определить номер карты — выберите существующую карту или создайте новую, чтобы продолжить.
              </div>
            )}
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

            <div className="tx-actions">
              <button className="btn-secondary" onClick={onRecategorize} disabled={loading}>
                Применить правила
              </button>
              {isScreenshot && (
                <button className="btn-secondary" onClick={() => setAddOpen(!addOpen)}>
                  {addOpen ? 'Скрыть форму' : 'Добавить строку'}
                </button>
              )}
            </div>

            {isScreenshot && addOpen && (
              <div className="rule-quick-form">
                <div className="form-grid">
                  <label className="field">
                    <span>Наименование</span>
                    <input
                      type="text"
                      value={addOperation}
                      onChange={(e) => setAddOperation(e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Сумма</span>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      value={addAmount}
                      onChange={(e) => setAddAmount(e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Тип</span>
                    <select value={addType} onChange={(e) => setAddType(e.target.value as 'debit' | 'credit')}>
                      <option value="debit">Расход</option>
                      <option value="credit">Приход</option>
                    </select>
                  </label>
                  <label className="field">
                    <span>Дата</span>
                    <input type="date" value={addDate} onChange={(e) => setAddDate(e.target.value)} />
                  </label>
                </div>
                <div className="actions">
                  <button className="btn-sm" onClick={addRow}>
                    Добавить
                  </button>
                  <button type="button" className="btn-secondary btn-sm" onClick={() => setAddOpen(false)}>
                    Отмена
                  </button>
                </div>
              </div>
            )}

            <table className="table">
              <thead>
                <tr>
                  <th className="row-num">№</th>
                  <th>Дата</th>
                  <th>Операция</th>
                  <th>Назначение платежа</th>
                  <th>Категория</th>
                  <th className="amount">Сумма</th>
                </tr>
              </thead>
              <tbody>
                {preview.transactions.map((tx, idx) => (
                  <Fragment key={tx.rowNumber}>
                    <tr
                      className={expandedRow === tx.rowNumber ? 'tx-row open' : 'tx-row'}
                      onClick={() => toggleRow(tx.rowNumber)}
                    >
                      <td className="row-num">{idx + 1}</td>
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
                      <td
                        className={`amount ${Number(tx.amount) === 0 ? 'amount-suspect' : tx.type === 'debit' ? 'amount-debit' : 'amount-credit'}`}
                        title={Number(tx.amount) === 0 ? 'Сумма не распознана — проверьте' : undefined}
                      >
                        {tx.type === 'debit' ? '− ' : '+ '}
                        {fmtMoney(tx.amount)}
                      </td>
                    </tr>
                    {expandedRow === tx.rowNumber && (
                      <tr>
                        <td colSpan={6}>
                          <div className="tx-actions">
                            <CategoryPicker
                              categories={categories}
                              onAssign={(cid) => assignCategory(tx, cid)}
                              onCategoriesChanged={setCategories}
                            />
                            <button
                              className="btn-secondary btn-sm"
                              onClick={() => (editRow === tx.rowNumber ? setEditRow(null) : startEdit(tx))}
                            >
                              {editRow === tx.rowNumber ? 'Скрыть правку' : 'Изменить'}
                            </button>
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
                          {editRow === tx.rowNumber && (
                            <div className="rule-quick-form">
                              <div className="form-grid">
                                <label className="field">
                                  <span>Наименование</span>
                                  <input
                                    type="text"
                                    value={editOperation}
                                    onChange={(e) => setEditOperation(e.target.value)}
                                  />
                                </label>
                                <label className="field">
                                  <span>Сумма</span>
                                  <input
                                    type="number"
                                    step="0.01"
                                    min="0.01"
                                    value={editAmount}
                                    onChange={(e) => setEditAmount(e.target.value)}
                                  />
                                </label>
                                <label className="field">
                                  <span>Дата</span>
                                  <input
                                    type="date"
                                    value={editDate}
                                    onChange={(e) => setEditDate(e.target.value)}
                                  />
                                </label>
                              </div>
                              <div className="actions">
                                <button className="btn-sm" onClick={() => saveEdit(tx)}>
                                  Сохранить
                                </button>
                                <button
                                  type="button"
                                  className="btn-secondary btn-sm"
                                  onClick={() => setEditRow(null)}
                                >
                                  Отмена
                                </button>
                              </div>
                            </div>
                          )}
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
                    <td colSpan={6}>Распознанных операций нет</td>
                  </tr>
                )}
              </tbody>
            </table>

            <div className="wizard-footer">
              <div className="group">
                <button className="btn-secondary" onClick={() => setStep(1)}>
                  Назад
                </button>
                <button
                  onClick={() => setStep(3)}
                  disabled={!last4(preview.cardNumber) && !last4(cardInput)}
                  title={!last4(preview.cardNumber) && !last4(cardInput) ? 'Сначала укажите карту' : undefined}
                >
                  Далее →
                </button>
              </div>
            </div>
          </section>
        </>
      )}

      {step === 3 && preview && (
        <section className="card">
          <h2>Подтверждение изменений</h2>
          <p>
            Будет импортировано <strong>{preview.transactions.length}</strong> операций.
          </p>
          <p>
            Карта:{' '}
            <strong>
              {last4(preview.cardNumber)
                ? `•••• ${last4(preview.cardNumber)}`
                : `•••• ${last4(cardInput) || '—'}`}
            </strong>
          </p>
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
            {result.unrecognizedRows.length > 0 && (
              <>
                <p className="warning">Не распознано строк: {result.unrecognizedRows.length}</p>
                <ul>
                  {result.unrecognizedRows.map((r, i) => (
                    <li key={i} className="error">
                      {r}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
          <Link className="link-btn" to="/statements">
            К списку выписок
          </Link>
        </section>
      )}
    </div>
  )
}

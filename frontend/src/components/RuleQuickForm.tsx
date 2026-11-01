import { useState, type FormEvent } from 'react'
import { api, HttpError } from '../api'
import { buildRuleParams, ruleMatchesTemplate } from '../ruleHelpers'
import { CategorySelect } from './CategorySelect'

interface Category {
  id: number
  name: string
  icon: string
}

export interface RulePrefill {
  purpose: string | null
  operation: string | null
  categoryId: number | null
  amount: number | null
  date: string | null
}

interface RuleQuickFormProps {
  categories: Category[]
  prefill: RulePrefill
  onClose: () => void
  onCreated: () => void
  onCategoriesChanged: (categories: Category[]) => void
}

/**
 * Compact inline "create rule from example" form shown under a transaction row.
 * Pre-fills only the counterparty (назначение платежа) and operation; the
 * amount and day-of-month are left empty on purpose — they are too specific to
 * guess from a single example. The user picks the category and confirms.
 * Priority defaults to 100, name is left empty.
 *
 * Before saving, it verifies the rule would match the template transaction. If
 * it would not, an inline warning is shown (instead of the browser's native
 * `confirm`, which can be suppressed) and the user can either force-save or
 * cancel.
 */
export function RuleQuickForm({ categories, prefill, onClose, onCreated, onCategoriesChanged }: RuleQuickFormProps) {
  const [categoryId, setCategoryId] = useState(prefill.categoryId != null ? String(prefill.categoryId) : '')
  const [purpose, setPurpose] = useState(prefill.purpose ?? '')
  const [operation, setOperation] = useState(prefill.operation ?? '')
  const [min, setMin] = useState('')
  const [max, setMax] = useState('')
  const [dayFrom, setDayFrom] = useState('')
  const [dayTo, setDayTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [warnMismatch, setWarnMismatch] = useState(false)

  function buildParams(): Record<string, string | number> {
    return buildRuleParams({ purpose, operation, min, max, dayFrom, dayTo })
  }

  async function save(params: Record<string, string | number>) {
    setSaving(true)
    setError(null)
    try {
      await api.post('/api/rules', {
        name: null,
        params,
        categoryId: Number(categoryId),
        priority: null,
      })
      onCreated()
    } catch (err) {
      if (err instanceof HttpError && err.fieldErrors.length > 0) {
        setError(err.fieldErrors.map((f) => f.message).join('; '))
      } else {
        setError(err instanceof HttpError ? err.message : 'Не удалось создать правило')
      }
    } finally {
      setSaving(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const params = buildParams()
    const hasConditions = Object.keys(params).length > 0
    if (
      hasConditions &&
      !ruleMatchesTemplate(params, {
        purpose: prefill.purpose,
        operation: prefill.operation,
        amount: prefill.amount,
        date: prefill.date,
      })
    ) {
      setWarnMismatch(true)
      return
    }
    setWarnMismatch(false)
    void save(params)
  }

  return (
    <form className="rule-quick-form" onSubmit={submit}>
      <div className="form-grid">
        <label className="field">
          <span>Категория</span>
          <CategorySelect
            categories={categories}
            value={categoryId}
            onChange={setCategoryId}
            onCreated={(c) => onCategoriesChanged([...categories, c])}
            required
          />
        </label>
        <label className="field">
          <span>Назначение платежа (включая регулярные выражения)</span>
          <input value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="например: оплата аренды" />
        </label>
        <label className="field">
          <span>Операция (включая регулярные выражения)</span>
          <input value={operation} onChange={(e) => setOperation(e.target.value)} placeholder="например: перевод" />
        </label>
      </div>

      <div className="form-section-title">Дополнительно (по сумме и дню месяца)</div>
      <div className="form-grid">
        <label className="field">
          <span>Сумма от</span>
          <input value={min} onChange={(e) => setMin(e.target.value)} placeholder="например: 347.85" inputMode="decimal" />
        </label>
        <label className="field">
          <span>Сумма до</span>
          <input value={max} onChange={(e) => setMax(e.target.value)} placeholder="например: 347.85" inputMode="decimal" />
        </label>
        <label className="field">
          <span>День месяца от</span>
          <input value={dayFrom} onChange={(e) => setDayFrom(e.target.value)} placeholder="1–31" inputMode="numeric" />
        </label>
        <label className="field">
          <span>День месяца до</span>
          <input value={dayTo} onChange={(e) => setDayTo(e.target.value)} placeholder="1–31" inputMode="numeric" />
        </label>
      </div>

      {warnMismatch && (
        <div className="warning">
          <p>Правило не применится на эту запись.</p>
          <div className="row">
            <button
              type="button"
              onClick={() => {
                setWarnMismatch(false)
                void save(buildParams())
              }}
            >
              Сохранить всё равно
            </button>
            <button type="button" onClick={() => setWarnMismatch(false)}>
              Отмена
            </button>
          </div>
        </div>
      )}
      {error && <div className="error">{error}</div>}
      <div className="row">
        <button type="submit" disabled={saving}>
          {saving ? 'Создание…' : 'Создать правило'}
        </button>
        <button type="button" onClick={onClose}>
          Отмена
        </button>
      </div>
    </form>
  )
}

import { useState, type FormEvent } from 'react'
import { api, HttpError } from '../api'
import { buildRuleParams, ruleMatchesTemplate } from '../ruleHelpers'

interface Category {
  id: number
  name: string
}

export interface RulePrefill {
  purpose: string | null
  operation: string | null
  categoryId: number | null
}

interface RuleQuickFormProps {
  categories: Category[]
  prefill: RulePrefill
  onClose: () => void
  onCreated: () => void
}

/**
 * Compact inline "create rule from example" form shown under a transaction row.
 * Pre-fills the counterparty and operation from the transaction; the user picks
 * the category and confirms. Priority defaults to 100 (above the built-in
 * defaults), name is left empty.
 */
export function RuleQuickForm({ categories, prefill, onClose, onCreated }: RuleQuickFormProps) {
  const [categoryId, setCategoryId] = useState(prefill.categoryId != null ? String(prefill.categoryId) : '')
  const [purpose, setPurpose] = useState(prefill.purpose ?? '')
  const [operation, setOperation] = useState(prefill.operation ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const params = buildRuleParams({ purpose, operation, min: '', max: '', dayFrom: '', dayTo: '' })
    const hasConditions = Object.keys(params).length > 0
    if (hasConditions) {
      const matches = ruleMatchesTemplate(params, {
        purpose: prefill.purpose,
        operation: prefill.operation,
      })
      if (!matches) {
        const ok = window.confirm('Правило не применится на эту запись. Нажмите «ОК», чтобы продолжить.')
        if (!ok) return
      }
    }
    setSaving(true)
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

  return (
    <form className="rule-quick-form" onSubmit={submit}>
      <div className="form-grid">
        <label className="field">
          <span>Категория</span>
          <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
            <option value="">— выберите —</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
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

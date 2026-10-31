import { useEffect, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { api, HttpError } from '../api'
import { buildRuleParams } from '../ruleHelpers'
import { CATEGORY_ICONS, DEFAULT_ICON } from '../icons'
import { CategorySelect } from '../components/CategorySelect'

interface Category {
  id: number
  parentId: number | null
  name: string
  icon: string
}

interface Rule {
  id: number
  name: string | null
  params: Record<string, string | number>
  categoryId: number
  priority: number
  autoLearned: boolean
}

interface RulePrefill {
  purpose: string | null
  operation: string | null
  categoryId: number | null
}

function paramsLabel(rule: Rule) {
  const p = rule.params ?? {}
  const parts: string[] = []
  if (p.purpose_contains != null) parts.push(`назначение платежа содержит «${p.purpose_contains}»`)
  if (p.purpose_regex != null) parts.push(`назначение платежа (regex) «${p.purpose_regex}»`)
  if (p.operation_contains != null) parts.push(`операция содержит «${p.operation_contains}»`)
  if (p.operation_regex != null) parts.push(`операция (regex) «${p.operation_regex}»`)
  if (p.min != null || p.max != null) parts.push(`сумма ${p.min ?? '—'}…${p.max ?? '—'}`)
  if (p.day_from != null || p.day_to != null) {
    parts.push(p.day_from === p.day_to ? `день ${p.day_from}` : `день ${p.day_from ?? '—'}…${p.day_to ?? '—'}`)
  }
  return parts.length > 0 ? parts.join('; ') : '—'
}

interface CategoryForm {
  name: string
  parentId: string
  icon: string
}

interface RuleForm {
  name: string
  categoryId: string
  priority: string
  purpose: string
  operation: string
  min: string
  max: string
  dayFrom: string
  dayTo: string
}

const emptyCategory: CategoryForm = { name: '', parentId: '0', icon: DEFAULT_ICON }

const emptyRule: RuleForm = {
  name: '',
  categoryId: '',
  priority: '100',
  purpose: '',
  operation: '',
  min: '',
  max: '',
  dayFrom: '',
  dayTo: '',
}

export function RulesPage() {
  const location = useLocation()
  const [categories, setCategories] = useState<Category[]>([])
  const [rules, setRules] = useState<Rule[]>([])
  const [error, setError] = useState<string | null>(null)

  const [catEditingId, setCatEditingId] = useState<number | null>(null)
  const [catForm, setCatForm] = useState<CategoryForm>(emptyCategory)
  const [catFormError, setCatFormError] = useState<string | null>(null)
  const [showCatForm, setShowCatForm] = useState(false)

  const [ruleEditingId, setRuleEditingId] = useState<number | null>(null)
  const [ruleForm, setRuleForm] = useState<RuleForm>(emptyRule)
  const [ruleFormError, setRuleFormError] = useState<string | null>(null)
  const [showRuleForm, setShowRuleForm] = useState(false)
  const [applyMsg, setApplyMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const [applying, setApplying] = useState(false)

  async function load() {
    try {
      const [cats, rs] = await Promise.all([
        api.get<Category[]>('/api/categories'),
        api.get<Rule[]>('/api/rules'),
      ])
      setCategories(cats)
      setRules(rs)
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось загрузить данные')
    }
  }

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    const prefill = (location.state as { rulePrefill?: RulePrefill } | null)?.rulePrefill
    if (prefill) {
      setRuleEditingId(null)
      setRuleForm({
        name: '',
        categoryId: prefill.categoryId != null ? String(prefill.categoryId) : '',
        priority: '100',
        purpose: prefill.purpose ?? '',
        operation: prefill.operation ?? '',
        min: '',
        max: '',
        dayFrom: '',
        dayTo: '',
      })
      setRuleFormError(null)
      setShowRuleForm(true)
    }
  }, [location.state])

  function errMessage(err: unknown) {
    if (err instanceof HttpError && err.fieldErrors.length > 0) {
      return err.fieldErrors.map((f) => f.message).join('; ')
    }
    return err instanceof HttpError ? err.message : 'Ошибка сохранения'
  }

  function categoryName(id: number | null | undefined) {
    if (id == null) return '—'
    return categories.find((c) => c.id === id)?.name ?? `#${id}`
  }

  // ---- categories ----
  function startCatCreate() {
    setCatEditingId(null)
    setCatForm(emptyCategory)
    setCatFormError(null)
    setShowCatForm(true)
  }

  function startCatEdit(c: Category) {
    setCatEditingId(c.id)
    setCatForm({ name: c.name, parentId: c.parentId == null ? '0' : String(c.parentId), icon: c.icon ?? DEFAULT_ICON })
    setCatFormError(null)
    setShowCatForm(true)
  }

  function closeCatForm() {
    setShowCatForm(false)
    setCatForm(emptyCategory)
    setCatFormError(null)
  }

  async function saveCategory(e: FormEvent) {
    e.preventDefault()
    setCatFormError(null)
    try {
      const payload = {
        name: catForm.name,
        parentId: catForm.parentId === '0' ? null : Number(catForm.parentId),
        icon: catForm.icon,
      }
      if (catEditingId === null) {
        await api.post<Category>('/api/categories', payload)
      } else {
        await api.put<Category>(`/api/categories/${catEditingId}`, payload)
      }
      closeCatForm()
      await load()
    } catch (err) {
      setCatFormError(errMessage(err))
    }
  }

  async function deleteCategory(c: Category) {
    if (!window.confirm(`Удалить категорию «${c.name}»? Связанные правила будут удалены, операции останутся без категории.`)) return
    try {
      await api.del(`/api/categories/${c.id}`)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось удалить категорию')
    }
  }

  // ---- rules ----
  function startRuleCreate() {
    setRuleEditingId(null)
    setRuleForm(emptyRule)
    setRuleFormError(null)
    setShowRuleForm(true)
  }

  function startRuleEdit(r: Rule) {
    setRuleEditingId(r.id)
    const p = r.params ?? {}
    setRuleForm({
      name: r.name ?? '',
      categoryId: String(r.categoryId),
      priority: String(r.priority),
      purpose:
        p.purpose_regex != null
          ? String(p.purpose_regex)
          : p.purpose_contains != null
            ? String(p.purpose_contains)
            : '',
      operation:
        p.operation_regex != null
          ? String(p.operation_regex)
          : p.operation_contains != null
            ? String(p.operation_contains)
            : '',
      min: p.min != null ? String(p.min) : '',
      max: p.max != null ? String(p.max) : '',
      dayFrom: p.day_from != null ? String(p.day_from) : '',
      dayTo: p.day_to != null ? String(p.day_to) : '',
    })
    setRuleFormError(null)
    setShowRuleForm(true)
  }

  function closeRuleForm() {
    setShowRuleForm(false)
    setRuleForm(emptyRule)
    setRuleFormError(null)
  }

  async function saveRule(e: FormEvent) {
    e.preventDefault()
    setRuleFormError(null)
    try {
      const payload = {
        name: ruleForm.name.trim() === '' ? null : ruleForm.name.trim(),
        params: buildRuleParams(ruleForm),
        categoryId: Number(ruleForm.categoryId),
        priority: ruleForm.priority === '' ? null : Number(ruleForm.priority),
      }
      if (ruleEditingId === null) {
        await api.post<Rule>('/api/rules', payload)
      } else {
        await api.put<Rule>(`/api/rules/${ruleEditingId}`, payload)
      }
      closeRuleForm()
      await load()
    } catch (err) {
      setRuleFormError(errMessage(err))
    }
  }

  async function deleteRule(r: Rule) {
    if (!window.confirm('Удалить правило?')) return
    try {
      await api.del(`/api/rules/${r.id}`)
      await load()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось удалить правило')
    }
  }

  async function applyRules() {
    setApplyMsg(null)
    setApplying(true)
    try {
      const res = await api.post<{ recategorized: number }>('/api/rules/apply')
      setApplyMsg({
        ok: true,
        text: res.recategorized === 0
          ? 'Все операции уже соответствуют правилам'
          : `Перекатегоризировано операций: ${res.recategorized}`,
      })
    } catch (e) {
      setApplyMsg({
        ok: false,
        text: e instanceof HttpError ? e.message : 'Не удалось применить правила',
      })
    } finally {
      setApplying(false)
    }
  }

  return (
    <div className="page">
      <h1>Правила и категории</h1>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <div className="card-head">
          <h2>Категории</h2>
          <button type="button" className="btn-ghost btn-sm" onClick={showCatForm ? closeCatForm : startCatCreate}>
            {showCatForm ? 'Скрыть форму' : '+ Категория'}
          </button>
        </div>

        <div className="chip-list">
          {categories.map((c) => (
            <span key={c.id} className="chip" onClick={() => startCatEdit(c)} title="Нажмите, чтобы изменить">
              <span className="chip-icon">{c.icon}</span>{c.name}
              <button
                type="button"
                className="chip-x"
                title="Удалить"
                onClick={(e) => {
                  e.stopPropagation()
                  deleteCategory(c)
                }}
              >
                ×
              </button>
            </span>
          ))}
          {categories.length === 0 && <span className="muted">Категорий пока нет — создайте первую</span>}
        </div>

        {showCatForm && (
          <form className="rule-quick-form" onSubmit={saveCategory}>
            <div className="form-grid">
              <label className="field">
                <span>Название</span>
                <input
                  value={catForm.name}
                  onChange={(e) => setCatForm({ ...catForm, name: e.target.value })}
                  required
                />
              </label>
              <label className="field">
                <span>Родительская категория</span>
                <select
                  value={catForm.parentId}
                  onChange={(e) => setCatForm({ ...catForm, parentId: e.target.value })}
                >
                  <option value="0">— нет —</option>
                  {categories
                    .filter((c) => c.id !== catEditingId)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </label>
            </div>
            <div className="field">
              <span>Значок</span>
              <div className="icon-picker">
                {CATEGORY_ICONS.map((icon) => (
                  <button
                    key={icon}
                    type="button"
                    className={`icon-option${catForm.icon === icon ? ' selected' : ''}`}
                    onClick={() => setCatForm({ ...catForm, icon })}
                    title={icon}
                  >
                    {icon}
                  </button>
                ))}
              </div>
            </div>
            {catFormError && <div className="error">{catFormError}</div>}
            <div className="row">
              <button type="submit">{catEditingId === null ? 'Добавить' : 'Сохранить'}</button>
              <button type="button" className="btn-secondary" onClick={closeCatForm}>
                Отмена
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Правила</h2>
          <div className="row">
            <button type="button" className="btn-secondary btn-sm" onClick={applyRules} disabled={applying}>
              {applying ? 'Применяю…' : 'Применить правила'}
            </button>
            <button type="button" className="btn-sm" onClick={showRuleForm ? closeRuleForm : startRuleCreate}>
              {showRuleForm ? 'Скрыть форму' : '+ Правило'}
            </button>
          </div>
        </div>

        <p className="muted">Правило срабатывает, если совпадают все заполненные условия. Пустые поля игнорируются.</p>

        {applyMsg && <div className={applyMsg.ok ? 'success' : 'error'}>{applyMsg.text}</div>}

        {showRuleForm && (
          <form className="rule-quick-form" onSubmit={saveRule}>
            <div className="form-grid">
              <label className="field">
                <span>Имя правила</span>
                <input
                  value={ruleForm.name}
                  onChange={(e) => setRuleForm({ ...ruleForm, name: e.target.value })}
                  placeholder="например: Продукты — Магнит"
                />
              </label>
              <label className="field">
                <span>Категория</span>
                <CategorySelect
                  categories={categories}
                  value={ruleForm.categoryId}
                  onChange={(v) => setRuleForm({ ...ruleForm, categoryId: v })}
                  onCreated={(c) => setCategories([...categories, { ...c, parentId: null }])}
                  required
                />
              </label>
            </div>

            <div className="form-section-title">Что искать</div>
            <div className="form-grid">
              <label className="field">
                <span>Назначение платежа (включая регулярные выражения)</span>
                <input
                  value={ruleForm.purpose}
                  onChange={(e) => setRuleForm({ ...ruleForm, purpose: e.target.value })}
                  placeholder="например: оплата аренды или ^оплата"
                />
              </label>
              <label className="field">
                <span>Операция (включая регулярные выражения)</span>
                <input
                  value={ruleForm.operation}
                  onChange={(e) => setRuleForm({ ...ruleForm, operation: e.target.value })}
                  placeholder="например: перевод или ^покупка"
                />
              </label>
            </div>

            <div className="form-section-title">Дополнительно</div>
            <div className="form-grid">
              <label className="field">
                <span>Сумма от</span>
                <input
                  type="number"
                  step="0.01"
                  min={0}
                  value={ruleForm.min}
                  onChange={(e) => setRuleForm({ ...ruleForm, min: e.target.value })}
                />
              </label>
              <label className="field">
                <span>Сумма до</span>
                <input
                  type="number"
                  step="0.01"
                  min={0}
                  value={ruleForm.max}
                  onChange={(e) => setRuleForm({ ...ruleForm, max: e.target.value })}
                />
              </label>
              <label className="field">
                <span>День месяца от (1–31)</span>
                <input
                  type="number"
                  min={1}
                  max={31}
                  value={ruleForm.dayFrom}
                  onChange={(e) => setRuleForm({ ...ruleForm, dayFrom: e.target.value })}
                />
              </label>
              <label className="field">
                <span>День месяца до (1–31)</span>
                <input
                  type="number"
                  min={1}
                  max={31}
                  value={ruleForm.dayTo}
                  onChange={(e) => setRuleForm({ ...ruleForm, dayTo: e.target.value })}
                />
              </label>
              <label className="field">
                <span>Приоритет</span>
                <input
                  type="number"
                  value={ruleForm.priority}
                  onChange={(e) => setRuleForm({ ...ruleForm, priority: e.target.value })}
                />
              </label>
            </div>

            {ruleFormError && <div className="error">{ruleFormError}</div>}
            <div className="row">
              <button type="submit">{ruleEditingId === null ? 'Добавить правило' : 'Сохранить'}</button>
              <button type="button" className="btn-secondary" onClick={closeRuleForm}>
                Отмена
              </button>
            </div>
          </form>
        )}

        <table className="table" style={{ marginTop: showRuleForm || applyMsg ? 16 : 0 }}>
          <thead>
            <tr>
              <th>Имя</th>
              <th>Условие</th>
              <th>Категория</th>
              <th>Приоритет</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.id}>
                <td>{r.name ?? '—'}</td>
                <td>{paramsLabel(r)}</td>
                <td>
                  <span className="pill">{categoryName(r.categoryId)}</span>
                </td>
                <td>{r.priority}</td>
                <td>
                  <div className="actions">
                    <button className="btn-ghost btn-sm" onClick={() => startRuleEdit(r)}>
                      Изменить
                    </button>
                    <button className="btn-danger btn-sm" onClick={() => deleteRule(r)}>
                      Удалить
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {rules.length === 0 && (
              <tr>
                <td colSpan={5}>Правил нет</td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  )
}

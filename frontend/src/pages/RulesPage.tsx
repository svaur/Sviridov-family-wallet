import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'

interface Category {
  id: number
  parentId: number | null
  name: string
}

interface Rule {
  id: number
  type: string
  params: Record<string, string | number>
  categoryId: number
  priority: number
  autoLearned: boolean
}

const RULE_TYPES = [
  { value: 'merchant_contains', label: 'Содержит текст' },
  { value: 'merchant_regex', label: 'Регулярное выражение' },
  { value: 'amount_range', label: 'Диапазон суммы' },
  { value: 'day_of_month', label: 'День месяца' },
]

function typeLabel(type: string) {
  return RULE_TYPES.find((t) => t.value === type)?.label ?? type
}

function paramsLabel(rule: Rule) {
  const p = rule.params ?? {}
  switch (rule.type) {
    case 'merchant_contains':
      return `содержит «${p.value ?? ''}»`
    case 'merchant_regex':
      return `regex «${p.pattern ?? ''}»`
    case 'amount_range':
      return `от ${p.min ?? '—'} до ${p.max ?? '—'}`
    case 'day_of_month':
      return `день ${p.day ?? ''}`
    default:
      return JSON.stringify(p)
  }
}

interface CategoryForm {
  name: string
  parentId: string
}

interface RuleForm {
  type: string
  categoryId: string
  priority: string
  value: string
  pattern: string
  min: string
  max: string
  day: string
}

const emptyCategory: CategoryForm = { name: '', parentId: '0' }

const emptyRule: RuleForm = {
  type: 'merchant_contains',
  categoryId: '',
  priority: '100',
  value: '',
  pattern: '',
  min: '',
  max: '',
  day: '',
}

export function RulesPage() {
  const { user, logout } = useAuth()
  const [categories, setCategories] = useState<Category[]>([])
  const [rules, setRules] = useState<Rule[]>([])
  const [error, setError] = useState<string | null>(null)

  const [catEditingId, setCatEditingId] = useState<number | null>(null)
  const [catForm, setCatForm] = useState<CategoryForm>(emptyCategory)
  const [catFormError, setCatFormError] = useState<string | null>(null)

  const [ruleEditingId, setRuleEditingId] = useState<number | null>(null)
  const [ruleForm, setRuleForm] = useState<RuleForm>(emptyRule)
  const [ruleFormError, setRuleFormError] = useState<string | null>(null)

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
  }

  function startCatEdit(c: Category) {
    setCatEditingId(c.id)
    setCatForm({ name: c.name, parentId: c.parentId == null ? '0' : String(c.parentId) })
    setCatFormError(null)
  }

  async function saveCategory(e: FormEvent) {
    e.preventDefault()
    setCatFormError(null)
    try {
      const payload = {
        name: catForm.name,
        parentId: catForm.parentId === '0' ? null : Number(catForm.parentId),
      }
      if (catEditingId === null) {
        await api.post<Category>('/api/categories', payload)
      } else {
        await api.put<Category>(`/api/categories/${catEditingId}`, payload)
      }
      startCatCreate()
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
  }

  function startRuleEdit(r: Rule) {
    setRuleEditingId(r.id)
    const p = r.params ?? {}
    setRuleForm({
      type: r.type,
      categoryId: String(r.categoryId),
      priority: String(r.priority),
      value: p.value != null ? String(p.value) : '',
      pattern: p.pattern != null ? String(p.pattern) : '',
      min: p.min != null ? String(p.min) : '',
      max: p.max != null ? String(p.max) : '',
      day: p.day != null ? String(p.day) : '',
    })
    setRuleFormError(null)
  }

  function buildParams(f: RuleForm): Record<string, string | number> {
    switch (f.type) {
      case 'merchant_contains':
        return { value: f.value }
      case 'merchant_regex':
        return { pattern: f.pattern }
      case 'amount_range': {
        const params: Record<string, string | number> = {}
        if (f.min !== '') params.min = Number(f.min)
        if (f.max !== '') params.max = Number(f.max)
        return params
      }
      case 'day_of_month':
        return { day: Number(f.day) }
      default:
        return {}
    }
  }

  async function saveRule(e: FormEvent) {
    e.preventDefault()
    setRuleFormError(null)
    try {
      const payload = {
        type: ruleForm.type,
        params: buildParams(ruleForm),
        categoryId: Number(ruleForm.categoryId),
        priority: ruleForm.priority === '' ? null : Number(ruleForm.priority),
      }
      if (ruleEditingId === null) {
        await api.post<Rule>('/api/rules', payload)
      } else {
        await api.put<Rule>(`/api/rules/${ruleEditingId}`, payload)
      }
      startRuleCreate()
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

  return (
    <div className="page">
      <header className="topbar">
        <h1>Правила и категории</h1>
        <div className="topbar-right">
          <Link className="link-btn" to="/">← Карты</Link>
          <Link className="link-btn" to="/transactions">Операции</Link>
          <span className="muted">{user?.username}</span>
          <button onClick={() => logout()}>Выйти</button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="card">
        <h2>Категории</h2>
        <form className="form-grid" onSubmit={saveCategory}>
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
          {catFormError && <div className="error">{catFormError}</div>}
          <div className="row">
            <button type="submit">{catEditingId === null ? 'Добавить' : 'Сохранить'}</button>
            {catEditingId !== null && (
              <button type="button" onClick={startCatCreate}>
                Отмена
              </button>
            )}
          </div>
        </form>

        <table className="table">
          <thead>
            <tr>
              <th>Название</th>
              <th>Родитель</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {categories.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>{categoryName(c.parentId)}</td>
                <td className="row">
                  <button onClick={() => startCatEdit(c)}>Изменить</button>
                  <button onClick={() => deleteCategory(c)}>Удалить</button>
                </td>
              </tr>
            ))}
            {categories.length === 0 && (
              <tr>
                <td colSpan={3}>Категорий нет</td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Правила</h2>
        <form className="form-grid" onSubmit={saveRule}>
          <label className="field">
            <span>Тип</span>
            <select
              value={ruleForm.type}
              onChange={(e) => setRuleForm({ ...ruleForm, type: e.target.value })}
            >
              {RULE_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Категория</span>
            <select
              value={ruleForm.categoryId}
              onChange={(e) => setRuleForm({ ...ruleForm, categoryId: e.target.value })}
              required
            >
              <option value="">— выберите —</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Приоритет</span>
            <input
              type="number"
              value={ruleForm.priority}
              onChange={(e) => setRuleForm({ ...ruleForm, priority: e.target.value })}
            />
          </label>

          {ruleForm.type === 'merchant_contains' && (
            <label className="field">
              <span>Содержит текст</span>
              <input
                value={ruleForm.value}
                onChange={(e) => setRuleForm({ ...ruleForm, value: e.target.value })}
                placeholder="например: korma"
              />
            </label>
          )}
          {ruleForm.type === 'merchant_regex' && (
            <label className="field">
              <span>Регулярное выражение</span>
              <input
                value={ruleForm.pattern}
                onChange={(e) => setRuleForm({ ...ruleForm, pattern: e.target.value })}
                placeholder="например: ^korma"
              />
            </label>
          )}
          {ruleForm.type === 'amount_range' && (
            <>
              <label className="field">
                <span>Мин. сумма</span>
                <input
                  type="number"
                  step="0.01"
                  value={ruleForm.min}
                  onChange={(e) => setRuleForm({ ...ruleForm, min: e.target.value })}
                />
              </label>
              <label className="field">
                <span>Макс. сумма</span>
                <input
                  type="number"
                  step="0.01"
                  value={ruleForm.max}
                  onChange={(e) => setRuleForm({ ...ruleForm, max: e.target.value })}
                />
              </label>
            </>
          )}
          {ruleForm.type === 'day_of_month' && (
            <label className="field">
              <span>День месяца (1–31)</span>
              <input
                type="number"
                min={1}
                max={31}
                value={ruleForm.day}
                onChange={(e) => setRuleForm({ ...ruleForm, day: e.target.value })}
              />
            </label>
          )}

          {ruleFormError && <div className="error">{ruleFormError}</div>}
          <div className="row">
            <button type="submit">{ruleEditingId === null ? 'Добавить' : 'Сохранить'}</button>
            {ruleEditingId !== null && (
              <button type="button" onClick={startRuleCreate}>
                Отмена
              </button>
            )}
          </div>
        </form>

        <table className="table">
          <thead>
            <tr>
              <th>Тип</th>
              <th>Условие</th>
              <th>Категория</th>
              <th>Приоритет</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.id}>
                <td>{typeLabel(r.type)}</td>
                <td>{paramsLabel(r)}</td>
                <td>{categoryName(r.categoryId)}</td>
                <td>{r.priority}</td>
                <td className="row">
                  <button onClick={() => startRuleEdit(r)}>Изменить</button>
                  <button onClick={() => deleteRule(r)}>Удалить</button>
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

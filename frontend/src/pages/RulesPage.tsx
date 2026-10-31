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
  name: string | null
  params: Record<string, string | number>
  categoryId: number
  priority: number
  autoLearned: boolean
}

function isRegex(value: string): boolean {
  return /[\\^$*+?()[\]{}|]/.test(value)
}

function paramsLabel(rule: Rule) {
  const p = rule.params ?? {}
  const parts: string[] = []
  if (p.merchant_contains != null) parts.push(`назначение платежа содержит «${p.merchant_contains}»`)
  if (p.merchant_regex != null) parts.push(`назначение платежа (regex) «${p.merchant_regex}»`)
  if (p.min != null || p.max != null) parts.push(`сумма ${p.min ?? '—'}…${p.max ?? '—'}`)
  if (p.day_from != null || p.day_to != null) {
    parts.push(p.day_from === p.day_to ? `день ${p.day_from}` : `день ${p.day_from ?? '—'}…${p.day_to ?? '—'}`)
  }
  return parts.length > 0 ? parts.join('; ') : '—'
}

interface CategoryForm {
  name: string
  parentId: string
}

interface RuleForm {
  name: string
  categoryId: string
  priority: string
  purpose: string
  min: string
  max: string
  dayFrom: string
  dayTo: string
}

const emptyCategory: CategoryForm = { name: '', parentId: '0' }

const emptyRule: RuleForm = {
  name: '',
  categoryId: '',
  priority: '100',
  purpose: '',
  min: '',
  max: '',
  dayFrom: '',
  dayTo: '',
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
      name: r.name ?? '',
      categoryId: String(r.categoryId),
      priority: String(r.priority),
      purpose:
        p.merchant_regex != null
          ? String(p.merchant_regex)
          : p.merchant_contains != null
            ? String(p.merchant_contains)
            : '',
      min: p.min != null ? String(p.min) : '',
      max: p.max != null ? String(p.max) : '',
      dayFrom: p.day_from != null ? String(p.day_from) : '',
      dayTo: p.day_to != null ? String(p.day_to) : '',
    })
    setRuleFormError(null)
  }

  function buildParams(f: RuleForm): Record<string, string | number> {
    const params: Record<string, string | number> = {}
    const purpose = f.purpose.trim()
    if (purpose !== '') {
      if (isRegex(purpose)) {
        params.merchant_regex = purpose
      } else {
        params.merchant_contains = purpose
      }
    }
    if (f.min !== '') params.min = Number(f.min)
    if (f.max !== '') params.max = Number(f.max)
    if (f.dayFrom !== '') params.day_from = Number(f.dayFrom)
    if (f.dayTo !== '') params.day_to = Number(f.dayTo)
    return params
  }

  async function saveRule(e: FormEvent) {
    e.preventDefault()
    setRuleFormError(null)
    try {
      const payload = {
        name: ruleForm.name.trim() === '' ? null : ruleForm.name.trim(),
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
        <p className="muted">Правило срабатывает, если совпадают все заполненные условия. Пустые поля игнорируются.</p>
        <form className="form-grid" onSubmit={saveRule}>
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
          <label className="field">
            <span>Назначение платежа (включая регулярные выражения)</span>
            <input
              value={ruleForm.purpose}
              onChange={(e) => setRuleForm({ ...ruleForm, purpose: e.target.value })}
              placeholder="например: korma или ^korma"
            />
          </label>
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

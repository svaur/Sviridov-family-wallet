import { useState } from 'react'
import { api, HttpError } from '../api'
import { CATEGORY_ICONS, DEFAULT_ICON } from '../icons'

interface Category {
  id: number
  name: string
  icon: string
}

interface CategoryPickerProps {
  categories: Category[]
  onAssign: (categoryId: number) => void | Promise<void>
  onCategoriesChanged: (categories: Category[]) => void
}

/**
 * "Assign category manually" flow for a transaction row: a single button that
 * expands into a category dropdown (with a "create new category" entry), which
 * in turn swaps into an inline create-category form (name + emoji icon picker).
 * Creating a category immediately assigns it to the row.
 */
export function CategoryPicker({ categories, onAssign, onCategoriesChanged }: CategoryPickerProps) {
  const [mode, setMode] = useState<'closed' | 'pick' | 'create'>('closed')
  const [selected, setSelected] = useState('')
  const [name, setName] = useState('')
  const [icon, setIcon] = useState(DEFAULT_ICON)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    setMode('closed')
    setSelected('')
    setName('')
    setIcon(DEFAULT_ICON)
    setError(null)
  }

  function handleSelect(value: string) {
    if (value === '__create__') {
      setSelected('')
      setMode('create')
    } else {
      setSelected(value)
    }
  }

  async function assign() {
    if (selected === '' || selected === '__create__') return
    setBusy(true)
    setError(null)
    try {
      await onAssign(Number(selected))
      close()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось назначить категорию')
    } finally {
      setBusy(false)
    }
  }

  async function create() {
    const trimmed = name.trim()
    if (trimmed === '') return
    setBusy(true)
    setError(null)
    try {
      const created = await api.post<Category>('/api/categories', { name: trimmed, icon })
      onCategoriesChanged([...categories, created])
      await onAssign(created.id)
      close()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось создать категорию')
    } finally {
      setBusy(false)
    }
  }

  if (mode === 'closed') {
    return (
      <button type="button" className="btn-secondary btn-sm" onClick={() => setMode('pick')}>
        Назначить категорию вручную
      </button>
    )
  }

  if (mode === 'create') {
    return (
      <div className="cat-create">
        <label className="field">
          <span>Новая категория</span>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Название"
            autoFocus
          />
        </label>
        <div className="icon-picker">
          {CATEGORY_ICONS.map((i) => (
            <button
              key={i}
              type="button"
              className={`icon-option ${icon === i ? 'selected' : ''}`}
              onClick={() => setIcon(i)}
            >
              {i}
            </button>
          ))}
        </div>
        {error && <div className="error">{error}</div>}
        <div className="row">
          <button className="btn-sm" onClick={create} disabled={busy || name.trim() === ''}>
            {busy ? 'Создание…' : 'Создать'}
          </button>
          <button className="btn-secondary btn-sm" onClick={() => setMode('pick')}>
            Назад
          </button>
          <button className="btn-secondary btn-sm" onClick={close}>
            Отмена
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="row">
      <select value={selected} onChange={(e) => handleSelect(e.target.value)} aria-label="Категория вручную">
        <option value="">— категория —</option>
        <option value="__create__">＋ Создать новую категорию</option>
        {categories.map((c) => (
          <option key={c.id} value={c.id}>
            {c.icon} {c.name}
          </option>
        ))}
      </select>
      <button className="btn-sm" onClick={assign} disabled={selected === '' || selected === '__create__' || busy}>
        Назначить
      </button>
      <button className="btn-secondary btn-sm" onClick={close}>
        Отмена
      </button>
      {error && <div className="error">{error}</div>}
    </div>
  )
}

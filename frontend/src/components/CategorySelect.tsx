import { useState } from 'react'
import { api, HttpError } from '../api'
import { CATEGORY_ICONS, DEFAULT_ICON } from '../icons'

interface Category {
  id: number
  name: string
  icon: string
}

interface CategorySelectProps {
  categories: Category[]
  value: string
  onChange: (value: string) => void
  onCreated: (category: Category) => void
  /** Show a leading "auto (by rules)" empty option (value ''). */
  allowAuto?: boolean
  autoLabel?: string
  /** Mark the select as required (blocks submit until a category is chosen). */
  required?: boolean
}

/**
 * Category `<select>` with an inline "create new category" entry. Choosing
 * "＋ Создать новую категорию" swaps the select for a small name + emoji-icon
 * form; on save the category is created and immediately selected.
 */
export function CategorySelect({
  categories,
  value,
  onChange,
  onCreated,
  allowAuto = false,
  autoLabel = '— авто (по правилам) —',
  required = false,
}: CategorySelectProps) {
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [icon, setIcon] = useState(DEFAULT_ICON)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setCreating(false)
    setName('')
    setIcon(DEFAULT_ICON)
    setError(null)
  }

  async function create() {
    const trimmed = name.trim()
    if (trimmed === '') return
    setBusy(true)
    setError(null)
    try {
      const created = await api.post<Category>('/api/categories', { name: trimmed, icon })
      onCreated(created)
      onChange(String(created.id))
      reset()
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось создать категорию')
    } finally {
      setBusy(false)
    }
  }

  if (creating) {
    return (
      <div className="cat-create">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Название категории"
          autoFocus
        />
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
          <button className="btn-secondary btn-sm" onClick={reset}>
            Отмена
          </button>
        </div>
      </div>
    )
  }

  return (
    <select
      value={value}
      required={required}
      onChange={(e) => {
        if (e.target.value === '__create__') {
          setCreating(true)
        } else {
          onChange(e.target.value)
        }
      }}
    >
      {allowAuto ? (
        <option value="">{autoLabel}</option>
      ) : (
        <option value="">— выберите —</option>
      )}
      <option value="__create__">＋ Создать новую категорию</option>
      {categories.map((c) => (
        <option key={c.id} value={c.id}>
          {c.icon} {c.name}
        </option>
      ))}
    </select>
  )
}

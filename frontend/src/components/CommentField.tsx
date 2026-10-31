import { useState } from 'react'
import { api, HttpError } from '../api'

interface CommentFieldProps {
  id: number
  comment: string | null
  onSaved: (comment: string | null) => void
}

/**
 * Inline comment editor for a transaction: a compact "＋ Комментарий" / "💬 …"
 * toggle that expands into an input + save/cancel. Saves via
 * PUT /api/transactions/{id}/comment; blank input clears the comment.
 */
export function CommentField({ id, comment, onSaved }: CommentFieldProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(comment ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    const trimmed = value.trim()
    setBusy(true)
    setError(null)
    try {
      await api.put(`/api/transactions/${id}/comment`, { comment: value })
      onSaved(trimmed === '' ? null : trimmed)
      setEditing(false)
    } catch (e) {
      setError(e instanceof HttpError ? e.message : 'Не удалось сохранить комментарий')
    } finally {
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <div className="comment-edit">
        <input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="Комментарий"
          maxLength={500}
          autoFocus
        />
        <button className="btn-sm" onClick={save} disabled={busy}>
          {busy ? '…' : 'Сохранить'}
        </button>
        <button className="btn-secondary btn-sm" onClick={() => setEditing(false)}>
          Отмена
        </button>
        {error && <div className="error">{error}</div>}
      </div>
    )
  }

  return (
    <button
      type="button"
      className={`comment-toggle ${comment ? 'has' : ''}`}
      onClick={() => {
        setValue(comment ?? '')
        setEditing(true)
      }}
      title="Комментарий"
    >
      {comment ? `💬 ${comment}` : '＋ Комментарий'}
    </button>
  )
}

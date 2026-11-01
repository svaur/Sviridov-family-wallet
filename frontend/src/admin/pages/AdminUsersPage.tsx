import { useEffect, useState, type FormEvent } from 'react'
import { adminApi, HttpError } from '../api'

interface AdminUserRow {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  active: boolean
  mustChangePassword: boolean
  lastLoginAt: string | null
  createdAt: string
}

function fmtDate(value: string | null): string {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fullName(u: AdminUserRow): string {
  return [u.firstName, u.lastName].filter(Boolean).join(' ') || '—'
}

export function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

  // Create form
  const [showCreate, setShowCreate] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [creating, setCreating] = useState(false)

  // One-time temporary password shown after a reset.
  const [temp, setTemp] = useState<{ username: string; password: string } | null>(null)

  // Two-step delete confirmation (a plain window.confirm can be suppressed).
  const [confirmingId, setConfirmingId] = useState<number | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setUsers(await adminApi.get<AdminUserRow[]>('/api/admin/users'))
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось загрузить пользователей')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function createUser(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCreating(true)
    try {
      await adminApi.post('/api/admin/users', { username, password, firstName, lastName })
      setUsername('')
      setPassword('')
      setFirstName('')
      setLastName('')
      setShowCreate(false)
      await load()
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось создать пользователя')
    } finally {
      setCreating(false)
    }
  }

  async function setActive(u: AdminUserRow, active: boolean) {
    setError(null)
    setBusy(u.id)
    try {
      await adminApi.post(`/api/admin/users/${u.id}/${active ? 'unban' : 'ban'}`)
      await load()
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось изменить статус')
    } finally {
      setBusy(null)
    }
  }

  async function resetPassword(u: AdminUserRow) {
    setError(null)
    setBusy(u.id)
    try {
      const res = await adminApi.post<{ tempPassword: string }>(`/api/admin/users/${u.id}/reset-password`)
      setTemp({ username: u.username, password: res.tempPassword })
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось сбросить пароль')
    } finally {
      setBusy(null)
    }
  }

  async function forceLogout(u: AdminUserRow) {
    setError(null)
    setBusy(u.id)
    try {
      await adminApi.post(`/api/admin/users/${u.id}/logout`)
      await load()
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось завершить сеансы')
    } finally {
      setBusy(null)
    }
  }

  async function deleteUser(u: AdminUserRow) {
    setError(null)
    setBusy(u.id)
    try {
      await adminApi.del(`/api/admin/users/${u.id}`)
      setConfirmingId(null)
      await load()
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось удалить пользователя')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>Пользователи</h1>
        <button type="button" className={showCreate ? 'btn-secondary' : ''} onClick={() => setShowCreate((v) => !v)}>
          {showCreate ? 'Отмена' : '＋ Добавить пользователя'}
        </button>
      </div>

      {temp && (
        <div className="notice">
          Временный пароль для <b>{temp.username}</b>: <b>{temp.password}</b> — сообщите его пользователю.
          <button type="button" className="text-link" style={{ marginLeft: 10 }} onClick={() => setTemp(null)}>
            Скрыть
          </button>
        </div>
      )}

      {error && <div className="error">{error}</div>}

      {showCreate && (
        <form className="card" onSubmit={createUser}>
          <h2>Новый пользователь</h2>
          <div className="form-grid">
            <label className="field">
              <span>Логин</span>
              <input value={username} onChange={(e) => setUsername(e.target.value)} maxLength={64} required autoFocus />
            </label>
            <label className="field">
              <span>Пароль</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="не короче 8 символов"
                required
              />
            </label>
            <label className="field">
              <span>Имя</span>
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} maxLength={64} />
            </label>
            <label className="field">
              <span>Фамилия</span>
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} maxLength={64} />
            </label>
          </div>
          <p className="muted">Пользователь получит временный пароль и сменит его при первом входе.</p>
          <div className="actions">
            <button type="submit" disabled={creating}>
              {creating ? 'Создание…' : 'Создать'}
            </button>
          </div>
        </form>
      )}

      <div className="card">
        {loading ? (
          <p className="muted">Загрузка…</p>
        ) : users.length === 0 ? (
          <p className="muted">Пользователей нет.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Логин</th>
                <th>Имя</th>
                <th>Статус</th>
                <th>Последний вход</th>
                <th>Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <b>{u.username}</b>
                  </td>
                  <td>{fullName(u)}</td>
                  <td>
                    {!u.active ? (
                      <span className="badge badge-blocked">заблокирован</span>
                    ) : u.mustChangePassword ? (
                      <span className="badge badge-partial">смена пароля</span>
                    ) : (
                      <span className="badge badge-ok">активен</span>
                    )}
                  </td>
                  <td className="muted">{fmtDate(u.lastLoginAt)}</td>
                  <td>
                    <div className="tx-actions">
                      <button
                        type="button"
                        className="btn-sm btn-secondary"
                        disabled={busy === u.id}
                        onClick={() => setActive(u, !u.active)}
                      >
                        {u.active ? 'Заблокировать' : 'Разблокировать'}
                      </button>
                      <button
                        type="button"
                        className="btn-sm btn-secondary"
                        disabled={busy === u.id}
                        onClick={() => resetPassword(u)}
                      >
                        Сбросить пароль
                      </button>
                      <button
                        type="button"
                        className="btn-sm btn-secondary"
                        disabled={busy === u.id}
                        onClick={() => forceLogout(u)}
                      >
                        Завершить сеансы
                      </button>
                      {confirmingId === u.id ? (
                        <>
                          <span className="muted">Точно удалить?</span>
                          <button type="button" className="btn-sm btn-danger" disabled={busy === u.id} onClick={() => deleteUser(u)}>
                            Удалить
                          </button>
                          <button type="button" className="btn-sm btn-ghost" onClick={() => setConfirmingId(null)}>
                            Отмена
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          className="btn-sm btn-danger"
                          disabled={busy === u.id}
                          onClick={() => setConfirmingId(u.id)}
                        >
                          Удалить
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { api, HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'

/**
 * Shown instead of the whole app right after the admin reset the password:
 * the backend sets `mustChangePassword` and blocks every `/api/**` call until
 * a new password is set. Standalone (no sidebar) so nothing else is reachable.
 */
export function ForcePasswordChangePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeatPassword, setRepeatPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!user?.mustChangePassword) return <Navigate to="/" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (newPassword.length < 8) {
      setError('Новый пароль должен быть не короче 8 символов')
      return
    }
    if (newPassword !== repeatPassword) {
      setError('Пароли не совпадают')
      return
    }
    if (newPassword === currentPassword) {
      setError('Новый пароль должен отличаться от временного')
      return
    }
    setBusy(true)
    try {
      await api.put<void>('/api/me/password', { currentPassword, newPassword })
      // The backend bumps the epoch and clears the flag, so this session is
      // already stale. Leave it explicitly instead of waiting for a 401.
      await logout()
      navigate('/login', { replace: true, state: { notice: 'Пароль изменён. Войдите снова.' } })
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось сменить пароль')
    } finally {
      setBusy(false)
    }
  }

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="page page-centered">
      <form className="card card-narrow" onSubmit={onSubmit}>
        <div className="login-brand">
          <div className="login-mark">₽</div>
          <b>Sviridov · Семейный кошелёк</b>
        </div>
        <h1>Смена пароля</h1>
        <p className="login-sub">Администратор задал вам временный пароль — задайте новый, чтобы продолжить.</p>
        <label className="field">
          <span>Временный пароль</span>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            autoFocus
          />
        </label>
        <label className="field">
          <span>Новый пароль</span>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="не короче 8 символов"
            autoComplete="new-password"
          />
        </label>
        <label className="field">
          <span>Повторите новый пароль</span>
          <input
            type="password"
            value={repeatPassword}
            onChange={(e) => setRepeatPassword(e.target.value)}
            autoComplete="new-password"
          />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit" className="btn-block" disabled={busy}>
          {busy ? 'Смена пароля…' : 'Сменить пароль'}
        </button>
        <button type="button" className="text-link" style={{ marginTop: 14, width: '100%' }} onClick={onLogout}>
          Выйти
        </button>
      </form>
    </div>
  )
}

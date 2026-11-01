import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { HttpError } from '../api'
import { useAdminAuth } from '../AdminAuthContext'

export function AdminLoginPage() {
  const { admin, login } = useAdminAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (admin) return <Navigate to="/" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось войти')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page page-centered">
      <form className="card card-narrow" onSubmit={onSubmit}>
        <div className="login-brand">
          <div className="login-mark">A</div>
          <b>Sviridov · Администрирование</b>
        </div>
        <h1>Вход администратора</h1>
        <p className="login-sub">Управление пользователями и просмотр аудита.</p>
        <label className="field">
          <span>Логин</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="admin"
            autoComplete="username"
            autoFocus
          />
        </label>
        <label className="field">
          <span>Пароль</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
          />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit" className="btn-block" disabled={busy}>
          {busy ? 'Вход…' : 'Войти'}
        </button>
        <div className="login-note">Отдельная учётная запись администратора.</div>
      </form>
    </div>
  )
}

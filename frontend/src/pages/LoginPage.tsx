import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { HttpError } from '../api'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username, password)
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/'
      navigate(from, { replace: true })
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
          <div className="login-mark">₽</div>
          <b>Sviridov · Семейный кошелёк</b>
        </div>
        <h1>Вход</h1>
        <p className="login-sub">Личный и семейный учёт финансов по выпискам банка.</p>
        <label className="field">
          <span>Логин</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="ivan"
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
        <div className="login-note">Доступ по приглашению — пользователи создаются вручную.</div>
      </form>
    </div>
  )
}

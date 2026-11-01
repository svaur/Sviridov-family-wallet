import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, HttpError } from '../api'
import { displayName, useAuth, type User } from '../auth/AuthContext'

/** How long the "saved" confirmation stays on screen. */
const SAVED_MS = 3000

export function SettingsPage() {
  const { user, setUser, logout } = useAuth()
  const navigate = useNavigate()

  const [firstName, setFirstName] = useState(user?.firstName ?? '')
  const [lastName, setLastName] = useState(user?.lastName ?? '')
  const [savingProfile, setSavingProfile] = useState(false)
  const [profileSaved, setProfileSaved] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeatPassword, setRepeatPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  useEffect(() => {
    if (!profileSaved) return
    const timer = window.setTimeout(() => setProfileSaved(false), SAVED_MS)
    return () => window.clearTimeout(timer)
  }, [profileSaved])

  const profileDirty =
    firstName.trim() !== (user?.firstName ?? '') || lastName.trim() !== (user?.lastName ?? '')

  async function saveProfile(e: FormEvent) {
    e.preventDefault()
    setProfileError(null)
    setProfileSaved(false)
    setSavingProfile(true)
    try {
      const fresh = await api.put<User>('/api/me/profile', { firstName, lastName })
      setUser(fresh)
      setProfileSaved(true)
    } catch (err) {
      setProfileError(err instanceof HttpError ? err.message : 'Не удалось сохранить профиль')
    } finally {
      setSavingProfile(false)
    }
  }

  async function savePassword(e: FormEvent) {
    e.preventDefault()
    setPasswordError(null)
    if (newPassword.length < 8) {
      setPasswordError('Новый пароль должен быть не короче 8 символов')
      return
    }
    if (newPassword !== repeatPassword) {
      setPasswordError('Пароли не совпадают')
      return
    }
    if (newPassword === currentPassword) {
      setPasswordError('Новый пароль должен отличаться от текущего')
      return
    }
    setSavingPassword(true)
    try {
      await api.put<void>('/api/me/password', { currentPassword, newPassword })
      // The server bumps the password epoch, so this session is already stale.
      // Leave it explicitly instead of waiting for the next request to 401.
      await logout()
      navigate('/login', { replace: true, state: { notice: 'Пароль изменён. Войдите снова.' } })
    } catch (err) {
      setPasswordError(err instanceof HttpError ? err.message : 'Не удалось сменить пароль')
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <div className="page settings-page">
      <div className="page-head">
        <h1>Настройки</h1>
        <span className="muted">@{user?.username}</span>
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Профиль</h2>
        </div>
        <form onSubmit={saveProfile}>
          <div className="form-grid">
            <label className="field">
              <span>Имя</span>
              <input
                type="text"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                placeholder="напр. Иван"
                maxLength={64}
                autoComplete="given-name"
              />
            </label>
            <label className="field">
              <span>Фамилия</span>
              <input
                type="text"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                placeholder="напр. Свиридов"
                maxLength={64}
                autoComplete="family-name"
              />
            </label>
          </div>
          <p className="muted">
            Имя показывается в меню слева. Пусто — будет логин «{user?.username}». Сейчас:{' '}
            <b>{displayName(user)}</b>.
          </p>
          {profileError && <div className="error">{profileError}</div>}
          {profileSaved && <div className="success">Сохранено</div>}
          <div className="actions">
            <button type="submit" disabled={savingProfile || !profileDirty}>
              {savingProfile ? 'Сохранение…' : 'Сохранить'}
            </button>
          </div>
        </form>
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Безопасность</h2>
        </div>
        <form onSubmit={savePassword}>
          <div className="form-grid">
            <label className="field">
              <span>Текущий пароль</span>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                required
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
                required
              />
            </label>
            <label className="field">
              <span>Повторите новый пароль</span>
              <input
                type="password"
                value={repeatPassword}
                onChange={(e) => setRepeatPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            </label>
          </div>
          <p className="muted">
            После смены пароля все сеансы, включая этот, завершатся — на других устройствах тоже
            потребуется войти заново.
          </p>
          {passwordError && <div className="error">{passwordError}</div>}
          <div className="actions">
            <button type="submit" disabled={savingPassword}>
              {savingPassword ? 'Смена пароля…' : 'Сменить пароль'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

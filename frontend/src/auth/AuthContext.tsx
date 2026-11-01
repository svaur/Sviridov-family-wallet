import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api'

export interface User {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  /** True right after the admin reset the password — the app is locked until a new one is set. */
  mustChangePassword: boolean
}

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Applies a fresh /api/me payload (e.g. after the profile was saved). */
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const me = await api.get<User>('/api/me')
        if (!cancelled) setUserState(me)
      } catch {
        if (!cancelled) setUserState(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  // The backend rejects a stale session (password changed elsewhere, server
  // restart) with 401; drop the local user so RequireAuth sends the user to
  // the login page instead of every screen showing an error banner.
  useEffect(() => {
    const onUnauthorized = () => setUserState(null)
    window.addEventListener('wallet:unauthorized', onUnauthorized)
    return () => window.removeEventListener('wallet:unauthorized', onUnauthorized)
  }, [])

  const setUser = useCallback((fresh: User) => setUserState(fresh), [])

  async function login(username: string, password: string) {
    const me = await api.post<User>('/api/auth/login', { username, password })
    setUserState(me)
  }

  async function logout() {
    try {
      await api.post<void>('/api/auth/logout')
    } catch {
      // ignore network errors on logout
    }
    setUserState(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, setUser }}>{children}</AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

/** "Иван Свиридов", or the login when no name has been set. */
export function displayName(user: User | null): string {
  if (!user) return '—'
  return [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username
}

/** Initial for the sidebar avatar. */
export function initialOf(user: User | null): string {
  const source = user?.firstName ?? user?.username ?? '?'
  return source[0]?.toUpperCase() ?? '?'
}

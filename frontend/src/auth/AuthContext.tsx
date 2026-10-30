import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api'

export interface User {
  id: number
  username: string
}

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const me = await api.get<User>('/api/auth/me')
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  async function login(username: string, password: string) {
    const me = await api.post<User>('/api/auth/login', { username, password })
    setUser(me)
  }

  async function logout() {
    try {
      await api.post<void>('/api/auth/logout')
    } catch {
      // ignore network errors on logout
    }
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

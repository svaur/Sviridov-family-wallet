import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { adminApi } from './api'

export interface AdminUser {
  id: number
  username: string
  mustChangePassword: boolean
}

interface AdminAuthContextValue {
  admin: AdminUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  setAdmin: (admin: AdminUser) => void
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null)

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdminState] = useState<AdminUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const me = await adminApi.get<AdminUser>('/api/admin/auth/me')
        if (!cancelled) setAdminState(me)
      } catch {
        if (!cancelled) setAdminState(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const onUnauthorized = () => setAdminState(null)
    window.addEventListener('admin:unauthorized', onUnauthorized)
    return () => window.removeEventListener('admin:unauthorized', onUnauthorized)
  }, [])

  const setAdmin = useCallback((a: AdminUser) => setAdminState(a), [])

  async function login(username: string, password: string) {
    const me = await adminApi.post<AdminUser>('/api/admin/auth/login', { username, password })
    setAdminState(me)
  }

  async function logout() {
    try {
      await adminApi.post<void>('/api/admin/auth/logout')
    } catch {
      // ignore network errors on logout
    }
    setAdminState(null)
  }

  return (
    <AdminAuthContext.Provider value={{ admin, loading, login, logout, setAdmin }}>{children}</AdminAuthContext.Provider>
  )
}

export function useAdminAuth(): AdminAuthContextValue {
  const ctx = useContext(AdminAuthContext)
  if (!ctx) throw new Error('useAdminAuth must be used within AdminAuthProvider')
  return ctx
}

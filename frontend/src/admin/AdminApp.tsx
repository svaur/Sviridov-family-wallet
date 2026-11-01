import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AdminAuthProvider, useAdminAuth } from './AdminAuthContext'
import { AdminLayout } from './AdminLayout'
import { AdminAuditPage } from './pages/AdminAuditPage'
import { AdminHomePage } from './pages/AdminHomePage'
import { AdminLoginPage } from './pages/AdminLoginPage'
import { AdminServerPage } from './pages/AdminServerPage'
import { AdminUsersPage } from './pages/AdminUsersPage'

function RequireAdmin({ children }: { children: ReactNode }) {
  const { admin, loading } = useAdminAuth()
  if (loading) return <div className="page">Загрузка…</div>
  if (!admin) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function AdminApp() {
  return (
    <AdminAuthProvider>
      <Routes>
        <Route path="/login" element={<AdminLoginPage />} />
        <Route
          path="/"
          element={
            <RequireAdmin>
              <AdminLayout />
            </RequireAdmin>
          }
        >
          <Route index element={<AdminHomePage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="audit" element={<AdminAuditPage />} />
          <Route path="server" element={<AdminServerPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AdminAuthProvider>
  )
}

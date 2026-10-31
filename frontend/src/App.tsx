import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './components/RequireAuth'
import { DashboardPage } from './pages/DashboardPage'
import { ImportPage } from './pages/ImportPage'
import { LoginPage } from './pages/LoginPage'
import { RulesPage } from './pages/RulesPage'
import { StatementsPage } from './pages/StatementsPage'
import { TransactionsPage } from './pages/TransactionsPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route path="/dashboard" element={<Navigate to="/" replace />} />
        <Route
          path="/statements"
          element={
            <RequireAuth>
              <StatementsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/import"
          element={
            <RequireAuth>
              <ImportPage />
            </RequireAuth>
          }
        />
        <Route
          path="/rules"
          element={
            <RequireAuth>
              <RulesPage />
            </RequireAuth>
          }
        />
        <Route
          path="/transactions"
          element={
            <RequireAuth>
              <TransactionsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

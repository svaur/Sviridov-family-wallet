import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './components/RequireAuth'
import { CardsPage } from './pages/CardsPage'
import { LoginPage } from './pages/LoginPage'
import { StatementsPage } from './pages/StatementsPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <CardsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/cards/:cardId/statements"
          element={
            <RequireAuth>
              <StatementsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

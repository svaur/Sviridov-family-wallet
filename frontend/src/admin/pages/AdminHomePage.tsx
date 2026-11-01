import { useAdminAuth } from '../AdminAuthContext'

export function AdminHomePage() {
  const { admin } = useAdminAuth()

  return (
    <div className="page">
      <div className="page-head">
        <h1>Администрирование</h1>
      </div>
      <div className="card">
        <h2>Обзор</h2>
        <p className="muted">
          Вы вошли как <b>{admin?.username}</b>.
        </p>
      </div>
    </div>
  )
}

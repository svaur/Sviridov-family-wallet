import { NavLink, Outlet } from 'react-router-dom'
import { useAdminAuth } from './AdminAuthContext'

function navClass({ isActive }: { isActive: boolean }) {
  return 'side-item' + (isActive ? ' active' : '')
}

export function AdminLayout() {
  const { admin, logout } = useAdminAuth()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink className="brand" to="/">
          <span className="mark">A</span>
          <b>Админ</b>
        </NavLink>

        <div className="side-group">Управление</div>
        <NavLink className={navClass} to="/" end>
          <span className="ico">▦</span>Обзор
        </NavLink>
        <NavLink className={navClass} to="/users">
          <span className="ico">👥</span>Пользователи
        </NavLink>
        <NavLink className={navClass} to="/audit">
          <span className="ico">📋</span>Аудит
        </NavLink>
        <NavLink className={navClass} to="/server">
          <span className="ico">🖥</span>Сервер
        </NavLink>

        <div className="side-bottom">
          <div className="avatar">{admin?.username[0]?.toUpperCase() ?? '?'}</div>
          <div>
            <div className="nm">{admin?.username}</div>
            <button type="button" className="logout" onClick={() => logout()}>
              Выйти
            </button>
          </div>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}

import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function navClass({ isActive }: { isActive: boolean }) {
  return 'side-item' + (isActive ? ' active' : '')
}

export function Layout() {
  const { user, logout } = useAuth()
  const initial = user?.username?.[0]?.toUpperCase() ?? '?'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink className="brand" to="/">
          <span className="mark">₽</span>
          <b>Кошелёк</b>
        </NavLink>

        <div className="side-group">Обзор</div>
        <NavLink className={navClass} to="/" end>
          <span className="ico">▦</span>Дашборд
        </NavLink>
        <NavLink className={navClass} to="/transactions">
          <span className="ico">⇄</span>Операции
        </NavLink>

        <div className="side-group">Данные</div>
        <NavLink className={navClass} to="/statements">
          <span className="ico">▤</span>Выписки
        </NavLink>
        <NavLink className={navClass} to="/cards">
          <span className="ico">▭</span>Карты
        </NavLink>
        <NavLink className={navClass} to="/rules">
          <span className="ico">◎</span>Правила
        </NavLink>

        <div className="side-bottom">
          <div className="avatar">{initial}</div>
          <div>
            <div className="nm">{user?.username ?? '—'}</div>
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

import { useEffect, useState } from 'react'
import { adminApi, HttpError } from '../api'

interface AuditRow {
  id: number
  at: string
  actorKind: string
  actorId: number | null
  entity: string
  entityId: number
  action: string
  username: string | null
  ip: string | null
}

interface AuditPage {
  content: AuditRow[]
  totalElements: number
  totalPages: number
  number: number
}

const ACTION_LABELS: Record<string, string> = {
  create: 'создание пользователя',
  ban: 'блокировка',
  unban: 'разблокировка',
  reset_password: 'сброс пароля',
  force_logout: 'завершение сеансов',
  delete: 'удаление',
  login: 'вход администратора',
}

function fmtDate(value: string): string {
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function target(r: AuditRow): string {
  if (r.entity === 'admin') {
    return r.username ? `администратор ${r.username}` : `администратор #${r.entityId}`
  }
  return r.username ? `пользователь ${r.username}` : `пользователь #${r.entityId}`
}

export function AdminAuditPage() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<AuditPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load(p: number) {
    setLoading(true)
    setError(null)
    try {
      setData(await adminApi.get<AuditPage>(`/api/admin/audit?page=${p}&size=50`))
      setPage(p)
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось загрузить журнал')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(0)
  }, [])

  const rows = data?.content ?? []
  const total = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 1

  return (
    <div className="page">
      <div className="page-head">
        <h1>Аудит</h1>
        <span className="muted">Действия администраторов</span>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card">
        {loading ? (
          <p className="muted">Загрузка…</p>
        ) : rows.length === 0 ? (
          <p className="muted">Записей нет.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Время</th>
                <th>Действие</th>
                <th>Объект</th>
                <th>IP</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="muted">{fmtDate(r.at)}</td>
                  <td>{ACTION_LABELS[r.action] ?? r.action}</td>
                  <td>{target(r)}</td>
                  <td className="muted">{r.ip ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {!loading && total > 0 && (
          <div className="actions">
            <button type="button" className="btn-sm btn-secondary" disabled={page === 0} onClick={() => load(page - 1)}>
              ← Назад
            </button>
            <span className="muted">
              {total} записей · стр. {page + 1} из {totalPages}
            </span>
            <button
              type="button"
              className="btn-sm btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => load(page + 1)}
            >
              Вперёд →
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

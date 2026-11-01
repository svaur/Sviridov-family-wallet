import { useEffect, useState } from 'react'
import { adminApi, HttpError } from '../api'

interface ServerStatus {
  version: string
  uptimeSeconds: number
  dbStatus: string
  userCount: number
  adminCount: number
  transactionCount: number
  statementCount: number
  cardCount: number
  categoryCount: number
  ruleCount: number
  lastImportAt: string | null
  jvmMaxBytes: number
  jvmUsedBytes: number
  diskTotalBytes: number
  diskUsableBytes: number
}

function fmtBytes(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} ГБ`
  return `${Math.round(bytes / 1024 / 1024)} МБ`
}

function fmtDuration(seconds: number): string {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `${d} д ${h} ч ${m} м`
  if (h > 0) return `${h} ч ${m} м`
  return `${m} м`
}

function fmtDate(value: string | null): string {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function AdminServerPage() {
  const [status, setStatus] = useState<ServerStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setStatus(await adminApi.get<ServerStatus>('/api/admin/status'))
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Не удалось получить статус')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const dbOk = status?.dbStatus === 'ok'

  return (
    <div className="page">
      <div className="page-head">
        <h1>Сервер</h1>
        <button type="button" className="btn-secondary" onClick={() => load()}>
          Обновить
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      {loading && !status ? (
        <div className="card">
          <p className="muted">Загрузка…</p>
        </div>
      ) : status ? (
        <>
          <div className="card">
            <h2>Состояние</h2>
            <div className="kv">
              <div>
                <span className="muted">Версия</span>
                <b>{status.version}</b>
              </div>
              <div>
                <span className="muted">База данных</span>
                <span className={`badge ${dbOk ? 'badge-ok' : 'badge-blocked'}`}>
                  {dbOk ? 'подключена' : 'ошибка'}
                </span>
              </div>
              <div>
                <span className="muted">Время работы</span>
                <b>{fmtDuration(status.uptimeSeconds)}</b>
              </div>
              <div>
                <span className="muted">Последний импорт</span>
                <b>{fmtDate(status.lastImportAt)}</b>
              </div>
              <div>
                <span className="muted">Память JVM</span>
                <b>
                  {fmtBytes(status.jvmUsedBytes)} / {fmtBytes(status.jvmMaxBytes)}
                </b>
              </div>
              <div>
                <span className="muted">Диск свободно</span>
                <b>
                  {fmtBytes(status.diskUsableBytes)} / {fmtBytes(status.diskTotalBytes)}
                </b>
              </div>
            </div>
          </div>

          <div className="card">
            <h2>Данные</h2>
            <div className="stats">
              <div className="stat">
                <div className="lbl">Пользователи</div>
                <div className="val">{status.userCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Администраторы</div>
                <div className="val">{status.adminCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Операции</div>
                <div className="val">{status.transactionCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Выписки</div>
                <div className="val">{status.statementCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Карты</div>
                <div className="val">{status.cardCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Категории</div>
                <div className="val">{status.categoryCount}</div>
              </div>
              <div className="stat">
                <div className="lbl">Правила</div>
                <div className="val">{status.ruleCount}</div>
              </div>
            </div>
          </div>
        </>
      ) : null}
    </div>
  )
}

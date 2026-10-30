import { useEffect, useState } from 'react'

interface AppInfo {
  name: string
  version: string
  time: string
}

export default function HomePage() {
  const [info, setInfo] = useState<AppInfo | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api')
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        return r.json()
      })
      .then(setInfo)
      .catch((e) => setError(String(e)))
  }, [])

  return (
    <section>
      <h1>Главная</h1>
      {error && <p className="error">Бэкенд недоступен: {error}</p>}
      {info && (
        <p>
          Приложение <strong>{info.name}</strong> v{info.version} запущено и отвечает.
        </p>
      )}
    </section>
  )
}

export function fmtDate(d: string | null | undefined): string {
  if (!d) return '—'
  const [y, m, dd] = d.split('-')
  return dd && m && y ? `${dd}.${m}.${y}` : d
}

export function fmtInstant(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
}

export function fmtMoney(amount: string | number): string {
  const n = typeof amount === 'number' ? amount : Number(amount)
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0)
}

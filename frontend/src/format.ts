const eur = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const signedEur = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
})

const signedPercent = new Intl.NumberFormat('en-GB', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
  signDisplay: 'exceptZero',
})

const dateFmt = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
})

const calendarDateFmt = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

export const EMPTY = '—'

export function formatCost(value: number | null | undefined): string {
  if (value === null || value === undefined) return EMPTY
  return eur.format(value)
}

// An offer of 0 has no percentage.
export function formatPriceDifference(offer: number, real: number): string {
  const difference = signedEur.format(real - offer)
  if (offer === 0) return difference
  return `${difference} · ${signedPercent.format((real - offer) / offer)}`
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY
  const parts = value.slice(0, 10).split('-')
  if (parts.length !== 3) return value
  const [y, m, d] = parts.map(Number)
  if (!y || !m || !d) return value
  return calendarDateFmt.format(new Date(Date.UTC(y, m - 1, d)))
}

function days(count: number): string {
  return count === 1 ? '1 day' : `${count} days`
}

export function formatDaysLate(daysLate: number): string {
  return `${days(daysLate)} late`
}

function calendarDay(value: string): number {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return Date.UTC(y, m - 1, d) / 86_400_000
}

// The real arrival minus the expected one, in calendar days.
export function formatArrivalDifference(expected: string, real: string): string {
  const difference = calendarDay(real) - calendarDay(expected)
  if (difference === 0) return 'on time'
  return difference > 0 ? formatDaysLate(difference) : `${days(-difference)} early`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return EMPTY
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return `${dateFmt.format(parsed)} ${String(parsed.getHours()).padStart(2, '0')}:${String(
    parsed.getMinutes(),
  ).padStart(2, '0')}`
}

export function formatNumber(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined) return EMPTY
  return value.toLocaleString('en-GB', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals,
  })
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return EMPTY
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(0)} kB`
  return `${(kb / 1024).toFixed(1)} MB`
}

export function formatPlainNumber(value: number): string {
  return String(Number(value.toFixed(2)))
}

export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

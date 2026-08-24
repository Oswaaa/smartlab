function toValidDate(value) {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value, width = 2) {
  return String(value).padStart(width, '0')
}

function formatClock(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`
}

export function formatLogClock(value = new Date()) {
  const date = toValidDate(value)
  return date ? formatClock(date) : '-'
}

export function formatLogDateTime(value) {
  if (value == null || value === '') return '-'
  const date = toValidDate(value)
  if (!date) return String(value)
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()} ${formatClock(date)}`
}

/**
 * 相对时间格式化 —— 会话列表、消息气泡的时间标签。
 *
 * <p>后端 {@code LocalDateTime} 经 Jackson 序列化为 {@code yyyy-MM-dd'T'HH:mm:ss}
 * （无时区后缀）。JS 对无时区 ISO 串按「本地时间」解析，与后端服务器本地时间一致，
 * 因此直接 {@code new Date(str)} 即可，无需时区换算。</p>
 */

/** 解析后端时间串 / Date，非法输入返回 Invalid Date（由调用方判定 NaN） */
export function parseLocalTime(value: string | Date): Date {
  return value instanceof Date ? value : new Date(value)
}

/**
 * 相对时间标签：
 *  <1min → 刚刚；<1h → N 分钟前；
 *  今天 → HH:mm；昨天 → 昨天；
 *  今年 → M 月 D 日；更早 → YYYY-MM-DD
 */
export function formatRelativeTime(value: string | Date, now: Date = new Date()): string {
  const t = parseLocalTime(value)
  if (Number.isNaN(t.getTime())) return ''

  const diffMin = Math.floor((now.getTime() - t.getTime()) / 60_000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`

  if (isSameDay(t, now)) return `${pad(t.getHours())}:${pad(t.getMinutes())}`

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (isSameDay(t, yesterday)) return '昨天'

  if (t.getFullYear() === now.getFullYear()) {
    return `${t.getMonth() + 1} 月 ${t.getDate()} 日`
  }
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`
}

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

import { addDays, formatDayMonth, parseDateString, startOfWeek, toDateString, weekdayShort } from '../../shared/lib/dates'

/** Semana = a mesma da agenda (segunda a domingo), barras por dia. Mês = dia 1 ao último, barras por semana. */
export type View = 'week' | 'month'
export type GroupBy = 'DAY' | 'WEEK'

export interface Period {
  view: View
  from: Date
  to: Date
  groupBy: GroupBy
}

const MONTHS = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
]

export function periodFor(view: View, anchor: Date): Period {
  if (view === 'week') {
    const from = startOfWeek(anchor)
    return { view, from, to: addDays(from, 6), groupBy: 'DAY' }
  }
  const from = new Date(anchor.getFullYear(), anchor.getMonth(), 1)
  const to = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0)
  return { view, from, to, groupBy: 'WEEK' }
}

/** Período anterior (-1) ou seguinte (+1). */
export function shiftPeriod(period: Period, delta: number): Period {
  if (period.view === 'week') return periodFor('week', addDays(period.from, 7 * delta))
  return periodFor('month', new Date(period.from.getFullYear(), period.from.getMonth() + delta, 1))
}

/** Lê "?view=month&date=2026-11-01" da URL; sem parâmetro (ou inválido): semana de hoje. */
export function periodFromParams(params: URLSearchParams, today: Date): Period {
  const view: View = params.get('view') === 'month' ? 'month' : 'week'
  const date = parseDateString(params.get('date') ?? '') ?? today
  return periodFor(view, date)
}

export function periodToParams(period: Period): Record<string, string> {
  return { view: period.view, date: toDateString(period.from) }
}

/** "16/11 a 22/11 de 2026" ou "novembro de 2026". */
export function periodLabel(period: Period): string {
  if (period.view === 'month') return `${MONTHS[period.from.getMonth()]} de ${period.from.getFullYear()}`
  return `${formatDayMonth(period.from)} a ${formatDayMonth(period.to)} de ${period.to.getFullYear()}`
}

/** Rótulo de uma barra: "Seg 16" (por dia) ou "02 a 08" (por semana; um dia só: "01"). */
export function barLabel(start: string, end: string, groupBy: GroupBy): string {
  const s = parseDateString(start)
  const e = parseDateString(end)
  if (!s || !e) return start
  const day = (d: Date) => String(d.getDate()).padStart(2, '0')
  if (groupBy === 'DAY') {
    const weekday = weekdayShort(s)
    return `${weekday.charAt(0).toUpperCase()}${weekday.slice(1)} ${day(s)}`
  }
  return start === end ? day(s) : `${day(s)} a ${day(e)}`
}

/** Altura de cada parte da barra, em % da maior barra do gráfico. Sem valores: tudo zero. */
export function barHeights<T extends { received: number; receivable: number; expected: number }>(
  bars: T[],
): { received: number; receivable: number; expected: number }[] {
  const max = Math.max(0, ...bars.map((b) => b.received + b.receivable + b.expected))
  return bars.map((b) =>
    max === 0
      ? { received: 0, receivable: 0, expected: 0 }
      : {
          received: (b.received / max) * 100,
          receivable: (b.receivable / max) * 100,
          expected: (b.expected / max) * 100,
        },
  )
}

import { describe, expect, it } from 'vitest'
import { toDateString } from '../../shared/lib/dates'
import { barHeights, barLabel, periodFor, periodFromParams, periodLabel, periodToParams, shiftPeriod } from './periods'

const day = (dateText: string) => {
  const [year, month, dayOfMonth] = dateText.split('-').map(Number)
  return new Date(year, month - 1, dayOfMonth)
}
const range = (period: { from: Date; to: Date }) => [toDateString(period.from), toDateString(period.to)]

describe('periodFor', () => {
  it('semana: segunda a domingo, barras por dia', () => {
    const period = periodFor('week', day('2026-11-19')) // quinta
    expect(range(period)).toEqual(['2026-11-16', '2026-11-22'])
    expect(period.groupBy).toBe('DAY')
  })

  it('mês: dia 1 ao último dia, barras por semana', () => {
    const period = periodFor('month', day('2026-11-19'))
    expect(range(period)).toEqual(['2026-11-01', '2026-11-30'])
    expect(period.groupBy).toBe('WEEK')
    expect(range(periodFor('month', day('2028-02-10')))).toEqual(['2028-02-01', '2028-02-29'])
  })
})

describe('shiftPeriod', () => {
  it('anda de semana em semana', () => {
    const period = periodFor('week', day('2026-11-19'))
    expect(range(shiftPeriod(period, 1))).toEqual(['2026-11-23', '2026-11-29'])
    expect(range(shiftPeriod(period, -1))).toEqual(['2026-11-09', '2026-11-15'])
  })

  it('anda de mês em mês, inclusive na virada do ano', () => {
    const dec = periodFor('month', day('2026-12-05'))
    expect(range(shiftPeriod(dec, 1))).toEqual(['2027-01-01', '2027-01-31'])
    expect(range(shiftPeriod(dec, -1))).toEqual(['2026-11-01', '2026-11-30'])
  })
})

describe('URL', () => {
  it('sem parâmetros: semana de hoje', () => {
    const period = periodFromParams(new URLSearchParams(''), day('2026-11-19'))
    expect(period.view).toBe('week')
    expect(range(period)).toEqual(['2026-11-16', '2026-11-22'])
  })

  it('ida e volta pela URL', () => {
    const period = periodFor('month', day('2026-11-19'))
    const back = periodFromParams(new URLSearchParams(periodToParams(period)), day('2030-01-01'))
    expect(back.view).toBe('month')
    expect(range(back)).toEqual(['2026-11-01', '2026-11-30'])
  })

  it('data inválida cai em hoje', () => {
    const period = periodFromParams(new URLSearchParams('view=month&date=2026-02-31'), day('2026-11-19'))
    expect(range(period)).toEqual(['2026-11-01', '2026-11-30'])
  })
})

describe('rótulos', () => {
  it('do período', () => {
    expect(periodLabel(periodFor('week', day('2026-11-19')))).toBe('16/11 a 22/11 de 2026')
    expect(periodLabel(periodFor('month', day('2026-03-02')))).toBe('março de 2026')
  })

  it('das barras', () => {
    expect(barLabel('2026-11-16', '2026-11-16', 'DAY')).toBe('Seg 16')
    expect(barLabel('2026-11-02', '2026-11-08', 'WEEK')).toBe('02 a 08')
    expect(barLabel('2026-11-01', '2026-11-01', 'WEEK')).toBe('01')
  })
})

describe('barHeights', () => {
  it('proporcional à maior barra', () => {
    const heights = barHeights([
      { received: 100, receivable: 50, expected: 50 },
      { received: 0, receivable: 0, expected: 100 },
    ])
    expect(heights[0]).toEqual({ received: 50, receivable: 25, expected: 25 })
    expect(heights[1]).toEqual({ received: 0, receivable: 0, expected: 50 })
  })

  it('sem valores: tudo zero (sem dividir por zero)', () => {
    expect(barHeights([{ received: 0, receivable: 0, expected: 0 }])).toEqual([
      { received: 0, receivable: 0, expected: 0 },
    ])
  })
})

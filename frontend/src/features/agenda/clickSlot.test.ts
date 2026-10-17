import { describe, expect, it } from 'vitest'
import { formatMinutes, minutesFromOffset } from './clickSlot'

describe('minutesFromOffset', () => {
  it('começa na hora do topo da grade', () => {
    expect(minutesFromOffset(0, 64, 8)).toBe(8 * 60)
  })

  it('arredonda para baixo de 15 em 15 minutos', () => {
    // 64px = 1h. 40px = 37,5 min -> 30 min.
    expect(minutesFromOffset(40, 64, 8)).toBe(8 * 60 + 30)
    // 15 min = 16px; 15px ainda é 14 min -> 0.
    expect(minutesFromOffset(15, 64, 8)).toBe(8 * 60)
    expect(minutesFromOffset(16, 64, 8)).toBe(8 * 60 + 15)
  })

  it('uma hora abaixo soma uma hora', () => {
    expect(minutesFromOffset(64, 64, 8)).toBe(9 * 60)
  })

  it('nunca passa de 23:45 nem fica negativo', () => {
    expect(minutesFromOffset(100000, 64, 8)).toBe(23 * 60 + 45)
    expect(minutesFromOffset(-50, 64, 0)).toBe(0)
  })
})

describe('formatMinutes', () => {
  it('formata como HH:mm', () => {
    expect(formatMinutes(0)).toBe('00:00')
    expect(formatMinutes(9 * 60 + 5)).toBe('09:05')
    expect(formatMinutes(23 * 60 + 45)).toBe('23:45')
  })
})

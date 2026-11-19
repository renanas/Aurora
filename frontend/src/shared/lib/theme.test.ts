import { describe, expect, it } from 'vitest'
import { isDarkTheme, parseThemeChoice } from './theme'

describe('parseThemeChoice', () => {
  it('lê as escolhas válidas', () => {
    expect(parseThemeChoice('dark')).toBe('dark')
    expect(parseThemeChoice('system')).toBe('system')
    expect(parseThemeChoice('light')).toBe('light')
  })

  it('sem escolha guardada (ou valor estranho): claro', () => {
    expect(parseThemeChoice(null)).toBe('light')
    expect(parseThemeChoice('roxo')).toBe('light')
  })
})

describe('isDarkTheme', () => {
  it('claro e escuro ignoram o aparelho', () => {
    expect(isDarkTheme('light', true)).toBe(false)
    expect(isDarkTheme('dark', false)).toBe(true)
  })

  it('automático segue o aparelho', () => {
    expect(isDarkTheme('system', true)).toBe(true)
    expect(isDarkTheme('system', false)).toBe(false)
  })
})

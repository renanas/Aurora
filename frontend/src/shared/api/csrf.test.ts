import { describe, expect, it } from 'vitest'
import { csrfTokenFrom } from './csrf'

describe('csrfTokenFrom', () => {
  it('devolve undefined quando não há cookies', () => {
    expect(csrfTokenFrom('')).toBeUndefined()
  })

  it('acha o token entre outros cookies', () => {
    expect(csrfTokenFrom('a=1; XSRF-TOKEN=abc-123; b=2')).toBe('abc-123')
  })

  it('não confunde com cookies de nome parecido', () => {
    expect(csrfTokenFrom('NOT-XSRF-TOKEN=errado; XSRF-TOKEN-2=errado')).toBeUndefined()
  })

  it('decodifica o valor', () => {
    expect(csrfTokenFrom('XSRF-TOKEN=a%20b')).toBe('a b')
  })

  it('devolve o valor cru se a decodificação falhar', () => {
    expect(csrfTokenFrom('XSRF-TOKEN=100%')).toBe('100%')
  })
})

import { describe, expect, it } from 'vitest'
import { PASSWORD_ALPHABET, generatePassword, secureRandomInt } from './generatePassword'
import { checkPasswordStrength } from '../auth/passwordRules'

describe('generatePassword', () => {
  it('gera 16 caracteres por padrão, todos do alfabeto', () => {
    const password = generatePassword()

    expect(password).toHaveLength(16)
    expect([...password].every((character) => PASSWORD_ALPHABET.includes(character))).toBe(true)
  })

  it('respeita o tamanho pedido', () => {
    expect(generatePassword(secureRandomInt, 24)).toHaveLength(24)
  })

  it('usa o sorteador recebido, na ordem', () => {
    let next = 0
    const password = generatePassword(() => next++ % PASSWORD_ALPHABET.length, 5)

    expect(password).toBe(PASSWORD_ALPHABET.slice(0, 5))
  })

  it('o alfabeto não tem caracteres ambíguos', () => {
    for (const ambiguous of ['0', 'O', '1', 'l', 'I']) {
      expect(PASSWORD_ALPHABET.includes(ambiguous)).toBe(false)
    }
  })

  it('a senha gerada passa nas regras de senha do sistema', () => {
    for (let i = 0; i < 50; i += 1) {
      expect(checkPasswordStrength(generatePassword())).toBeNull()
    }
  })
})

describe('secureRandomInt', () => {
  it('sempre devolve um inteiro dentro de [0, max)', () => {
    for (let i = 0; i < 2000; i += 1) {
      const value = secureRandomInt(57)
      expect(Number.isInteger(value)).toBe(true)
      expect(value).toBeGreaterThanOrEqual(0)
      expect(value).toBeLessThan(57)
    }
  })

  it('com max 1 devolve sempre 0', () => {
    expect(secureRandomInt(1)).toBe(0)
  })
})

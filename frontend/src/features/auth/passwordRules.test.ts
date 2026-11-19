import { describe, expect, it } from 'vitest'
import { checkNewPassword, checkPasswordStrength } from './passwordRules'

describe('checkPasswordStrength', () => {
  it('exige pelo menos 12 caracteres', () => {
    expect(checkPasswordStrength('a'.repeat(11))).toMatch(/pelo menos 12/)
    expect(checkPasswordStrength('a'.repeat(12))).toBeNull()
  })

  it('aceita no máximo 72 bytes', () => {
    expect(checkPasswordStrength('a'.repeat(72))).toBeNull()
    expect(checkPasswordStrength('a'.repeat(73))).toMatch(/longa demais/)
  })

  it('conta bytes, não letras: acentos ocupam mais de um', () => {
    expect(checkPasswordStrength('é'.repeat(36))).toBeNull() // 72 bytes
    expect(checkPasswordStrength('é'.repeat(37))).toMatch(/longa demais/) // 74 bytes
  })

  it('emoji conta como um caractere no mínimo e quatro bytes no máximo', () => {
    expect(checkPasswordStrength('😀'.repeat(12))).toBeNull() // 12 caracteres, 48 bytes
    expect(checkPasswordStrength('😀'.repeat(19))).toMatch(/longa demais/) // 76 bytes
  })

  it('espaços fazem parte da senha', () => {
    expect(checkPasswordStrength(' '.repeat(12))).toBeNull()
  })
})

describe('checkNewPassword', () => {
  const current = 'provisoria-1234'

  it('aceita uma senha nova, forte e confirmada', () => {
    expect(checkNewPassword(current, 'senha-nova-5678', 'senha-nova-5678')).toBeNull()
  })

  it('recusa senha nova curta antes de qualquer outra coisa', () => {
    expect(checkNewPassword(current, 'curta', 'outra')).toMatch(/pelo menos 12/)
  })

  it('recusa repetir a senha atual', () => {
    expect(checkNewPassword(current, current, current)).toMatch(/diferente da atual/)
  })

  it('recusa confirmação diferente', () => {
    expect(checkNewPassword(current, 'senha-nova-5678', 'senha-nova-5679')).toMatch(/confirmação/)
  })
})

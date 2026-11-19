export const MIN_PASSWORD_LENGTH = 12
export const MAX_PASSWORD_BYTES = 72

/**
 * Mesmas regras do servidor (PasswordPolicy): 12 ou mais caracteres e no máximo 72 bytes em UTF-8
 * (limite do BCrypt; acentos e emojis ocupam mais de um byte). O servidor sempre confere de novo.
 * Devolve a mensagem do problema, ou null se a senha serve.
 */
export function checkPasswordStrength(password: string): string | null {
  if ([...password].length < MIN_PASSWORD_LENGTH) {
    return `A senha deve ter pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`
  }
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return 'A senha é longa demais (acentos e emojis contam mais de um caractere).'
  }
  return null
}

/** Troca da própria senha: força, diferente da atual e confirmação igual. */
export function checkNewPassword(current: string, next: string, confirmation: string): string | null {
  const strength = checkPasswordStrength(next)
  if (strength) return strength
  if (next === current) return 'A nova senha deve ser diferente da atual.'
  if (next !== confirmation) return 'A confirmação não é igual à nova senha.'
  return null
}

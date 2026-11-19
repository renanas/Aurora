/** Sem os caracteres que se confundem ao ler ou ditar (0/O, 1/l/I). */
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'

/** Inteiro sorteado em [0, max) com o gerador seguro do navegador, sem o viés do "resto da divisão". */
export function secureRandomInt(max: number): number {
  const limit = Math.floor(0x100000000 / max) * max
  const buffer = new Uint32Array(1)
  let value: number
  do {
    crypto.getRandomValues(buffer)
    value = buffer[0] as number
  } while (value >= limit)
  return value % max
}

/** Senha provisória aleatória (16 caracteres por padrão, bem acima do mínimo de 12). */
export function generatePassword(randomInt: (max: number) => number = secureRandomInt, length = 16): string {
  let password = ''
  for (let i = 0; i < length; i += 1) {
    password += ALPHABET.charAt(randomInt(ALPHABET.length))
  }
  return password
}

export const PASSWORD_ALPHABET = ALPHABET

export function formatMoney(value: number): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

/** 5511999998888 -> +55 (11) 99999-8888. Formatos desconhecidos são devolvidos como vieram. */
export function formatPhone(digits: string): string {
  const d = digits.replace(/\D/g, '')

  const withCountry = d.startsWith('55') && (d.length === 12 || d.length === 13)
  const local = withCountry ? d.slice(2) : d
  if (local.length !== 10 && local.length !== 11) {
    return digits
  }

  const ddd = local.slice(0, 2)
  const number = local.slice(2)
  const split = number.length === 9 ? 5 : 4
  const formatted = `(${ddd}) ${number.slice(0, split)}-${number.slice(split)}`
  return withCountry ? `+55 ${formatted}` : formatted
}

export function countDigits(text: string): number {
  return text.replace(/\D/g, '').length
}

/** "180", "180,5" e "180.50" são aceitos; qualquer outra coisa é inválida (null). */
export function parsePrice(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d+([.,]\d{1,2})?$/.test(trimmed)) {
    return null
  }
  return Number(trimmed.replace(',', '.'))
}

export function priceToInput(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

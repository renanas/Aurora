/** Lê o token CSRF (cookie XSRF-TOKEN) de uma string no formato de document.cookie. */
export function csrfTokenFrom(cookieHeader: string): string | undefined {
  for (const part of cookieHeader.split(';')) {
    const [name, ...rest] = part.trim().split('=')
    if (name !== 'XSRF-TOKEN') continue
    const value = rest.join('=')
    if (!value) return undefined
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }
  return undefined
}

/**
 * Garante que o cookie XSRF-TOKEN exista antes de uma chamada que altera algo.
 * O servidor apaga o cookie no logout (e ele some se o servidor reinicia sem sessão); sem ele o
 * login levaria 403. Se não houver token, faz `prime` (um GET qualquer, que recria o cookie
 * mesmo respondendo 401) e ignora o resultado: o que importa é só o cookie.
 */
export async function ensureCsrfToken(
  readCookie: () => string,
  prime: () => Promise<unknown>,
): Promise<void> {
  if (csrfTokenFrom(readCookie())) return
  try {
    await prime()
  } catch {
    // 401 (esperado) ou falha de rede: o login em seguida mostra o erro verdadeiro.
  }
}

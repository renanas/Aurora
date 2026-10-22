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

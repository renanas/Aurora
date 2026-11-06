import type { QueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../../shared/api/client'
import { ensureCsrfToken } from '../../shared/api/csrf'

export interface Me {
  /** E-mail da conta (o login). */
  username: string
  /** Nome para exibir no cabeçalho. */
  displayName: string
  role: 'ADMIN' | 'PSYCHOLOGIST'
}

/** Chave do "quem está logado" no cache: null = sem sessão. */
export const AUTH_KEY = ['auth', 'me'] as const

/** Quem está logado, ou null se não há sessão válida (401 não é erro aqui). */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export async function login(username: string, password: string): Promise<void> {
  // Depois do "Sair" o cookie CSRF não existe mais; sem ele o servidor responderia 403.
  await ensureCsrfToken(
    () => document.cookie,
    () => api<Me>('/auth/me'),
  )
  return api<void>('/auth/login', {
    method: 'POST',
    body: new URLSearchParams({ username, password }),
  })
}

export function logout(): Promise<void> {
  return api<void>('/auth/logout', { method: 'POST' })
}

/** Fim da sessão (logout ou 401): apaga do navegador tudo o que veio do servidor e volta ao login. */
export function dropSession(queryClient: QueryClient) {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== AUTH_KEY[0] })
  queryClient.setQueryData<Me | null>(AUTH_KEY, null)
}

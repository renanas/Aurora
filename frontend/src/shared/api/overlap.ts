import { ApiError, type Conflict } from './client'

export function isOverlapError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 409 && error.code === 'OVERLAP_DETECTED'
}

/**
 * Conflito de horário é aviso, não bloqueio (como a folha de papel).
 * Envia a requisição; se o servidor avisar do conflito (409), pergunta à psicóloga e,
 * se ela confirmar, reenvia com confirmOverlap = true. Se ela desistir, devolve undefined.
 * Qualquer outro erro segue para quem chamou.
 */
export async function withOverlapConfirmation<T>(
  send: (confirmOverlap: boolean) => Promise<T>,
  ask: (conflicts: Conflict[]) => Promise<boolean> | boolean,
): Promise<T | undefined> {
  try {
    return await send(false)
  } catch (error) {
    if (!isOverlapError(error)) {
      throw error
    }
    if (!(await ask(error.conflicts))) {
      return undefined
    }
    return send(true)
  }
}

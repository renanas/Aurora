import { csrfTokenFrom } from './csrf'

export interface FieldError {
  field: string
  message: string
}

/** Uma sessão que se sobrepõe ao horário pedido (corpo do 409 OVERLAP_DETECTED). */
export interface Conflict {
  sessionId: string
  patientName: string
  startsAt: string
  endsAt: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly conflicts: Conflict[]

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: FieldError[] = [],
    conflicts: Conflict[] = [],
  ) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.conflicts = conflicts
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** Objeto (vai como JSON) ou URLSearchParams (vai como formulário, usado só no login). */
  body?: unknown
}

/** Cliente HTTP único. Erros viram ApiError com o "code" estável do servidor. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const hasBody = options.body !== undefined
  const isForm = options.body instanceof URLSearchParams

  const headers: Record<string, string> = {}
  if (hasBody && !isForm) {
    headers['Content-Type'] = 'application/json'
  }
  // Toda chamada que altera algo devolve o token CSRF que o servidor entregou no cookie.
  if (method !== 'GET' && typeof document !== 'undefined') {
    const token = csrfTokenFrom(document.cookie)
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let body: BodyInit | undefined
  if (hasBody) {
    body = isForm ? (options.body as URLSearchParams) : JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body,
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Sem conexão com o servidor.')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!response.ok) {
    const problem = (data ?? {}) as {
      code?: string
      detail?: string
      errors?: FieldError[]
      conflicts?: Conflict[]
    }
    throw new ApiError(
      response.status,
      problem.code ?? 'UNKNOWN_ERROR',
      problem.detail ?? 'Erro inesperado.',
      problem.errors ?? [],
      problem.conflicts ?? [],
    )
  }

  return data as T
}

import { ApiError } from './client'

/** Texto mostrado para cada "code" do servidor. O "detail" do servidor é só reserva. */
const MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Confira os campos informados.',
  NOT_FOUND: 'Registro não encontrado.',
  CONCURRENT_MODIFICATION: 'Este registro foi alterado em outro lugar. Atualize a tela e tente de novo.',
  INTERNAL_ERROR: 'Ocorreu um erro inesperado. Tente novamente.',
  NETWORK_ERROR: 'Não foi possível falar com o servidor. Ele está ligado?',
  INVALID_WHATSAPP: 'O WhatsApp deve ter de 10 a 15 dígitos.',
  INVALID_PATIENT_NAME: 'O nome deve ter de 1 a 150 caracteres.',
  INVALID_SESSION_PRICE: 'O valor da sessão não pode ser negativo.',
  PATIENT_INACTIVE: 'Este paciente está inativo. Reative-o antes de definir o horário.',
  NO_ACTIVE_RECURRENCE: 'Este paciente não tem horário recorrente ativo.',
  RECURRENCE_ANCHOR_NOT_AFTER_CURRENT:
    'A nova data de início precisa ser depois da data de início do horário atual.',
  INVALID_DATE_RANGE: 'Período inválido. Escolha no máximo 100 dias.',
}

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.code] ?? error.message
  }
  return 'Ocorreu um erro inesperado.'
}

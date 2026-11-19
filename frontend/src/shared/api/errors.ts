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
  INVALID_STATUS_TRANSITION: 'Esta ação não é possível na situação atual da sessão. Atualize a tela.',
  SESSION_NOT_BILLABLE: 'Esta sessão não é cobrada, então não recebe pagamento.',
  OVERLAP_DETECTED: 'Este horário se sobrepõe a outra sessão.',
  SESSION_NOT_SCHEDULED: 'Só dá para corrigir o horário de uma sessão agendada. Para as outras, reabra ou reagende.',
  RESCHEDULE_SAME_TIME: 'Escolha um horário diferente do atual.',
  UNDO_RESCHEDULE_NOT_ALLOWED: 'Só dá para desfazer o reagendamento enquanto a nova sessão ainda está agendada.',
  REOPEN_NOT_ALLOWED: 'Só é possível reabrir uma sessão realizada, cancelada ou com falta.',
  INVALID_DATE_TIME: 'Essa data não existe no calendário.',
  PASSWORD_CHANGE_REQUIRED: 'Troque a senha provisória para continuar.',
  PASSWORD_TOO_SHORT: 'A senha deve ter pelo menos 12 caracteres.',
  PASSWORD_TOO_LONG: 'A senha é longa demais (acentos e emojis contam mais de um caractere).',
  WRONG_CURRENT_PASSWORD: 'A senha atual está incorreta.',
  SAME_PASSWORD: 'A nova senha deve ser diferente da atual.',
  TOO_MANY_PENDING_REGISTRATIONS: 'Há muitos cadastros aguardando aprovação. Tente de novo mais tarde.',
  ACCOUNT_NOT_PENDING: 'Esta conta já foi aprovada. Para tirar o acesso, desative-a.',
  INVALID_ACCOUNT_DATA: 'Confira o nome e o e-mail informados.',
  CANNOT_MODIFY_OWN_ACCOUNT: 'Você não pode desativar nem redefinir a senha da sua própria conta por aqui.',
  NOT_ALLOWED: 'Você não tem permissão para fazer isso.',
}

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.code] ?? error.message
  }
  return 'Ocorreu um erro inesperado.'
}

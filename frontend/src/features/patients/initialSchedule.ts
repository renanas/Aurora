import { parseDateString } from '../../shared/lib/dates'
import type { Frequency, RecurrenceInput } from './recurrence'

/** Campos opcionais de horário no cadastro do paciente. */
export interface ScheduleFields {
  anchorDate: string
  startTime: string
  frequency: Frequency
}

export type ScheduleChoice =
  | { kind: 'none' }
  | { kind: 'invalid'; field: 'anchorDate' | 'startTime'; message: string }
  | { kind: 'ok'; input: RecurrenceInput }

/**
 * O horário é opcional: com data e horário vazios, o paciente é criado sem horário.
 * Se só um dos dois foi preenchido, avisa qual falta (em vez de ignorar o que foi digitado).
 */
export function readInitialSchedule(fields: ScheduleFields): ScheduleChoice {
  const anchorDate = fields.anchorDate.trim()
  const startTime = fields.startTime.trim()
  if (!anchorDate && !startTime) return { kind: 'none' }
  if (!anchorDate) return { kind: 'invalid', field: 'anchorDate', message: 'Informe a data da primeira sessão.' }
  if (parseDateString(anchorDate) === null) return { kind: 'invalid', field: 'anchorDate', message: 'Data inválida.' }
  if (!startTime) return { kind: 'invalid', field: 'startTime', message: 'Informe o horário.' }
  return { kind: 'ok', input: { anchorDate, startTime, frequency: fields.frequency } }
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Conflict } from '../../shared/api/client'
import { withOverlapConfirmation } from '../../shared/api/overlap'
import { timeOf } from '../../shared/lib/dates'

export type SessionStatus = 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW' | 'RESCHEDULED'

/** Datas como o servidor envia: "2026-09-28T09:00" (sem segundos, sem fuso). */
export interface AgendaSession {
  id: string
  patientId: string
  patientName: string
  startsAt: string
  endsAt: string
  durationMinutes: number
  status: SessionStatus
  cancellationType: 'EARLY' | 'LATE' | null
  paymentStatus: 'PENDING' | 'PAID'
  /** Quando foi marcada como paga: "2026-09-28T14:30" (hora de Brasília), ou null. */
  paidAt: string | null
  amount: number
  billable: boolean
  recurrenceId: string | null
  rescheduledFromId: string | null
  overlapsWith: string[]
}

export const SESSIONS_KEY = ['sessions'] as const

/** Sessões dos dias de `from` a `to` (inclusive), no formato "2026-09-28". */
export function useSessions(from: string, to: string) {
  return useQuery({
    queryKey: [...SESSIONS_KEY, from, to],
    queryFn: () => api<AgendaSession[]>(`/sessions?${new URLSearchParams({ from, to })}`),
  })
}

export type CancellationType = 'EARLY' | 'LATE'

/** Ações da psicóloga sobre uma sessão. Todas podem ser repetidas sem efeito colateral. */
export type SessionAction =
  | { kind: 'confirm' | 'complete' | 'no-show' }
  | { kind: 'cancel'; cancellationType: CancellationType }
  | { kind: 'payment'; status: 'PAID' | 'PENDING' }
  | { kind: 'reopen' }
  /** O id é o da sessão NOVA (a que nasceu do reagendamento). */
  | { kind: 'undo-reschedule' }
  /** Corrige o horário de uma sessão agendada. startsAt: "2026-09-28T09:00". */
  | { kind: 'time'; startsAt: string }
  | { kind: 'reschedule'; startsAt: string }

function sendAction(id: string, action: SessionAction, confirmOverlap: boolean): Promise<AgendaSession> {
  switch (action.kind) {
    case 'cancel':
      return api<AgendaSession>(`/sessions/${id}/cancel`, {
        method: 'POST',
        body: { cancellationType: action.cancellationType },
      })
    case 'payment':
      return api<AgendaSession>(`/sessions/${id}/payment`, { method: 'PUT', body: { status: action.status } })
    case 'reopen':
    case 'undo-reschedule':
      return api<AgendaSession>(`/sessions/${id}/${action.kind}`, { method: 'POST', body: { confirmOverlap } })
    case 'time':
      return api<AgendaSession>(`/sessions/${id}/time`, {
        method: 'PATCH',
        body: { startsAt: action.startsAt, confirmOverlap },
      })
    case 'reschedule':
      return api<AgendaSession>(`/sessions/${id}/reschedule`, {
        method: 'POST',
        body: { startsAt: action.startsAt, confirmOverlap },
      })
    default:
      return api<AgendaSession>(`/sessions/${id}/${action.kind}`, { method: 'POST' })
  }
}

/** Texto do aviso de conflito: quem já está naquele horário. Conflito é aviso, não bloqueio. */
function askAboutOverlap(conflicts: Conflict[]): boolean {
  const lines = conflicts.map((c) => `• ${c.patientName}, das ${timeOf(c.startsAt)} às ${timeOf(c.endsAt)}`)
  return window.confirm(`Este horário se sobrepõe a outra sessão:\n${lines.join('\n')}\n\nMarcar mesmo assim?`)
}

/**
 * Aplica uma ação e recarrega a agenda (também se falhar: a sessão pode ter mudado em outro lugar).
 * Se o horário bater com outra sessão, pergunta antes de insistir. Resolve com a sessão
 * resultante, ou com undefined se a psicóloga desistiu no aviso de conflito.
 */
export function useSessionAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: SessionAction }) =>
      withOverlapConfirmation((confirmOverlap) => sendAction(id, action, confirmOverlap), askAboutOverlap),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
  })
}

/** Sessão avulsa. Mesmo aviso de conflito das outras ações. */
export function useCreateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ patientId, startsAt }: { patientId: string; startsAt: string }) =>
      withOverlapConfirmation(
        (confirmOverlap) =>
          api<AgendaSession>('/sessions', { method: 'POST', body: { patientId, startsAt, confirmOverlap } }),
        askAboutOverlap,
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
  })
}

/** Pacientes ativos para escolher na sessão avulsa. */
export interface PatientOption {
  id: string
  name: string
}

export function useActivePatients() {
  return useQuery({
    queryKey: ['patients', 'options'],
    queryFn: async () => {
      const all = await api<Array<PatientOption & { active?: boolean }>>('/patients')
      return all
        .filter((p) => p.active !== false)
        .map((p) => ({ id: p.id, name: p.name }))
        .sort((x, y) => x.name.localeCompare(y.name, 'pt-BR'))
    },
  })
}

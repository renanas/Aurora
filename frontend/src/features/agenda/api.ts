import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api/client'

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

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api/client'
import type { SessionStatus } from '../agenda/api'
import type { GroupBy } from './periods'

export interface EarningsBar {
  start: string
  end: string
  received: number
  receivable: number
  expected: number
}

export interface ReceivableSession {
  sessionId: string
  patientId: string
  patientName: string
  /** "2026-11-16T09:00" */
  startsAt: string
  status: SessionStatus
  amount: number
}

export interface Earnings {
  from: string
  to: string
  groupBy: GroupBy
  totals: { received: number; receivable: number; expected: number; total: number }
  /** Pagas em sessões que não são cobradas (ex.: canceladas com antecedência): fora dos totais. */
  paidNotBillable: { amount: number; count: number }
  /** Sessões que já terminaram e continuam agendadas/confirmadas (contam no previsto). */
  sessionsWithoutOutcome: number
  /** O período passa do horizonte em que as sessões recorrentes já foram criadas. */
  forecastIncomplete: boolean
  periods: EarningsBar[]
  receivableSessions: ReceivableSession[]
}

export function useEarnings(from: string, to: string, groupBy: GroupBy) {
  return useQuery({
    // Debaixo de "sessions": toda mudança na agenda (que invalida 'sessions') atualiza os ganhos.
    queryKey: ['sessions', 'earnings', from, to, groupBy],
    queryFn: () => api<Earnings>(`/earnings?${new URLSearchParams({ from, to, groupBy })}`),
    placeholderData: keepPreviousData,
  })
}

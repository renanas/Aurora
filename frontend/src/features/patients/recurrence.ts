import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/client'
import { parseDateString, weekdayFull } from '../../shared/lib/dates'

export type Frequency = 'WEEKLY' | 'BIWEEKLY'

export interface Recurrence {
  id: string
  anchorDate: string
  startTime: string
  frequency: Frequency
  endsOn: string | null
  active: boolean
}

export interface RecurrenceInput {
  anchorDate: string
  startTime: string
  frequency: Frequency
}

export const RECURRENCE_KEY = ['recurrence'] as const

/** Horário recorrente ativo do paciente; `null` quando não há (o servidor responde 204). */
export function useRecurrence(patientId: string) {
  return useQuery({
    queryKey: [...RECURRENCE_KEY, patientId],
    queryFn: async () => (await api<Recurrence | undefined>(`/patients/${patientId}/recurrence`)) ?? null,
  })
}

function useRecurrenceMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: RECURRENCE_KEY }),
        queryClient.invalidateQueries({ queryKey: ['sessions'] }),
      ])
    },
  })
}

export function useReplaceRecurrence(patientId: string) {
  return useRecurrenceMutation((input: RecurrenceInput) =>
    api<Recurrence>(`/patients/${patientId}/recurrence`, { method: 'PUT', body: input }),
  )
}

export function useEndRecurrence(patientId: string) {
  return useRecurrenceMutation(() => api<void>(`/patients/${patientId}/recurrence/end`, { method: 'POST' }))
}

/** "Toda segunda às 09:00" ou "Quinzenal, segunda às 09:00" (o dia da semana vem da data da primeira sessão). */
export function describeRecurrence(r: Recurrence): string {
  const anchor = parseDateString(r.anchorDate)
  const day = anchor ? weekdayFull(anchor).replace('-feira', '') : ''
  if (r.frequency === 'BIWEEKLY') return `Quinzenal, ${day} às ${r.startTime}`
  const every = day === 'sábado' || day === 'domingo' ? 'Todo' : 'Toda'
  return `${every} ${day} às ${r.startTime}`
}

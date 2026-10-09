import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/client'

export interface Patient {
  id: string
  name: string
  whatsapp: string
  sessionPrice: number
  active: boolean
}

export interface PatientInput {
  name: string
  whatsapp: string
  sessionPrice: number
}

const PATIENTS = ['patients'] as const

export function usePatients(search: string, active: boolean) {
  return useQuery({
    queryKey: [...PATIENTS, 'list', search, active],
    queryFn: () => api<Patient[]>(`/patients?${new URLSearchParams({ search, active: String(active) })}`),
    placeholderData: keepPreviousData,
  })
}

export function usePatient(id: string | undefined) {
  return useQuery({
    queryKey: [...PATIENTS, 'detail', id],
    queryFn: () => api<Patient>(`/patients/${id}`),
    enabled: id !== undefined,
  })
}

function useInvalidatingMutation<TVariables>(fn: (variables: TVariables) => Promise<Patient>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PATIENTS }),
  })
}

export function useCreatePatient() {
  return useInvalidatingMutation((input: PatientInput) =>
    api<Patient>('/patients', { method: 'POST', body: input }),
  )
}

export function useUpdatePatient() {
  return useInvalidatingMutation(({ id, input }: { id: string; input: PatientInput }) =>
    api<Patient>(`/patients/${id}`, { method: 'PUT', body: input }),
  )
}

export function useDeactivatePatient() {
  return useInvalidatingMutation((id: string) => api<Patient>(`/patients/${id}/deactivate`, { method: 'POST' }))
}

export function useActivatePatient() {
  return useInvalidatingMutation((id: string) => api<Patient>(`/patients/${id}/activate`, { method: 'POST' }))
}

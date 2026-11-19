import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/client'

export interface Account {
  id: string
  email: string
  displayName: string
  role: 'ADMIN' | 'PSYCHOLOGIST'
  active: boolean
  mustChangePassword: boolean
  /** Falso enquanto o cadastro feito pela própria pessoa aguarda aprovação. */
  approved: boolean
  createdAt: string
}

const ACCOUNTS = ['accounts'] as const

export function useAccounts() {
  return useQuery({ queryKey: ACCOUNTS, queryFn: () => api<Account[]>('/admin/accounts') })
}

export function useApproveAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<Account>(`/admin/accounts/${id}/approve`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ACCOUNTS }),
  })
}

export function useRejectAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/admin/accounts/${id}/reject`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ACCOUNTS }),
  })
}

export function useSetAccountActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api<Account>(`/admin/accounts/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ACCOUNTS }),
  })
}

export function useResetPassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, temporaryPassword }: { id: string; temporaryPassword: string }) =>
      api<Account>(`/admin/accounts/${id}/reset-password`, { method: 'POST', body: { temporaryPassword } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ACCOUNTS }),
  })
}

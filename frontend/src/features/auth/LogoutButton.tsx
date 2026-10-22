import { useMutation, useQueryClient } from '@tanstack/react-query'
import { dropSession, logout } from './api'

export default function LogoutButton() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: logout,
    // Mesmo se a chamada falhar (sem rede), os dados saem da tela.
    onSettled: () => dropSession(queryClient),
  })

  return (
    <button
      type="button"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className="rounded-lg px-3 py-2 text-slate-600"
    >
      Sair
    </button>
  )
}

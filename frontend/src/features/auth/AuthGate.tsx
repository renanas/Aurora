import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AUTH_KEY, fetchMe } from './api'
import LoginPage from './LoginPage'

/** Só mostra o app para quem tem sessão válida; senão, mostra o login. */
export default function AuthGate({ children }: { children: ReactNode }) {
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: AUTH_KEY,
    queryFn: fetchMe,
  })

  // Já logada: uma falha passageira de rede ao revalidar não derruba a tela.
  if (data) return <>{children}</>

  if (isPending) {
    return <p className="p-6 text-slate-500">Carregando…</p>
  }

  if (isError) {
    return (
      <div className="p-6">
        <p className="text-slate-700">Não foi possível falar com o servidor.</p>
        <button
          type="button"
          onClick={() => refetch()}
          className="mt-3 rounded-lg bg-indigo-600 px-4 py-2 text-white"
        >
          Tentar de novo
        </button>
      </div>
    )
  }

  return <LoginPage />
}

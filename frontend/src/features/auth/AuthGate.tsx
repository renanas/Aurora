import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router'
import { AUTH_KEY, fetchMe } from './api'
import ForcedPasswordChange from './ForcedPasswordChange'
import LoginPage from './LoginPage'
import RegisterPage from './RegisterPage'

/** Só mostra o app para quem tem sessão válida; senão, mostra o login (ou o cadastro, em /cadastro). */
export default function AuthGate({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: AUTH_KEY,
    queryFn: fetchMe,
  })

  // Já logada: uma falha passageira de rede ao revalidar não derruba a tela.
  // Com senha provisória pendente, nada do app abre antes de trocá-la (o servidor também exige).
  if (data) return data.mustChangePassword ? <ForcedPasswordChange /> : <>{children}</>

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

  return location.pathname === '/cadastro' ? <RegisterPage /> : <LoginPage />
}

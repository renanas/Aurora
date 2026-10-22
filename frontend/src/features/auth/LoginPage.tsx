import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { AUTH_KEY, login } from './api'

const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-3 text-base focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'

export default function LoginPage() {
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: () => login(username, password),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: AUTH_KEY }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  let message: string | null = null
  if (mutation.error) {
    message =
      mutation.error instanceof ApiError
        ? mutation.error.message
        : 'Não foi possível entrar. Tente de novo.'
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
      >
        <h1 className="text-2xl font-semibold text-indigo-700">Aurora</h1>
        <p className="mt-1 text-slate-600">Entre para ver a agenda.</p>

        <label className="mt-6 block text-base font-medium text-slate-700">
          Usuário
          <input
            className={inputClass}
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            autoFocus
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </label>

        <label className="mt-4 block text-base font-medium text-slate-700">
          Senha
          <input
            className={inputClass}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>

        {message && (
          <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-red-700">
            {message}
          </p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending || !username || !password}
          className="mt-6 w-full rounded-lg bg-indigo-600 px-4 py-3 text-base font-medium text-white disabled:opacity-50"
        >
          {mutation.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  )
}

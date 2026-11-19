import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { messageFor } from '../../shared/api/errors'
import { AUTH_KEY, changePassword } from './api'
import { checkNewPassword, MIN_PASSWORD_LENGTH } from './passwordRules'

const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-3 text-base focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'

/** Formulário de troca da própria senha. Serve para a troca obrigatória e para a voluntária. */
export default function ChangePasswordForm() {
  const queryClient = useQueryClient()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const mutation = useMutation({
    mutationFn: () => changePassword(current, next),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setConfirmation('')
      setDone(true)
      // Atualiza "quem sou eu": se a troca era obrigatória, a tela normal abre.
      return queryClient.invalidateQueries({ queryKey: AUTH_KEY })
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setDone(false)
    const problem = checkNewPassword(current, next, confirmation)
    setLocalError(problem)
    if (problem === null) mutation.mutate()
  }

  const message = localError ?? (mutation.error ? messageFor(mutation.error) : null)

  return (
    <form onSubmit={submit}>
      <label className="block text-base font-medium text-slate-700">
        Senha atual
        <input
          className={inputClass}
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
        />
      </label>

      <label className="mt-4 block text-base font-medium text-slate-700">
        Nova senha
        <input
          className={inputClass}
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(event) => setNext(event.target.value)}
        />
      </label>
      <p className="mt-1 text-sm text-slate-500">Pelo menos {MIN_PASSWORD_LENGTH} caracteres.</p>

      <label className="mt-4 block text-base font-medium text-slate-700">
        Repita a nova senha
        <input
          className={inputClass}
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
        />
      </label>

      {message && (
        <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-red-700">
          {message}
        </p>
      )}
      {done && !message && (
        <p role="status" className="mt-4 rounded-lg bg-green-50 px-3 py-2 text-green-700">
          Senha trocada.
        </p>
      )}

      <button
        type="submit"
        disabled={mutation.isPending || !current || !next || !confirmation}
        className="mt-6 w-full rounded-lg bg-indigo-600 px-4 py-3 text-base font-medium text-white disabled:opacity-50"
      >
        {mutation.isPending ? 'Trocando…' : 'Trocar senha'}
      </button>
    </form>
  )
}

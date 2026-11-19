import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { messageFor } from '../../shared/api/errors'
import { register } from './api'
import { checkPasswordStrength, MIN_PASSWORD_LENGTH } from './passwordRules'

const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-3 text-base focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'

/**
 * Cadastro pela própria psicóloga. A conta só entra depois que o administrador aprovar.
 * O servidor responde igual se o e-mail já tem conta (não revela quem está cadastrada).
 */
export default function RegisterPage() {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => register({ displayName: displayName.trim(), email, password }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const problem =
      checkPasswordStrength(password) ?? (password !== confirmation ? 'A confirmação não é igual à senha.' : null)
    setLocalError(problem)
    if (problem === null) mutation.mutate()
  }

  const message = localError ?? (mutation.error ? messageFor(mutation.error) : null)

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold text-indigo-700">Aurora</h1>

        {mutation.isSuccess ? (
          <div role="status">
            <p className="mt-4 text-lg font-medium text-slate-800">Cadastro enviado.</p>
            <p className="mt-2 text-slate-600">
              Você poderá entrar com este e-mail e senha assim que o administrador aprovar o cadastro.
            </p>
            <Link
              to="/login"
              className="mt-6 block w-full rounded-lg bg-indigo-600 px-4 py-3 text-center text-base font-medium text-white"
            >
              Voltar para a entrada
            </Link>
          </div>
        ) : (
          <form onSubmit={submit}>
            <p className="mt-1 text-slate-600">Crie sua conta. O administrador precisa aprovar antes do primeiro acesso.</p>

            <label className="mt-6 block text-base font-medium text-slate-700">
              Nome
              <input
                className={inputClass}
                type="text"
                autoComplete="name"
                maxLength={150}
                autoFocus
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>

            <label className="mt-4 block text-base font-medium text-slate-700">
              E-mail
              <input
                className={inputClass}
                type="text"
                inputMode="email"
                autoComplete="username"
                autoCapitalize="none"
                maxLength={254}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <label className="mt-4 block text-base font-medium text-slate-700">
              Senha
              <input
                className={inputClass}
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            <p className="mt-1 text-sm text-slate-500">Pelo menos {MIN_PASSWORD_LENGTH} caracteres.</p>

            <label className="mt-4 block text-base font-medium text-slate-700">
              Repita a senha
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

            <button
              type="submit"
              disabled={mutation.isPending || !displayName.trim() || !email.trim() || !password}
              className="mt-6 w-full rounded-lg bg-indigo-600 px-4 py-3 text-base font-medium text-white disabled:opacity-50"
            >
              {mutation.isPending ? 'Enviando…' : 'Criar conta'}
            </button>

            <p className="mt-4 text-center text-slate-600">
              Já tem conta?{' '}
              <Link to="/login" className="text-indigo-700 underline">
                Entrar
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}

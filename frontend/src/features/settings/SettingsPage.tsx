import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { messageFor } from '../../shared/api/errors'
import { useTheme } from '../../shared/lib/ThemeProvider'
import type { ThemeChoice } from '../../shared/lib/theme'
import { AUTH_KEY, changeEmail, changeName, useMe } from '../auth/api'
import type { Me } from '../auth/api'
import ChangePasswordForm from '../auth/ChangePasswordForm'

const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-3 text-base focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'
const primaryButton =
  'mt-4 rounded-lg bg-indigo-600 px-5 py-3 text-base font-medium text-white disabled:opacity-50'

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
      {description && <p className="mt-1 text-slate-600">{description}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function Feedback({ errorMessage, successMessage }: { errorMessage: string | null; successMessage: string | null }) {
  if (errorMessage) {
    return (
      <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-red-700">
        {errorMessage}
      </p>
    )
  }
  if (successMessage) {
    return (
      <p role="status" className="mt-4 rounded-lg bg-green-50 px-3 py-2 text-green-800">
        {successMessage}
      </p>
    )
  }
  return null
}

function NameForm({ loggedUser }: { loggedUser: Me }) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(loggedUser.displayName)
  const nameMutation = useMutation({
    mutationFn: () => changeName(displayName.trim()),
    onSuccess: (updatedUser) => queryClient.setQueryData(AUTH_KEY, updatedUser),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    nameMutation.mutate()
  }

  return (
    <form onSubmit={submit}>
      <label className="block text-base font-medium text-slate-700">
        Nome
        <input
          className={inputClass}
          type="text"
          autoComplete="name"
          maxLength={150}
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
      </label>
      <Feedback
        errorMessage={nameMutation.error ? messageFor(nameMutation.error) : null}
        successMessage={nameMutation.isSuccess ? 'Nome atualizado.' : null}
      />
      <button
        type="submit"
        disabled={nameMutation.isPending || !displayName.trim() || displayName.trim() === loggedUser.displayName}
        className={primaryButton}
      >
        Salvar nome
      </button>
    </form>
  )
}

function EmailForm({ loggedUser }: { loggedUser: Me }) {
  const queryClient = useQueryClient()
  const [newEmail, setNewEmail] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const emailMutation = useMutation({
    mutationFn: () => changeEmail(newEmail, currentPassword),
    onSuccess: (updatedUser) => {
      queryClient.setQueryData(AUTH_KEY, updatedUser)
      setNewEmail('')
      setCurrentPassword('')
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    emailMutation.mutate()
  }

  return (
    <form onSubmit={submit}>
      <p className="text-slate-600">
        E-mail atual: <span className="font-medium text-slate-900">{loggedUser.username}</span>
      </p>
      <label className="mt-4 block text-base font-medium text-slate-700">
        Novo e-mail
        <input
          className={inputClass}
          type="text"
          inputMode="email"
          autoCapitalize="none"
          autoComplete="email"
          maxLength={254}
          value={newEmail}
          onChange={(event) => setNewEmail(event.target.value)}
        />
      </label>
      <label className="mt-4 block text-base font-medium text-slate-700">
        Senha atual (para confirmar)
        <input
          className={inputClass}
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />
      </label>
      <Feedback
        errorMessage={emailMutation.error ? messageFor(emailMutation.error) : null}
        successMessage={emailMutation.isSuccess ? 'E-mail atualizado. Use o novo e-mail para entrar.' : null}
      />
      <button
        type="submit"
        disabled={emailMutation.isPending || !newEmail.trim() || !currentPassword}
        className={primaryButton}
      >
        Salvar e-mail
      </button>
    </form>
  )
}

const THEME_OPTIONS: { value: ThemeChoice; label: string; description: string }[] = [
  { value: 'light', label: 'Claro', description: 'Fundo branco.' },
  { value: 'dark', label: 'Escuro', description: 'Fundo escuro, melhor à noite.' },
  { value: 'system', label: 'Automático', description: 'Segue o claro/escuro do aparelho.' },
]

function ThemePicker() {
  const { themeChoice, changeTheme } = useTheme()
  return (
    <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Aparência">
      {THEME_OPTIONS.map((themeOption) => {
        const selected = themeChoice === themeOption.value
        return (
          <button
            key={themeOption.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => changeTheme(themeOption.value)}
            className={`rounded-xl border p-4 text-left ${
              selected ? 'border-indigo-600 bg-indigo-50 ring-2 ring-indigo-200' : 'border-slate-200 bg-white'
            }`}
          >
            <p className="font-medium text-slate-900">{themeOption.label}</p>
            <p className="text-sm text-slate-600">{themeOption.description}</p>
          </button>
        )
      })}
    </div>
  )
}

/** Configurações da própria conta: nome, e-mail, senha e aparência. */
export default function SettingsPage() {
  const { data: loggedUser } = useMe()
  if (!loggedUser) return null

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-slate-900">Configurações</h1>
      <Section title="Aparência" description="Vale só para este aparelho.">
        <ThemePicker />
      </Section>
      <Section title="Nome" description="É o nome que aparece no topo da tela.">
        <NameForm loggedUser={loggedUser} />
      </Section>
      <Section title="E-mail" description="É o e-mail que você usa para entrar.">
        <EmailForm loggedUser={loggedUser} />
      </Section>
      <Section title="Senha">
        <ChangePasswordForm />
      </Section>
    </div>
  )
}

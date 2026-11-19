import { useState } from 'react'
import type { FormEvent } from 'react'
import { messageFor } from '../../shared/api/errors'
import { useMe } from '../auth/api'
import { checkPasswordStrength } from '../auth/passwordRules'
import { useAccounts, useApproveAccount, useRejectAccount, useResetPassword, useSetAccountActive } from './api'
import type { Account } from './api'
import { generatePassword } from './generatePassword'

const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-3 text-base focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200'
const secondaryButton = 'rounded-lg border border-slate-300 px-4 py-2 text-base text-slate-700 disabled:opacity-50'

/** Dados para passar à psicóloga. A senha provisória só aparece aqui, uma vez. */
interface Credentials {
  email: string
  password: string
  title: string
}

function CredentialsNotice({ credentials, onClose }: { credentials: Credentials; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(credentials.password)
      setCopied(true)
    } catch {
      setCopied(false) // sem permissão de área de transferência: a senha continua na tela para copiar à mão
    }
  }

  return (
    <div role="status" className="mt-6 rounded-xl border border-green-200 bg-green-50 p-4">
      <p className="font-medium text-green-800">{credentials.title}</p>
      <p className="mt-2 text-slate-700">
        Passe estes dados para a psicóloga. Ela será obrigada a trocar a senha no próximo acesso.
      </p>
      <dl className="mt-3 space-y-1 text-base">
        <div>
          <dt className="inline text-slate-600">E-mail: </dt>
          <dd className="inline font-medium text-slate-900">{credentials.email}</dd>
        </div>
        <div>
          <dt className="inline text-slate-600">Senha provisória: </dt>
          <dd className="inline font-mono font-medium text-slate-900">{credentials.password}</dd>
        </div>
      </dl>
      <p className="mt-2 text-sm text-slate-600">Esta senha não será mostrada de novo.</p>
      <div className="mt-3 flex gap-2">
        <button type="button" onClick={copy} className={secondaryButton}>
          {copied ? 'Copiada' : 'Copiar senha'}
        </button>
        <button type="button" onClick={onClose} className={secondaryButton}>
          Fechar
        </button>
      </div>
    </div>
  )
}

function PendingCard({ account }: { account: Account }) {
  const approve = useApproveAccount()
  const reject = useRejectAccount()
  const busy = approve.isPending || reject.isPending
  const error = approve.error ?? reject.error

  return (
    <li className="rounded-2xl border border-amber-200 bg-white p-4 shadow-sm">
      <div className="min-w-0">
        <p className="truncate text-lg font-medium text-slate-900">{account.displayName}</p>
        <p className="truncate text-slate-600">{account.email}</p>
        <p className="text-sm text-slate-500">
          Pedido em {new Date(account.createdAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}
        </p>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          disabled={busy}
          onClick={() => approve.mutate(account.id)}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-base font-medium text-white disabled:opacity-50"
        >
          Aprovar
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            if (window.confirm(`Recusar o cadastro de ${account.displayName}? O pedido será apagado.`)) {
              reject.mutate(account.id)
            }
          }}
          className={secondaryButton}
        >
          Recusar
        </button>
      </div>
      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-700">
          {messageFor(error)}
        </p>
      )}
    </li>
  )
}

function AccountCard({
  account,
  isSelf,
  onCredentials,
}: {
  account: Account
  isSelf: boolean
  onCredentials: (credentials: Credentials) => void
}) {
  const setActive = useSetAccountActive()
  const reset = useResetPassword()
  const [resetting, setResetting] = useState(false)
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  function submitReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const problem = checkPasswordStrength(temporaryPassword)
    setLocalError(problem)
    if (problem !== null) return
    reset.mutate(
      { id: account.id, temporaryPassword },
      {
        onSuccess: () => {
          onCredentials({ email: account.email, password: temporaryPassword, title: 'Senha redefinida.' })
          setResetting(false)
          setTemporaryPassword('')
        },
      },
    )
  }

  const error = localError ?? (reset.error ? messageFor(reset.error) : null) ?? (setActive.error ? messageFor(setActive.error) : null)

  return (
    <li className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-lg font-medium text-slate-900">
            {account.displayName}
            {isSelf && <span className="ml-2 text-sm font-normal text-slate-500">(você)</span>}
          </p>
          <p className="truncate text-slate-600">{account.email}</p>
        </div>
        <div className="flex flex-wrap gap-1 text-sm">
          <span className="rounded-full bg-slate-100 px-2 py-1 text-slate-700">
            {account.role === 'ADMIN' ? 'Administrador' : 'Psicóloga'}
          </span>
          <span
            className={`rounded-full px-2 py-1 ${account.active ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}
          >
            {account.active ? 'Ativa' : 'Desativada'}
          </span>
          {account.mustChangePassword && (
            <span className="rounded-full bg-amber-50 px-2 py-1 text-amber-700">Troca de senha pendente</span>
          )}
        </div>
      </div>

      {!isSelf && (
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            disabled={setActive.isPending}
            onClick={() => setActive.mutate({ id: account.id, active: !account.active })}
            className={secondaryButton}
          >
            {account.active ? 'Desativar' : 'Reativar'}
          </button>
          {!resetting && (
            <button type="button" onClick={() => setResetting(true)} className={secondaryButton}>
              Redefinir senha
            </button>
          )}
        </div>
      )}

      {resetting && (
        <form onSubmit={submitReset} className="mt-3">
          <label className="block text-base font-medium text-slate-700">
            Nova senha provisória
            <div className="flex gap-2">
              <input
                className={inputClass}
                type="text"
                autoComplete="off"
                value={temporaryPassword}
                onChange={(event) => setTemporaryPassword(event.target.value)}
              />
              <button
                type="button"
                onClick={() => setTemporaryPassword(generatePassword())}
                className={`${secondaryButton} mt-1 shrink-0`}
              >
                Gerar
              </button>
            </div>
          </label>
          <div className="mt-3 flex gap-2">
            <button
              type="submit"
              disabled={reset.isPending || !temporaryPassword}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-base font-medium text-white disabled:opacity-50"
            >
              {reset.isPending ? 'Redefinindo…' : 'Redefinir'}
            </button>
            <button
              type="button"
              onClick={() => {
                setResetting(false)
                setTemporaryPassword('')
                setLocalError(null)
              }}
              className={secondaryButton}
            >
              Cancelar
            </button>
          </div>
        </form>
      )}

      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-700">
          {error}
        </p>
      )}
    </li>
  )
}

/** Administração de contas: só o perfil ADMIN chega aqui (o servidor também confere). */
export default function AccountsPage() {
  const accounts = useAccounts()
  const loggedUserQuery = useMe()
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  const pending = accounts.data?.filter((account) => !account.approved) ?? []
  const approved = accounts.data?.filter((account) => account.approved) ?? []

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-800">Contas</h1>
      <p className="mt-1 text-slate-600">
        Cada psicóloga cria a própria conta na tela de cadastro e só entra depois que você aprovar. Cada uma só vê
        os próprios pacientes e sessões.
      </p>

      {credentials && <CredentialsNotice credentials={credentials} onClose={() => setCredentials(null)} />}

      <h2 className="mt-8 text-lg font-semibold text-slate-800">Aguardando aprovação</h2>
      {pending.length === 0 && accounts.data && (
        <p className="mt-3 text-slate-500">Nenhum cadastro aguardando aprovação.</p>
      )}
      {pending.length > 0 && (
        <ul className="mt-3 space-y-3">
          {pending.map((account) => (
            <PendingCard key={account.id} account={account} />
          ))}
        </ul>
      )}

      <h2 className="mt-8 text-lg font-semibold text-slate-800">Contas existentes</h2>
      {accounts.isPending && <p className="mt-3 text-slate-500">Carregando…</p>}
      {accounts.isError && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-700">
          {messageFor(accounts.error)}
        </p>
      )}
      {accounts.data && (
        <ul className="mt-3 space-y-3">
          {approved.map((account) => (
            <AccountCard
              key={account.id}
              account={account}
              isSelf={loggedUserQuery.data?.username === account.email}
              onCredentials={setCredentials}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

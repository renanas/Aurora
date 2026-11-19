import ChangePasswordForm from './ChangePasswordForm'
import LogoutButton from './LogoutButton'

/** Tela única de quem entrou com senha provisória: precisa trocá-la antes de usar o sistema. */
export default function ForcedPasswordChange() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold text-indigo-700">Aurora</h1>
        <p className="mt-1 mb-6 text-slate-600">
          Você entrou com uma senha provisória. Escolha uma senha nova para continuar.
        </p>
        <ChangePasswordForm />
        <div className="mt-4 text-center">
          <LogoutButton />
        </div>
      </div>
    </div>
  )
}

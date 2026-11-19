import ChangePasswordForm from './ChangePasswordForm'

/** Troca voluntária da senha, dentro do app. */
export default function ChangePasswordPage() {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-semibold text-slate-800">Trocar senha</h1>
      <p className="mt-1 mb-6 text-slate-600">Escolha uma senha que só você conheça.</p>
      <ChangePasswordForm />
    </div>
  )
}

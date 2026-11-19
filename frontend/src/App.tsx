import { Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router'
import AccountsPage from './features/accounts/AccountsPage'
import AgendaPage from './features/agenda/AgendaPage'
import { useMe } from './features/auth/api'
import AuthGate from './features/auth/AuthGate'
import ChangePasswordPage from './features/auth/ChangePasswordPage'
import CurrentUserName from './features/auth/CurrentUserName'
import EarningsPage from './features/earnings/EarningsPage'
import LogoutButton from './features/auth/LogoutButton'
import PatientsPage from './features/patients/PatientsPage'
import PatientFormPage from './features/patients/PatientFormPage'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 ${isActive ? 'bg-indigo-50 font-medium text-indigo-700' : 'text-slate-600'}`

/** Telas de formulário e lista ficam estreitas; a agenda usa a largura toda. */
function NarrowLayout() {
  return (
    <div className="mx-auto max-w-3xl">
      <Outlet />
    </div>
  )
}

function Layout() {
  const me = useMe()
  const isAdmin = me.data?.role === 'ADMIN'

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-y-2 px-4 py-3">
          <Link to="/agenda" className="text-xl font-semibold text-indigo-700">
            Aurora
          </Link>
          <nav className="flex flex-wrap items-center gap-1 text-base">
            <NavLink to="/agenda" className={navClass}>
              Agenda
            </NavLink>
            <NavLink to="/patients" className={navClass}>
              Pacientes
            </NavLink>
            <NavLink to="/ganhos" className={navClass}>
              Ganhos
            </NavLink>
            {isAdmin && (
              <NavLink to="/accounts" className={navClass}>
                Contas
              </NavLink>
            )}
            <NavLink to="/password" className={navClass}>
              Senha
            </NavLink>
            <CurrentUserName />
            <LogoutButton />
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/" element={<Navigate to="/agenda" replace />} />
          <Route path="/agenda" element={<AgendaPage />} />
          <Route path="/ganhos" element={<EarningsPage />} />
          <Route element={<NarrowLayout />}>
            <Route path="/patients" element={<PatientsPage />} />
            <Route path="/patients/new" element={<PatientFormPage />} />
            <Route path="/patients/:id" element={<PatientFormPage />} />
            <Route
              path="/accounts"
              element={isAdmin ? <AccountsPage /> : <Navigate to="/agenda" replace />}
            />
            <Route path="/password" element={<ChangePasswordPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/agenda" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <AuthGate>
      <Layout />
    </AuthGate>
  )
}

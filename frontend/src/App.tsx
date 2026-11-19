import { Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router'
import AccountsPage from './features/accounts/AccountsPage'
import AgendaPage from './features/agenda/AgendaPage'
import { useMe } from './features/auth/api'
import AuthGate from './features/auth/AuthGate'
import EarningsPage from './features/earnings/EarningsPage'
import UserMenu from './features/auth/UserMenu'
import PatientsPage from './features/patients/PatientsPage'
import PatientFormPage from './features/patients/PatientFormPage'
import SettingsPage from './features/settings/SettingsPage'

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
  const loggedUserQuery = useMe()
  const isAdmin = loggedUserQuery.data?.role === 'ADMIN'

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
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
          </nav>
          <UserMenu />
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
            <Route path="/configuracoes" element={<SettingsPage />} />
            <Route path="/password" element={<Navigate to="/configuracoes" replace />} />
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

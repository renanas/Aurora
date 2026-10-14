import { Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router'
import AgendaPage from './features/agenda/AgendaPage'
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

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/agenda" className="text-xl font-semibold text-indigo-700">
            Aurora
          </Link>
          <nav className="flex gap-1 text-base">
            <NavLink to="/agenda" className={navClass}>
              Agenda
            </NavLink>
            <NavLink to="/patients" className={navClass}>
              Pacientes
            </NavLink>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/" element={<Navigate to="/agenda" replace />} />
          <Route path="/agenda" element={<AgendaPage />} />
          <Route element={<NarrowLayout />}>
            <Route path="/patients" element={<PatientsPage />} />
            <Route path="/patients/new" element={<PatientFormPage />} />
            <Route path="/patients/:id" element={<PatientFormPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/agenda" replace />} />
        </Routes>
      </main>
    </div>
  )
}

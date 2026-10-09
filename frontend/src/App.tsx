import { Link, Navigate, Route, Routes } from 'react-router'
import PatientsPage from './features/patients/PatientsPage'
import PatientFormPage from './features/patients/PatientFormPage'

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
          <Link to="/patients" className="text-xl font-semibold text-indigo-700">
            Aurora
          </Link>
          <nav className="text-base text-slate-600">
            <Link to="/patients" className="px-3 py-2">
              Pacientes
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-6">
        <Routes>
          <Route path="/" element={<Navigate to="/patients" replace />} />
          <Route path="/patients" element={<PatientsPage />} />
          <Route path="/patients/new" element={<PatientFormPage />} />
          <Route path="/patients/:id" element={<PatientFormPage />} />
          <Route path="*" element={<Navigate to="/patients" replace />} />
        </Routes>
      </main>
    </div>
  )
}

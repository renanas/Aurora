import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { messageFor } from '../../shared/api/errors'
import { formatMoney, formatPhone } from '../../shared/lib/format'
import { usePatients } from './api'

export default function PatientsPage() {
  const [text, setText] = useState('')
  const [search, setSearch] = useState('')
  const [active, setActive] = useState(true)

  // Espera a pessoa parar de digitar antes de consultar o servidor.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(text.trim()), 250)
    return () => clearTimeout(timer)
  }, [text])

  const { data, isPending, error } = usePatients(search, active)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Pacientes</h1>
        <Link
          to="/patients/new"
          className="rounded-lg bg-indigo-600 px-5 py-3 text-lg font-medium text-white active:bg-indigo-700"
        >
          Novo paciente
        </Link>
      </div>

      <input
        type="search"
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Buscar por nome"
        aria-label="Buscar por nome"
        className="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-lg"
      />

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setActive(true)}
          className={`rounded-lg px-4 py-2 text-base ${active ? 'bg-slate-800 text-white' : 'bg-white text-slate-700 border border-slate-300'}`}
        >
          Ativos
        </button>
        <button
          type="button"
          onClick={() => setActive(false)}
          className={`rounded-lg px-4 py-2 text-base ${!active ? 'bg-slate-800 text-white' : 'bg-white text-slate-700 border border-slate-300'}`}
        >
          Inativos
        </button>
      </div>

      {error && <p className="rounded-lg bg-red-50 p-4 text-red-700">{messageFor(error)}</p>}
      {isPending && <p className="text-slate-500">Carregando…</p>}

      {data && data.length === 0 && (
        <p className="rounded-lg bg-white p-6 text-center text-slate-500">
          {search ? 'Nenhum paciente encontrado.' : active ? 'Nenhum paciente cadastrado ainda.' : 'Nenhum paciente inativo.'}
        </p>
      )}

      <ul className="space-y-2">
        {data?.map((patient) => (
          <li key={patient.id}>
            <Link
              to={`/patients/${patient.id}`}
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-4 active:bg-slate-100"
            >
              <div>
                <p className="text-lg font-medium">{patient.name}</p>
                <p className="text-base text-slate-500">{formatPhone(patient.whatsapp)}</p>
              </div>
              <p className="text-base text-slate-700">{formatMoney(patient.sessionPrice)}</p>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

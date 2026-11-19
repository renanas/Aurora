import { useMe } from './api'

/** Nome de quem está logada, no cabeçalho. Lê a mesma consulta do AuthGate, sem pedir de novo. */
export default function CurrentUserName() {
  const { data } = useMe()
  if (!data) return null

  return (
    <span
      title={data.username}
      className="hidden max-w-[14rem] truncate px-3 py-2 text-slate-500 sm:block"
    >
      {data.displayName}
    </span>
  )
}

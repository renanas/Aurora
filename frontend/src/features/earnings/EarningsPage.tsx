import { Link, useSearchParams } from 'react-router'
import { messageFor } from '../../shared/api/errors'
import { dateOf, formatDateBR, startOfWeek, timeOf, toDateString } from '../../shared/lib/dates'
import { formatMoney } from '../../shared/lib/format'
import { statusLabel } from '../agenda/status'
import { useEarnings } from './api'
import type { Earnings } from './api'
import { barHeights, barLabel, periodFor, periodFromParams, periodLabel, periodToParams, shiftPeriod } from './periods'
import type { Period, View } from './periods'

const BAND = {
  received: { label: 'Recebido', bar: 'bg-emerald-500', card: 'border-emerald-200 bg-emerald-50 text-emerald-900' },
  receivable: { label: 'A receber', bar: 'bg-amber-400', card: 'border-amber-200 bg-amber-50 text-amber-900' },
  expected: { label: 'Previsto', bar: 'bg-indigo-300', card: 'border-indigo-200 bg-indigo-50 text-indigo-900' },
} as const

const BAND_HINT = {
  received: 'Sessões pagas.',
  receivable: 'Realizadas, faltas e cancelamentos em cima da hora ainda não pagos.',
  expected: 'Sessões agendadas ou confirmadas ainda não pagas.',
} as const

const navButton = 'rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50'

function ViewToggle({ period, onChange }: { period: Period; onChange: (view: View) => void }) {
  const option = (view: View, label: string) => (
    <button
      type="button"
      onClick={() => onChange(view)}
      aria-pressed={period.view === view}
      className={`px-4 py-2 ${period.view === view ? 'bg-indigo-600 font-medium text-white' : 'bg-white text-slate-700'}`}
    >
      {label}
    </button>
  )
  return (
    <div className="flex overflow-hidden rounded-lg border border-slate-300">
      {option('week', 'Semana')}
      {option('month', 'Mês')}
    </div>
  )
}

function TotalCards({ earnings }: { earnings: Earnings }) {
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      {(['received', 'receivable', 'expected'] as const).map((band) => (
        <div key={band} className={`rounded-xl border p-4 ${BAND[band].card}`}>
          <p className="text-sm font-medium">{BAND[band].label}</p>
          <p className="mt-1 text-2xl font-semibold">{formatMoney(earnings.totals[band])}</p>
          <p className="mt-1 text-xs opacity-80">{BAND_HINT[band]}</p>
        </div>
      ))}
    </div>
  )
}

function Chart({ earnings }: { earnings: Earnings }) {
  const heights = barHeights(earnings.periods)
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex h-48 items-end gap-2" role="img" aria-label="Gráfico de ganhos por período">
        {earnings.periods.map((bar, i) => {
          const total = bar.received + bar.receivable + bar.expected
          return (
            <div
              key={bar.start}
              className="flex h-full min-w-0 flex-1 flex-col justify-end"
              title={`${barLabel(bar.start, bar.end, earnings.groupBy)}: ${formatMoney(total)}`}
            >
              {/* De cima para baixo: previsto, a receber, recebido (o recebido fica na base). */}
              <div className={`${BAND.expected.bar} rounded-t`} style={{ height: `${heights[i].expected}%` }} />
              <div className={BAND.receivable.bar} style={{ height: `${heights[i].receivable}%` }} />
              <div className={BAND.received.bar} style={{ height: `${heights[i].received}%` }} />
              {total === 0 && <div className="h-px bg-slate-200" />}
            </div>
          )
        })}
      </div>
      <div className="mt-2 flex gap-2">
        {earnings.periods.map((bar) => (
          <div key={bar.start} className="min-w-0 flex-1 text-center text-xs text-slate-600">
            <div className="truncate">{barLabel(bar.start, bar.end, earnings.groupBy)}</div>
            <div className="truncate text-slate-500">
              {formatMoney(bar.received + bar.receivable + bar.expected).replace(/,00$/, '')}
            </div>
          </div>
        ))}
      </div>
      <div className="mt-3 flex flex-wrap gap-4 text-sm text-slate-600">
        {(['received', 'receivable', 'expected'] as const).map((band) => (
          <span key={band} className="flex items-center gap-1">
            <span className={`inline-block h-3 w-3 rounded-sm ${BAND[band].bar}`} />
            {BAND[band].label}
          </span>
        ))}
      </div>
    </div>
  )
}

function Warnings({ earnings }: { earnings: Earnings }) {
  const items: string[] = []
  if (earnings.sessionsWithoutOutcome > 0) {
    const n = earnings.sessionsWithoutOutcome
    items.push(
      n === 1
        ? '1 sessão já passou e continua agendada. Marque se foi realizada, falta ou cancelada; até lá ela conta como prevista.'
        : `${n} sessões já passaram e continuam agendadas. Marque se foram realizadas, faltas ou canceladas; até lá elas contam como previstas.`,
    )
  }
  if (earnings.paidNotBillable.count > 0) {
    const n = earnings.paidNotBillable.count
    items.push(
      `Pagas em sessões não cobradas: ${formatMoney(earnings.paidNotBillable.amount)} (${n} ${n === 1 ? 'sessão' : 'sessões'}). ` +
        'Esse valor não entra nos totais acima, mas aparece no extrato do banco.',
    )
  }
  if (earnings.forecastIncomplete) {
    items.push(
      'As sessões recorrentes são criadas até 8 semanas à frente. Depois disso o previsto aparece incompleto, e não por falta de horário.',
    )
  }
  if (items.length === 0) return null
  return (
    <ul className="space-y-2">
      {items.map((text) => (
        <li key={text} className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {text}
        </li>
      ))}
    </ul>
  )
}

function ReceivableList({ earnings }: { earnings: Earnings }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-lg font-semibold text-slate-800">A receber</h2>
      {earnings.receivableSessions.length === 0 ? (
        <p className="mt-2 text-slate-500">Nada a receber neste período.</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100">
          {earnings.receivableSessions.map((s) => {
            const date = dateOf(s.startsAt)
            const week = toDateString(startOfWeek(new Date(`${date}T00:00`)))
            return (
              <li key={s.sessionId} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <div className="min-w-0">
                  <p className="truncate font-medium text-slate-900">{s.patientName}</p>
                  <p className="text-sm text-slate-600">
                    <Link to={`/agenda?week=${week}`} className="text-indigo-700 underline">
                      {formatDateBR(date)} às {timeOf(s.startsAt)}
                    </Link>{' '}
                    · {statusLabel[s.status]}
                  </p>
                </div>
                <span className="font-medium text-slate-900">{formatMoney(s.amount)}</span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/** Ganhos por semana ou por mês, pela data da sessão. Período na URL, como a semana da agenda. */
export default function EarningsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const today = new Date()
  const period = periodFromParams(searchParams, today)
  const query = useEarnings(toDateString(period.from), toDateString(period.to), period.groupBy)
  const goTo = (next: Period) => setSearchParams(periodToParams(next))
  const earnings = query.data

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Ganhos</h1>
          <p className="text-sm text-slate-600">{periodLabel(period)}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ViewToggle period={period} onChange={(view) => goTo(periodFor(view, period.from))} />
          <button type="button" onClick={() => goTo(shiftPeriod(period, -1))} className={navButton} aria-label="Anterior">
            ‹
          </button>
          <button type="button" onClick={() => goTo(periodFor(period.view, today))} className={navButton}>
            Hoje
          </button>
          <button type="button" onClick={() => goTo(shiftPeriod(period, 1))} className={navButton} aria-label="Próximo">
            ›
          </button>
        </div>
      </div>

      {query.isError && (
        <p role="alert" className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          {messageFor(query.error)}
        </p>
      )}
      {query.isPending && <p className="text-slate-500">Carregando…</p>}

      {earnings && (
        <>
          <TotalCards earnings={earnings} />
          <p className="text-slate-700">
            Total do período: <span className="font-semibold">{formatMoney(earnings.totals.total)}</span>
          </p>
          <Warnings earnings={earnings} />
          <Chart earnings={earnings} />
          <ReceivableList earnings={earnings} />
        </>
      )}
    </div>
  )
}

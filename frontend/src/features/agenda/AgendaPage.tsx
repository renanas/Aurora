import { useMemo, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
  addDays,
  dateOf,
  formatDateBR,
  formatDayMonth,
  minutesOfDay,
  parseDateString,
  startOfWeek,
  timeOf,
  toDateString,
  weekdayFull,
  weekdayShort,
} from '../../shared/lib/dates';
import { messageFor } from '../../shared/api/errors';
import { useSessions, type AgendaSession } from './api';
import {
  cancellationLabel,
  paymentLabel,
  statusBadgeClasses,
  statusBlockClasses,
  statusLabel,
} from './status';
import { hourRange, layoutDay } from './weekLayout';

// Mesmo valor do backend (SystemConstants): o gerador cria sessões até hoje + 8 semanas.
// Se mudar lá, mude aqui.
const GENERATION_HORIZON_WEEKS = 8;
const HOUR_PX = 64;

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export default function AgendaPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const today = useMemo(() => new Date(), []);
  const weekParam = searchParams.get('week');
  const parsedWeek = weekParam ? parseDateString(weekParam) : null;
  const weekStart = startOfWeek(parsedWeek ?? today);
  const weekEnd = addDays(weekStart, 6);

  const sessionsQuery = useSessions(toDateString(weekStart), toDateString(weekEnd));
  const sessions = useMemo(() => sessionsQuery.data ?? [], [sessionsQuery.data]);

  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
  const todayString = toDateString(today);

  const { startHour, endHour } = hourRange(
    sessions.map((s) => ({ startMin: minutesOfDay(s.startsAt), endMin: minutesOfDay(s.endsAt) })),
  );
  const hours = Array.from({ length: endHour - startHour }, (_, i) => startHour + i);
  const gridHeight = hours.length * HOUR_PX;

  const horizonDate = toDateString(addDays(today, GENERATION_HORIZON_WEEKS * 7));
  const beyondHorizon = toDateString(weekEnd) > horizonDate;

  const selected = sessions.find((s) => s.id === selectedId) ?? null;

  const goToWeek = (date: Date) => {
    setSelectedId(null);
    setSearchParams({ week: toDateString(startOfWeek(date)) });
  };

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Agenda</h1>
          <p className="text-sm text-slate-600">
            {formatDayMonth(weekStart)} a {formatDayMonth(weekEnd)} de {weekEnd.getFullYear()}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => goToWeek(addDays(weekStart, -7))}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50"
            aria-label="Semana anterior"
          >
            ‹
          </button>
          <button
            type="button"
            onClick={() => goToWeek(today)}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50"
          >
            Hoje
          </button>
          <button
            type="button"
            onClick={() => goToWeek(addDays(weekStart, 7))}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50"
            aria-label="Próxima semana"
          >
            ›
          </button>
        </div>
      </div>

      {beyondHorizon && (
        <p className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          As sessões recorrentes são criadas até {GENERATION_HORIZON_WEEKS} semanas à frente. Esta semana pode aparecer
          incompleta ou vazia por isso, e não por falta de horário.
        </p>
      )}

      {sessionsQuery.isError && (
        <p role="alert" className="mb-4 rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          {messageFor(sessionsQuery.error)}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <div className="min-w-[760px]">
          {/* Cabeçalho dos dias */}
          <div className="grid grid-cols-[3rem_repeat(7,minmax(0,1fr))] border-b border-slate-200">
            <div />
            {days.map((day) => {
              const isToday = toDateString(day) === todayString;
              return (
                <div
                  key={toDateString(day)}
                  className={`px-2 py-2 text-center text-sm ${isToday ? 'bg-indigo-50 font-semibold text-indigo-900' : 'text-slate-700'}`}
                >
                  <div className="capitalize">{weekdayShort(day)}</div>
                  <div>{formatDayMonth(day)}</div>
                </div>
              );
            })}
          </div>

          {/* Corpo: coluna de horas + 7 dias */}
          <div className="grid grid-cols-[3rem_repeat(7,minmax(0,1fr))]">
            <div className="relative" style={{ height: gridHeight }}>
              {hours.map((hour, i) => (
                <div
                  key={hour}
                  className="absolute right-1 -translate-y-1/2 text-xs text-slate-500"
                  style={{ top: i * HOUR_PX }}
                >
                  {i > 0 ? `${String(hour).padStart(2, '0')}:00` : ''}
                </div>
              ))}
            </div>

            {days.map((day) => {
              const dayString = toDateString(day);
              const daySessions = sessions.filter((s) => dateOf(s.startsAt) === dayString);
              const positioned = layoutDay(
                daySessions.map((s) => ({
                  id: s.id,
                  startMin: minutesOfDay(s.startsAt),
                  endMin: minutesOfDay(s.endsAt),
                })),
              );
              return (
                <div
                  key={dayString}
                  className={`relative border-l border-slate-200 ${dayString === todayString ? 'bg-indigo-50/30' : ''}`}
                  style={{ height: gridHeight }}
                >
                  {hours.map((hour, i) => (
                    <div
                      key={hour}
                      className="absolute inset-x-0 border-t border-slate-100"
                      style={{ top: i * HOUR_PX }}
                    />
                  ))}
                  {positioned.map((p) => {
                    const session = daySessions.find((s) => s.id === p.id)!;
                    const top = ((p.startMin - startHour * 60) / 60) * HOUR_PX;
                    const height = ((p.endMin - p.startMin) / 60) * HOUR_PX;
                    const hasOverlap = session.overlapsWith.length > 0;
                    return (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => setSelectedId(p.id)}
                        className={`absolute overflow-hidden rounded-md border px-1.5 py-1 text-left text-xs leading-tight shadow-sm ${statusBlockClasses[session.status]} ${hasOverlap ? '!border-amber-500 ring-2 ring-amber-300' : ''} ${p.id === selectedId ? 'outline outline-2 outline-indigo-600' : ''}`}
                        style={{
                          top,
                          height: height - 2,
                          left: `calc(${(p.column / p.columns) * 100}% + 2px)`,
                          width: `calc(${100 / p.columns}% - 4px)`,
                        }}
                        aria-label={`${session.patientName}, ${timeOf(session.startsAt)}, ${statusLabel[session.status]}${hasOverlap ? ', conflito de horário' : ''}`}
                      >
                        <div className="font-semibold">{timeOf(session.startsAt)}</div>
                        <div className="truncate">{session.patientName}</div>
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {sessionsQuery.isLoading && <p className="mt-4 text-sm text-slate-600">Carregando a agenda…</p>}

      {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length === 0 && (
        <p className="mt-4 rounded-lg bg-slate-100 px-4 py-3 text-sm text-slate-700">
          Nenhuma sessão nesta semana. Para preencher a agenda, abra um paciente e defina o horário recorrente.
        </p>
      )}

      {selected && <SessionPanel session={selected} all={sessions} onClose={() => setSelectedId(null)} />}
    </div>
  );
}

function SessionPanel({
  session,
  all,
  onClose,
}: {
  session: AgendaSession;
  all: AgendaSession[];
  onClose: () => void;
}) {
  const startDate = parseDateString(dateOf(session.startsAt));
  const conflicts = session.overlapsWith.map((id) => all.find((s) => s.id === id));

  return (
    <>
      <div className="fixed inset-0 z-10 bg-slate-900/30" onClick={onClose} aria-hidden="true" />
      <aside
        className="fixed inset-y-0 right-0 z-20 w-full max-w-sm overflow-y-auto bg-white p-5 shadow-xl"
        aria-label="Detalhes da sessão"
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 className="text-xl font-semibold text-slate-900">{session.patientName}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-3 py-1 text-slate-600 hover:bg-slate-100"
            aria-label="Fechar"
          >
            ✕
          </button>
        </div>

        <dl className="space-y-3 text-sm">
          <Row label="Data">
            {startDate ? `${weekdayFull(startDate)}, ${formatDateBR(dateOf(session.startsAt))}` : dateOf(session.startsAt)}
          </Row>
          <Row label="Horário">
            {timeOf(session.startsAt)} às {timeOf(session.endsAt)} ({session.durationMinutes} min)
          </Row>
          <Row label="Situação">
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusBadgeClasses[session.status]}`}>
              {statusLabel[session.status]}
            </span>
            {session.cancellationType && (
              <span className="ml-2 text-slate-600">{cancellationLabel[session.cancellationType]}</span>
            )}
          </Row>
          <Row label="Valor">
            {currency.format(session.amount)}
            {!session.billable && <span className="ml-2 text-slate-600">(não cobrada)</span>}
          </Row>
          {session.billable && <Row label="Pagamento">{paymentLabel[session.paymentStatus] ?? session.paymentStatus}</Row>}
        </dl>

        {conflicts.length > 0 && (
          <div className="mt-5 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
            <p className="font-medium">Conflito de horário com:</p>
            <ul className="mt-1 list-disc pl-5">
              {conflicts.map((c, i) => (
                <li key={c?.id ?? i}>{c ? `${c.patientName} (${timeOf(c.startsAt)})` : 'outra sessão'}</li>
              ))}
            </ul>
          </div>
        )}

        <Link
          to={`/patients/${session.patientId}`}
          className="mt-6 inline-block rounded-lg border border-indigo-600 px-4 py-2 text-indigo-700 hover:bg-indigo-50"
        >
          Abrir paciente
        </Link>
      </aside>
    </>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-slate-900">{children}</dd>
    </div>
  );
}

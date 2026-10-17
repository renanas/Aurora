import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import {
  addDays,
  dateOf,
  formatDayMonth,
  minutesOfDay,
  parseDateString,
  startOfWeek,
  timeOf,
  toDateString,
  weekdayShort,
} from '../../shared/lib/dates';
import { messageFor } from '../../shared/api/errors';
import { useSessions } from './api';
import { formatMinutes, minutesFromOffset } from './clickSlot';
import { NewSessionPanel, SessionPanel } from './SessionPanel';
import { statusBlockClasses, statusLabel } from './status';
import { hourRange, layoutDay } from './weekLayout';

// Mesmo valor do backend (SystemConstants): o gerador cria sessões até hoje + 8 semanas.
// Se mudar lá, mude aqui.
const GENERATION_HORIZON_WEEKS = 8;
const HOUR_PX = 64;

export default function AgendaPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState<{ date: string; time: string } | null>(null);

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

  // "Nova sessão": hoje, se hoje está na semana mostrada; senão, o primeiro dia dela.
  const defaultNewDate =
    todayString >= toDateString(weekStart) && todayString <= toDateString(weekEnd) ? todayString : toDateString(weekStart);

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
            onClick={() => {
              setSelectedId(null);
              setCreating({ date: defaultNewDate, time: '09:00' });
            }}
            className="rounded-lg border border-indigo-600 bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
          >
            Nova sessão
          </button>
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
                  className={`relative cursor-cell border-l border-slate-200 ${dayString === todayString ? 'bg-indigo-50/30' : ''}`}
                  style={{ height: gridHeight }}
                  onClick={(ev) => {
                    // Só o toque em espaço vazio conta; o toque numa sessão abre o painel dela.
                    if (ev.target !== ev.currentTarget) {
                      return;
                    }
                    const offset = ev.clientY - ev.currentTarget.getBoundingClientRect().top;
                    setSelectedId(null);
                    setCreating({ date: dayString, time: formatMinutes(minutesFromOffset(offset, HOUR_PX, startHour)) });
                  }}
                >
                  {hours.map((hour, i) => (
                    <div
                      key={hour}
                      className="pointer-events-none absolute inset-x-0 border-t border-slate-100"
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

      {selected && (
        <SessionPanel key={selected.id} session={selected} all={sessions} onClose={() => setSelectedId(null)} />
      )}

      {creating && (
        <NewSessionPanel initialDate={creating.date} initialTime={creating.time} onClose={() => setCreating(null)} />
      )}
    </div>
  );
}

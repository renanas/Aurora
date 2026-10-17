import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { dateOf, formatDateBR, parseDateString, timeOf, weekdayFull } from '../../shared/lib/dates';
import { messageFor } from '../../shared/api/errors';
import {
  useActivePatients,
  useCreateSession,
  useSessionAction,
  type AgendaSession,
  type SessionAction,
} from './api';
import { cancellationLabel, paymentLabel, statusBadgeClasses, statusLabel } from './status';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

type Mode = 'none' | 'cancel' | 'reschedule' | 'time';

export function SessionPanel({
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
  const [mode, setMode] = useState<Mode>('none');
  const mutation = useSessionAction();
  const busy = mutation.isPending;

  const canAct = session.status === 'SCHEDULED' || session.status === 'CONFIRMED';
  const canReopen = session.status === 'COMPLETED' || session.status === 'CANCELLED' || session.status === 'NO_SHOW';
  const canUndoReschedule = session.rescheduledFromId !== null && session.status === 'SCHEDULED';
  const movedTo =
    session.status === 'RESCHEDULED'
      ? all.find((s) => s.rescheduledFromId === session.id && s.status !== 'CANCELLED')
      : undefined;
  const isPaid = session.paymentStatus === 'PAID';
  const showPayment = session.billable || isPaid;

  /** Se a psicóloga desistir no aviso de conflito, a ação não acontece e nada fecha. */
  const run = (action: SessionAction, afterSuccess?: () => void) =>
    mutation.mutate(
      { id: session.id, action },
      {
        onSuccess: (result) => {
          if (result) {
            afterSuccess?.();
          }
        },
      },
    );

  const open = (next: Mode) => {
    mutation.reset();
    setMode(next);
  };

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
            {movedTo && (
              <span className="ml-2 text-slate-600">
                para {formatDateBR(dateOf(movedTo.startsAt))} às {timeOf(movedTo.startsAt)}
              </span>
            )}
            {session.rescheduledFromId && session.status !== 'RESCHEDULED' && (
              <span className="ml-2 text-slate-600">(veio de um reagendamento)</span>
            )}
          </Row>
          <Row label="Valor">
            {currency.format(session.amount)}
            {!session.billable && <span className="ml-2 text-slate-600">(não cobrada)</span>}
          </Row>
          {showPayment && (
            <Row label="Pagamento">
              {paymentLabel[session.paymentStatus] ?? session.paymentStatus}
              {isPaid && session.paidAt && (
                <span className="ml-2 text-slate-600">em {formatDateBR(dateOf(session.paidAt))}</span>
              )}
            </Row>
          )}
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

        {mutation.isError && (
          <p role="alert" className="mt-5 rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
            {messageFor(mutation.error)}
          </p>
        )}

        {canAct && mode === 'none' && (
          <div className="mt-6 space-y-2">
            {session.status === 'SCHEDULED' && (
              <ActionButton tone="primary" disabled={busy} onClick={() => run({ kind: 'confirm' })}>
                Confirmar sessão
              </ActionButton>
            )}
            <ActionButton disabled={busy} onClick={() => run({ kind: 'complete' })}>
              Marcar como realizada
            </ActionButton>
            <ActionButton disabled={busy} onClick={() => run({ kind: 'no-show' })}>
              Marcar falta
            </ActionButton>
            <ActionButton disabled={busy} onClick={() => open('reschedule')}>
              Reagendar
            </ActionButton>
            {session.status === 'SCHEDULED' && (
              <ActionButton disabled={busy} onClick={() => open('time')}>
                Corrigir horário
              </ActionButton>
            )}
            <ActionButton tone="danger" disabled={busy} onClick={() => open('cancel')}>
              Cancelar sessão
            </ActionButton>
          </div>
        )}

        {canAct && mode === 'cancel' && (
          <div className="mt-6 rounded-lg border border-slate-200 p-3">
            <p className="font-medium text-slate-900">Como foi o cancelamento?</p>
            <div className="mt-3 space-y-2">
              <ActionButton
                tone="danger"
                disabled={busy}
                onClick={() => run({ kind: 'cancel', cancellationType: 'EARLY' }, () => setMode('none'))}
              >
                {cancellationLabel.EARLY}
              </ActionButton>
              {isPaid && (
                <p className="text-sm text-amber-900">
                  Esta sessão já está paga e continua paga depois de cancelar. Se precisar devolver o valor, desfaça o
                  pagamento em seguida.
                </p>
              )}
              <ActionButton
                tone="danger"
                disabled={busy}
                onClick={() => run({ kind: 'cancel', cancellationType: 'LATE' }, () => setMode('none'))}
              >
                {cancellationLabel.LATE}
              </ActionButton>
              <ActionButton disabled={busy} onClick={() => open('none')}>
                Voltar
              </ActionButton>
            </div>
          </div>
        )}

        {canAct && mode === 'reschedule' && (
          <TimeForm
            title="Reagendar para"
            hint={
              isPaid
                ? 'O pagamento acompanha a nova sessão. A recorrência não muda.'
                : 'A sessão atual fica como reagendada e a nova entra na agenda. A recorrência não muda.'
            }
            submitLabel="Reagendar"
            initialDate={dateOf(session.startsAt)}
            initialTime={timeOf(session.startsAt)}
            busy={busy}
            onSubmit={(startsAt) => run({ kind: 'reschedule', startsAt }, onClose)}
            onBack={() => open('none')}
          />
        )}

        {canAct && mode === 'time' && (
          <TimeForm
            title="Corrigir horário"
            hint="Para consertar um erro de horário. Não cria sessão reagendada."
            submitLabel="Salvar horário"
            initialDate={dateOf(session.startsAt)}
            initialTime={timeOf(session.startsAt)}
            busy={busy}
            onSubmit={(startsAt) => run({ kind: 'time', startsAt }, onClose)}
            onBack={() => open('none')}
          />
        )}

        {canReopen && (
          <div className="mt-6 space-y-2">
            <ActionButton disabled={busy} onClick={() => run({ kind: 'reopen' })}>
              Reabrir sessão
            </ActionButton>
            <p className="text-sm text-slate-600">Volta a sessão para agendada. O pagamento não muda.</p>
          </div>
        )}

        {canUndoReschedule && mode === 'none' && (
          <div className="mt-6 space-y-2">
            <ActionButton disabled={busy} onClick={() => run({ kind: 'undo-reschedule' })}>
              Desfazer reagendamento
            </ActionButton>
            <p className="text-sm text-slate-600">
              A sessão original volta para o horário dela, com o pagamento, e esta é cancelada sem cobrança.
            </p>
          </div>
        )}

        {showPayment && mode === 'none' && (
          <div className="mt-6 border-t border-slate-200 pt-4">
            {isPaid ? (
              <ActionButton disabled={busy} onClick={() => run({ kind: 'payment', status: 'PENDING' })}>
                Desfazer pagamento
              </ActionButton>
            ) : (
              <ActionButton tone="primary" disabled={busy} onClick={() => run({ kind: 'payment', status: 'PAID' })}>
                Marcar como pago
              </ActionButton>
            )}
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

function TimeForm({
  title,
  hint,
  submitLabel,
  initialDate,
  initialTime,
  busy,
  onSubmit,
  onBack,
}: {
  title: string;
  hint: string;
  submitLabel: string;
  initialDate: string;
  initialTime: string;
  busy: boolean;
  onSubmit: (startsAt: string) => void;
  onBack: () => void;
}) {
  const [date, setDate] = useState(initialDate);
  const [time, setTime] = useState(initialTime);
  const ready = date !== '' && time !== '';

  return (
    <div className="mt-6 rounded-lg border border-slate-200 p-3">
      <p className="font-medium text-slate-900">{title}</p>
      <p className="mt-1 text-sm text-slate-600">{hint}</p>
      <DateTimeFields date={date} time={time} onDate={setDate} onTime={setTime} />
      <div className="mt-3 space-y-2">
        <ActionButton tone="primary" disabled={busy || !ready} onClick={() => onSubmit(`${date}T${time}`)}>
          {submitLabel}
        </ActionButton>
        <ActionButton disabled={busy} onClick={onBack}>
          Voltar
        </ActionButton>
      </div>
    </div>
  );
}

function DateTimeFields({
  date,
  time,
  onDate,
  onTime,
}: {
  date: string;
  time: string;
  onDate: (value: string) => void;
  onTime: (value: string) => void;
}) {
  const field = 'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900';
  return (
    <div className="mt-3 grid grid-cols-2 gap-3">
      <label className="text-sm text-slate-700">
        Data
        <input type="date" value={date} onChange={(ev) => onDate(ev.target.value)} className={field} />
      </label>
      <label className="text-sm text-slate-700">
        Horário
        <input type="time" value={time} onChange={(ev) => onTime(ev.target.value)} className={field} />
      </label>
    </div>
  );
}

/** Sessão avulsa: escolhe o paciente, a data e o horário. */
export function NewSessionPanel({
  initialDate,
  initialTime,
  onClose,
}: {
  initialDate: string;
  initialTime: string;
  onClose: () => void;
}) {
  const patients = useActivePatients();
  const create = useCreateSession();
  const [patientId, setPatientId] = useState('');
  const [date, setDate] = useState(initialDate);
  const [time, setTime] = useState(initialTime);
  const ready = patientId !== '' && date !== '' && time !== '';

  return (
    <>
      <div className="fixed inset-0 z-10 bg-slate-900/30" onClick={onClose} aria-hidden="true" />
      <aside
        className="fixed inset-y-0 right-0 z-20 w-full max-w-sm overflow-y-auto bg-white p-5 shadow-xl"
        aria-label="Nova sessão"
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 className="text-xl font-semibold text-slate-900">Nova sessão</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-3 py-1 text-slate-600 hover:bg-slate-100"
            aria-label="Fechar"
          >
            ✕
          </button>
        </div>
        <p className="text-sm text-slate-600">
          Sessão avulsa, fora do horário recorrente. O valor é o valor atual do paciente.
        </p>

        {patients.isError && (
          <p role="alert" className="mt-4 rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
            {messageFor(patients.error)}
          </p>
        )}

        {patients.data && patients.data.length === 0 && (
          <p className="mt-4 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">
            Nenhum paciente ativo. <Link to="/patients" className="text-indigo-700 underline">Cadastre um paciente</Link>{' '}
            primeiro.
          </p>
        )}

        <label className="mt-4 block text-sm text-slate-700">
          Paciente
          <select
            value={patientId}
            onChange={(ev) => setPatientId(ev.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900"
          >
            <option value="">Escolha o paciente</option>
            {(patients.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>

        <DateTimeFields date={date} time={time} onDate={setDate} onTime={setTime} />

        {create.isError && (
          <p role="alert" className="mt-4 rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
            {messageFor(create.error)}
          </p>
        )}

        <div className="mt-5 space-y-2">
          <ActionButton
            tone="primary"
            disabled={create.isPending || !ready}
            onClick={() =>
              create.mutate(
                { patientId, startsAt: `${date}T${time}` },
                {
                  onSuccess: (result) => {
                    if (result) {
                      onClose();
                    }
                  },
                },
              )
            }
          >
            Criar sessão
          </ActionButton>
          <ActionButton disabled={create.isPending} onClick={onClose}>
            Cancelar
          </ActionButton>
        </div>
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

const actionButtonTones = {
  primary: 'border-indigo-600 bg-indigo-600 text-white hover:bg-indigo-700',
  neutral: 'border-slate-300 bg-white text-slate-800 hover:bg-slate-50',
  danger: 'border-rose-300 bg-white text-rose-800 hover:bg-rose-50',
};

function ActionButton({
  tone = 'neutral',
  disabled,
  onClick,
  children,
}: {
  tone?: keyof typeof actionButtonTones;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`w-full rounded-lg border px-4 py-3 text-left font-medium disabled:cursor-not-allowed disabled:opacity-50 ${actionButtonTones[tone]}`}
    >
      {children}
    </button>
  );
}

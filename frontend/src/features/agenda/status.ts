import type { SessionStatus } from './api';

export const statusLabel: Record<SessionStatus, string> = {
  SCHEDULED: 'Agendada',
  CONFIRMED: 'Confirmada',
  COMPLETED: 'Realizada',
  CANCELLED: 'Cancelada',
  NO_SHOW: 'Faltou',
  RESCHEDULED: 'Reagendada',
};

/** Classes do bloco na grade. Escritas por extenso para o Tailwind encontrá-las. */
export const statusBlockClasses: Record<SessionStatus, string> = {
  SCHEDULED: 'bg-indigo-50 border-indigo-300 text-indigo-900',
  CONFIRMED: 'bg-indigo-100 border-indigo-500 text-indigo-900',
  COMPLETED: 'bg-emerald-50 border-emerald-400 text-emerald-900',
  CANCELLED: 'bg-slate-100 border-slate-300 text-slate-500 line-through',
  NO_SHOW: 'bg-rose-50 border-rose-300 text-rose-900',
  RESCHEDULED: 'bg-slate-100 border-dashed border-slate-300 text-slate-500',
};

/** Classes da etiqueta de status no painel. */
export const statusBadgeClasses: Record<SessionStatus, string> = {
  SCHEDULED: 'bg-indigo-100 text-indigo-800',
  CONFIRMED: 'bg-indigo-200 text-indigo-900',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-slate-200 text-slate-700',
  NO_SHOW: 'bg-rose-100 text-rose-800',
  RESCHEDULED: 'bg-slate-200 text-slate-700',
};

export const cancellationLabel: Record<string, string> = {
  EARLY: 'Com antecedência (sem cobrança)',
  LATE: 'Em cima da hora (com cobrança)',
};

export const paymentLabel: Record<string, string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
};

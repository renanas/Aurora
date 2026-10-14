// Datas "de calendário" (sem fuso), como o backend: "2026-09-28" e "2026-09-28T09:00".
// Regra: NUNCA usar toISOString() aqui; ele converte para UTC e pode mudar o dia.

const WEEKDAYS_SHORT = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const WEEKDAYS_FULL = [
  'domingo',
  'segunda-feira',
  'terça-feira',
  'quarta-feira',
  'quinta-feira',
  'sexta-feira',
  'sábado',
];

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** Data local como "2026-09-28". */
export function toDateString(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** "2026-09-28" → Date local à meia-noite; null se o formato ou a data forem inválidos (ex.: 2026-02-31). */
export function parseDateString(s: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const d = new Date(year, month - 1, day);
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null;
  return d;
}

export function addDays(d: Date, days: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + days);
}

/** Segunda-feira da semana de d (a semana da agenda começa na segunda). */
export function startOfWeek(d: Date): Date {
  const sinceMonday = (d.getDay() + 6) % 7;
  return addDays(d, -sinceMonday);
}

/** Parte da data de "2026-09-28T09:00". */
export function dateOf(dateTime: string): string {
  return dateTime.slice(0, 10);
}

/** Parte da hora de "2026-09-28T09:00" → "09:00". */
export function timeOf(dateTime: string): string {
  return dateTime.slice(11, 16);
}

/** Minutos desde a meia-noite de "2026-09-28T09:30" → 570. */
export function minutesOfDay(dateTime: string): number {
  const time = timeOf(dateTime);
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

export function weekdayShort(d: Date): string {
  return WEEKDAYS_SHORT[d.getDay()];
}

export function weekdayFull(d: Date): string {
  return WEEKDAYS_FULL[d.getDay()];
}

/** "28/09" */
export function formatDayMonth(d: Date): string {
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;
}

/** "2026-09-28" → "28/09/2026" (devolve o texto original se for inválido). */
export function formatDateBR(s: string): string {
  const d = parseDateString(s);
  if (!d) return s;
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

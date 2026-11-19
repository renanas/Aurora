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

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Data local como "2026-09-28". */
export function toDateString(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** "2026-09-28" → Date local à meia-noite; null se o formato ou a data forem inválidos (ex.: 2026-02-31). */
export function parseDateString(dateText: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateText);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return date;
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** Segunda-feira da semana de d (a semana da agenda começa na segunda). */
export function startOfWeek(date: Date): Date {
  const sinceMonday = (date.getDay() + 6) % 7;
  return addDays(date, -sinceMonday);
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

export function weekdayShort(date: Date): string {
  return WEEKDAYS_SHORT[date.getDay()];
}

export function weekdayFull(date: Date): string {
  return WEEKDAYS_FULL[date.getDay()];
}

/** "28/09" */
export function formatDayMonth(date: Date): string {
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

/** "2026-09-28" → "28/09/2026" (devolve o texto original se for inválido). */
export function formatDateBR(dateText: string): string {
  const date = parseDateString(dateText);
  if (!date) return dateText;
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

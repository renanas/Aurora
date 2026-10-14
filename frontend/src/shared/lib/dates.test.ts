import { describe, expect, it } from 'vitest';
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
} from './dates';

describe('toDateString / parseDateString', () => {
  it('formata a data local, sem passar por UTC', () => {
    // 23:30 local: com toISOString() em fusos negativos isto viraria o dia seguinte
    expect(toDateString(new Date(2026, 8, 28, 23, 30))).toBe('2026-09-28');
    expect(toDateString(new Date(2026, 0, 5, 0, 5))).toBe('2026-01-05');
  });

  it('faz o caminho de ida e volta', () => {
    expect(toDateString(parseDateString('2026-09-28')!)).toBe('2026-09-28');
  });

  it('rejeita datas inexistentes e formatos errados', () => {
    expect(parseDateString('2026-02-31')).toBeNull();
    expect(parseDateString('2026-13-01')).toBeNull();
    expect(parseDateString('2026-9-28')).toBeNull();
    expect(parseDateString('28/09/2026')).toBeNull();
    expect(parseDateString('')).toBeNull();
  });

  it('aceita 29/02 só em ano bissexto', () => {
    expect(parseDateString('2028-02-29')).not.toBeNull();
    expect(parseDateString('2027-02-29')).toBeNull();
  });
});

describe('addDays', () => {
  it('atravessa fim de mês e de ano', () => {
    expect(toDateString(addDays(new Date(2026, 8, 30), 1))).toBe('2026-10-01');
    expect(toDateString(addDays(new Date(2026, 11, 31), 1))).toBe('2027-01-01');
    expect(toDateString(addDays(new Date(2026, 0, 1), -1))).toBe('2025-12-31');
  });
});

describe('startOfWeek', () => {
  it('devolve a segunda-feira', () => {
    expect(toDateString(startOfWeek(new Date(2026, 8, 28)))).toBe('2026-09-28'); // segunda
    expect(toDateString(startOfWeek(new Date(2026, 8, 30)))).toBe('2026-09-28'); // quarta
    expect(toDateString(startOfWeek(new Date(2026, 9, 4)))).toBe('2026-09-28'); // domingo
  });
});

describe('texto de data e hora', () => {
  it('separa data e hora', () => {
    expect(dateOf('2026-09-28T09:00')).toBe('2026-09-28');
    expect(timeOf('2026-09-28T09:00')).toBe('09:00');
  });

  it('calcula minutos desde a meia-noite', () => {
    expect(minutesOfDay('2026-09-28T00:00')).toBe(0);
    expect(minutesOfDay('2026-09-28T09:30')).toBe(570);
    expect(minutesOfDay('2026-09-28T23:59')).toBe(1439);
  });

  it('formata em português', () => {
    const monday = new Date(2026, 8, 28);
    expect(weekdayShort(monday)).toBe('seg');
    expect(weekdayFull(monday)).toBe('segunda-feira');
    expect(formatDayMonth(monday)).toBe('28/09');
    expect(formatDateBR('2026-09-28')).toBe('28/09/2026');
    expect(formatDateBR('lixo')).toBe('lixo');
  });
});

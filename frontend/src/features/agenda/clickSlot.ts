/**
 * Toque em espaço vazio da agenda -> horário de início da nova sessão.
 * offsetPx: distância do topo da coluna do dia; startHour: hora que a grade mostra no topo.
 * O resultado é arredondado para baixo, de 15 em 15 minutos, e fica entre 00:00 e 23:45.
 */
export function minutesFromOffset(offsetPx: number, hourPx: number, startHour: number, stepMinutes = 15): number {
  const raw = startHour * 60 + (offsetPx / hourPx) * 60
  const snapped = Math.floor(raw / stepMinutes) * stepMinutes
  return Math.min(Math.max(snapped, 0), 24 * 60 - stepMinutes)
}

/** 570 -> "09:30" */
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

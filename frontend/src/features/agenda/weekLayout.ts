// Posiciona as sessões de um dia na grade. Sessões que se sobrepõem ficam lado a lado.
// Sessões que apenas encostam (fim de uma = início da outra) NÃO são sobreposição.

export interface DayItem {
  id: string;
  startMin: number;
  endMin: number;
}

export interface PositionedItem extends DayItem {
  /** Coluna (0, 1, …) dentro do grupo de sessões que se sobrepõem. */
  column: number;
  /** Quantas colunas o grupo tem; a largura de cada sessão é 1/columns. */
  columns: number;
}

export function layoutDay(items: DayItem[]): PositionedItem[] {
  const sorted = [...items].sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);
  const result: PositionedItem[] = [];

  let cluster: DayItem[] = [];
  let clusterEnd = -1;

  const flush = () => {
    if (cluster.length === 0) return;
    const columnEnds: number[] = []; // fim da última sessão de cada coluna
    const placed = cluster.map((item) => {
      let column = columnEnds.findIndex((end) => end <= item.startMin);
      if (column === -1) {
        column = columnEnds.length;
        columnEnds.push(item.endMin);
      } else {
        columnEnds[column] = item.endMin;
      }
      return { item, column };
    });
    for (const { item, column } of placed) {
      result.push({ ...item, column, columns: columnEnds.length });
    }
    cluster = [];
  };

  for (const item of sorted) {
    if (cluster.length > 0 && item.startMin >= clusterEnd) {
      flush();
      clusterEnd = -1;
    }
    cluster.push(item);
    clusterEnd = Math.max(clusterEnd, item.endMin);
  }
  flush();

  return result;
}

/** Faixa de horas exibida: padrão 8–20 h, ampliada para caber todas as sessões. */
export function hourRange(
  items: Array<{ startMin: number; endMin: number }>,
  defaultStart = 8,
  defaultEnd = 20,
): { startHour: number; endHour: number } {
  let startHour = defaultStart;
  let endHour = defaultEnd;
  for (const item of items) {
    startHour = Math.min(startHour, Math.floor(item.startMin / 60));
    endHour = Math.max(endHour, Math.ceil(item.endMin / 60));
  }
  return { startHour: Math.max(0, startHour), endHour: Math.min(24, endHour) };
}

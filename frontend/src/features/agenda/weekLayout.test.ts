import { describe, expect, it } from 'vitest';
import { hourRange, layoutDay } from './weekLayout';

const at = (id: string, startHour: number, endHour: number) => ({
  id,
  startMin: startHour * 60,
  endMin: endHour * 60,
});

const byId = (items: ReturnType<typeof layoutDay>, id: string) => items.find((i) => i.id === id)!;

describe('layoutDay', () => {
  it('sessão sozinha ocupa a largura toda', () => {
    const [a] = layoutDay([at('a', 9, 10)]);
    expect(a.column).toBe(0);
    expect(a.columns).toBe(1);
  });

  it('sessões que se sobrepõem ficam lado a lado', () => {
    const out = layoutDay([at('a', 9, 10), { id: 'b', startMin: 9 * 60 + 30, endMin: 10 * 60 + 30 }]);
    expect(byId(out, 'a').column).toBe(0);
    expect(byId(out, 'b').column).toBe(1);
    expect(byId(out, 'a').columns).toBe(2);
    expect(byId(out, 'b').columns).toBe(2);
  });

  it('sessões que só encostam não são sobreposição', () => {
    const out = layoutDay([at('a', 9, 10), at('b', 10, 11)]);
    expect(byId(out, 'a').columns).toBe(1);
    expect(byId(out, 'b').columns).toBe(1);
    expect(byId(out, 'b').column).toBe(0);
  });

  it('dois grupos separados têm larguras independentes', () => {
    const out = layoutDay([at('a', 8, 9), at('b', 8, 9), at('c', 14, 15)]);
    expect(byId(out, 'a').columns).toBe(2);
    expect(byId(out, 'c').columns).toBe(1);
  });

  it('reaproveita a coluna livre dentro do grupo', () => {
    // a (9–11) sobrepõe b (9–10) e c (10–11); b e c só encostam, então dividem a mesma coluna
    const out = layoutDay([at('a', 9, 11), at('b', 9, 10), at('c', 10, 11)]);
    expect(byId(out, 'b').column).toBe(byId(out, 'c').column);
    expect(byId(out, 'a').column === byId(out, 'b').column).toBe(false);
    expect(out.every((i) => i.columns === 2)).toBe(true);
  });

  it('não depende da ordem de entrada', () => {
    const out = layoutDay([at('b', 9, 10), at('a', 9, 10)]);
    expect(out.length).toBe(2);
    expect(out.map((i) => i.column).sort()).toEqual([0, 1]);
  });

  it('lista vazia', () => {
    expect(layoutDay([])).toEqual([]);
  });
});

describe('hourRange', () => {
  it('usa 8–20 h por padrão', () => {
    expect(hourRange([])).toEqual({ startHour: 8, endHour: 20 });
    expect(hourRange([at('a', 9, 10)])).toEqual({ startHour: 8, endHour: 20 });
  });

  it('amplia para caber sessões cedo e tarde', () => {
    expect(hourRange([{ startMin: 6 * 60 + 30, endMin: 7 * 60 + 20 }])).toEqual({ startHour: 6, endHour: 20 });
    expect(hourRange([{ startMin: 20 * 60, endMin: 20 * 60 + 50 }])).toEqual({ startHour: 8, endHour: 21 });
  });
});

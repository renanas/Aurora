import { describe, expect, it } from 'vitest'
import { readInitialSchedule } from './initialSchedule'

describe('readInitialSchedule', () => {
  it('sem data e sem horário, o paciente é criado sem horário', () => {
    expect(readInitialSchedule({ anchorDate: '', startTime: '', frequency: 'WEEKLY' })).toEqual({ kind: 'none' })
    expect(readInitialSchedule({ anchorDate: ' ', startTime: ' ', frequency: 'BIWEEKLY' })).toEqual({ kind: 'none' })
  })

  it('com data e horário, monta o horário recorrente', () => {
    expect(readInitialSchedule({ anchorDate: '2026-11-23', startTime: '09:00', frequency: 'BIWEEKLY' })).toEqual({
      kind: 'ok',
      input: { anchorDate: '2026-11-23', startTime: '09:00', frequency: 'BIWEEKLY' },
    })
  })

  it('só o horário: pede a data', () => {
    const choice = readInitialSchedule({ anchorDate: '', startTime: '09:00', frequency: 'WEEKLY' })
    expect(choice.kind).toBe('invalid')
    expect(choice.kind === 'invalid' && choice.field).toBe('anchorDate')
  })

  it('só a data: pede o horário', () => {
    const choice = readInitialSchedule({ anchorDate: '2026-11-23', startTime: '', frequency: 'WEEKLY' })
    expect(choice.kind === 'invalid' && choice.field).toBe('startTime')
  })

  it('data impossível é recusada', () => {
    const choice = readInitialSchedule({ anchorDate: '2026-02-31', startTime: '09:00', frequency: 'WEEKLY' })
    expect(choice.kind === 'invalid' && choice.field).toBe('anchorDate')
  })
})

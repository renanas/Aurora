import { describe, expect, it, vi } from 'vitest'
import { ApiError, type Conflict } from './client'
import { isOverlapError, withOverlapConfirmation } from './overlap'

const conflict: Conflict = {
  sessionId: 's1',
  patientName: 'Maria',
  startsAt: '2026-09-28T09:00',
  endsAt: '2026-09-28T09:50',
}

const overlapError = () => new ApiError(409, 'OVERLAP_DETECTED', 'conflito', [], [conflict])

describe('isOverlapError', () => {
  it('reconhece só o 409 OVERLAP_DETECTED', () => {
    expect(isOverlapError(overlapError())).toBe(true)
    expect(isOverlapError(new ApiError(409, 'CONCURRENT_MODIFICATION', 'x'))).toBe(false)
    expect(isOverlapError(new ApiError(422, 'OVERLAP_DETECTED', 'x'))).toBe(false)
    expect(isOverlapError(new Error('x'))).toBe(false)
  })
})

describe('withOverlapConfirmation', () => {
  it('sem conflito: envia uma vez, sem confirmar, e não pergunta nada', async () => {
    const send = vi.fn().mockResolvedValue('ok')
    const ask = vi.fn()

    await expect(withOverlapConfirmation(send, ask)).resolves.toBe('ok')

    expect(send).toHaveBeenCalledTimes(1)
    expect(send).toHaveBeenCalledWith(false)
    expect(ask).not.toHaveBeenCalled()
  })

  it('com conflito e confirmação: pergunta com a lista e reenvia com confirmOverlap = true', async () => {
    const send = vi.fn().mockRejectedValueOnce(overlapError()).mockResolvedValueOnce('ok')
    const ask = vi.fn().mockResolvedValue(true)

    await expect(withOverlapConfirmation(send, ask)).resolves.toBe('ok')

    expect(ask).toHaveBeenCalledWith([conflict])
    expect(send).toHaveBeenNthCalledWith(1, false)
    expect(send).toHaveBeenNthCalledWith(2, true)
  })

  it('com conflito e desistência: não reenvia e devolve undefined', async () => {
    const send = vi.fn().mockRejectedValue(overlapError())

    await expect(withOverlapConfirmation(send, () => false)).resolves.toBeUndefined()

    expect(send).toHaveBeenCalledTimes(1)
  })

  it('outro erro segue adiante sem perguntar', async () => {
    const failure = new ApiError(422, 'INVALID_STATUS_TRANSITION', 'x')
    const send = vi.fn().mockRejectedValue(failure)
    const ask = vi.fn()

    await expect(withOverlapConfirmation(send, ask)).rejects.toBe(failure)

    expect(ask).not.toHaveBeenCalled()
  })
})

import { describe, expect, it, vi } from 'vitest'
import { ensureCsrfToken } from './csrf'

describe('ensureCsrfToken', () => {
  it('não faz nada quando o cookie já existe', async () => {
    const prime = vi.fn().mockResolvedValue(undefined)
    await ensureCsrfToken(() => 'a=1; XSRF-TOKEN=abc', prime)
    expect(prime).not.toHaveBeenCalled()
  })

  it('faz a chamada de preparo quando o cookie não existe', async () => {
    const prime = vi.fn().mockResolvedValue(undefined)
    await ensureCsrfToken(() => '', prime)
    expect(prime).toHaveBeenCalledTimes(1)
  })

  it('trata cookie vazio como ausente', async () => {
    const prime = vi.fn().mockResolvedValue(undefined)
    await ensureCsrfToken(() => 'XSRF-TOKEN=', prime)
    expect(prime).toHaveBeenCalledTimes(1)
  })

  it('ignora o erro da chamada de preparo (401 é esperado)', async () => {
    const prime = vi.fn().mockRejectedValue(new Error('401'))
    await expect(ensureCsrfToken(() => '', prime)).resolves.toBeUndefined()
  })
})

import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../shared/api/client'
import { messageFor } from '../../shared/api/errors'
import { addDays, formatDateBR, parseDateString, toDateString, weekdayFull } from '../../shared/lib/dates'
import {
  describeRecurrence,
  useEndRecurrence,
  useRecurrence,
  useReplaceRecurrence,
  type Frequency,
  type RecurrenceInput,
} from './recurrence'

interface FormValues {
  anchorDate: string
  startTime: string
  frequency: Frequency
}

const inputClass = 'w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-lg'
const errorClass = 'mt-1 text-base text-red-600'

interface Props {
  patientId: string
  /** Paciente inativo não tem horário recorrente e não edita. */
  patientActive: boolean
}

export default function RecurrenceSection({ patientId, patientActive }: Props) {
  const recurrenceQuery = useRecurrence(patientId)
  const replace = useReplaceRecurrence(patientId)
  const end = useEndRecurrence(patientId)

  const [editing, setEditing] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { anchorDate: '', startTime: '', frequency: 'WEEKLY' } })

  const recurrence = recurrenceQuery.data
  const chosenDate = parseDateString(watch('anchorDate') ?? '')

  function startEditing() {
    setFormError(null)
    if (recurrence) {
      // Trocar o horário vale a partir de amanhã, por padrão.
      reset({
        anchorDate: toDateString(addDays(new Date(), 1)),
        startTime: recurrence.startTime,
        frequency: recurrence.frequency,
      })
    } else {
      reset({ anchorDate: '', startTime: '', frequency: 'WEEKLY' })
    }
    setEditing(true)
  }

  async function onSubmit(values: FormValues) {
    setFormError(null)
    const input: RecurrenceInput = {
      anchorDate: values.anchorDate,
      startTime: values.startTime,
      frequency: values.frequency,
    }
    try {
      await replace.mutateAsync(input)
      setEditing(false)
    } catch (error) {
      if (error instanceof ApiError) {
        for (const fe of error.fieldErrors) {
          if (fe.field === 'anchorDate' || fe.field === 'startTime' || fe.field === 'frequency') {
            setError(fe.field, { message: fe.message })
          }
        }
      }
      setFormError(messageFor(error))
    }
  }

  async function onEnd() {
    if (
      !window.confirm(
        'Encerrar o horário recorrente? As sessões futuras que ainda não foram alteradas saem da agenda.',
      )
    ) {
      return
    }
    setFormError(null)
    try {
      await end.mutateAsync()
      setEditing(false)
    } catch (error) {
      setFormError(messageFor(error))
    }
  }

  const heading = <h2 className="text-xl font-semibold">Horário recorrente</h2>

  if (recurrenceQuery.isPending) {
    return (
      <section className="space-y-3 rounded-lg bg-white p-4">
        {heading}
        <p className="text-slate-500">Carregando…</p>
      </section>
    )
  }
  if (recurrenceQuery.error) {
    return (
      <section className="space-y-3 rounded-lg bg-white p-4">
        {heading}
        <p className="rounded-lg bg-red-50 p-4 text-red-700">{messageFor(recurrenceQuery.error)}</p>
      </section>
    )
  }

  if (!patientActive) {
    return (
      <section className="space-y-3 rounded-lg bg-white p-4">
        {heading}
        <p className="text-slate-500">Paciente inativo. Reative-o para definir um horário recorrente.</p>
      </section>
    )
  }

  const showForm = editing || !recurrence
  const startsLater = recurrence !== null && recurrence !== undefined && recurrence.anchorDate > toDateString(new Date())

  return (
    <section className="space-y-4 rounded-lg bg-white p-4">
      {heading}

      {recurrence && !editing && (
        <div className="space-y-3">
          <p className="text-lg text-slate-900">{describeRecurrence(recurrence)}</p>
          {startsLater && (
            <p className="text-base text-slate-600">Começa em {formatDateBR(recurrence.anchorDate)}.</p>
          )}
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={startEditing}
              className="rounded-lg border border-slate-300 bg-white px-5 py-3 text-lg text-slate-700"
            >
              Alterar
            </button>
            <button
              type="button"
              onClick={onEnd}
              disabled={end.isPending}
              className="rounded-lg border border-slate-300 bg-white px-5 py-3 text-lg text-slate-700 disabled:opacity-50"
            >
              Encerrar
            </button>
          </div>
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {!recurrence && <p className="text-slate-600">Este paciente ainda não tem horário recorrente.</p>}

          <div>
            <label htmlFor="anchorDate" className="mb-1 block text-base font-medium">
              {recurrence ? 'A partir de' : 'Primeira sessão em'}
            </label>
            <input
              id="anchorDate"
              type="date"
              className={inputClass}
              {...register('anchorDate', {
                required: 'Informe a data.',
                validate: (v) => parseDateString(v) !== null || 'Data inválida.',
              })}
            />
            {chosenDate && <p className="mt-1 text-base text-slate-600">Cai em {weekdayFull(chosenDate)}.</p>}
            {errors.anchorDate && <p className={errorClass}>{errors.anchorDate.message}</p>}
          </div>

          <div>
            <label htmlFor="startTime" className="mb-1 block text-base font-medium">
              Horário
            </label>
            <input
              id="startTime"
              type="time"
              step={300}
              className={inputClass}
              {...register('startTime', { required: 'Informe o horário.' })}
            />
            {errors.startTime && <p className={errorClass}>{errors.startTime.message}</p>}
          </div>

          <div>
            <label htmlFor="frequency" className="mb-1 block text-base font-medium">
              Frequência
            </label>
            <select id="frequency" className={inputClass} {...register('frequency')}>
              <option value="WEEKLY">Semanal</option>
              <option value="BIWEEKLY">Quinzenal</option>
            </select>
          </div>

          {formError && <p className="rounded-lg bg-red-50 p-4 text-red-700">{formError}</p>}

          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-lg bg-indigo-600 px-6 py-3 text-lg font-medium text-white active:bg-indigo-700 disabled:opacity-50"
            >
              Salvar horário
            </button>
            {recurrence && (
              <button
                type="button"
                onClick={() => {
                  setEditing(false)
                  setFormError(null)
                }}
                className="rounded-lg border border-slate-300 bg-white px-6 py-3 text-lg"
              >
                Cancelar
              </button>
            )}
          </div>
        </form>
      )}

      {formError && !showForm && <p className="rounded-lg bg-red-50 p-4 text-red-700">{formError}</p>}
    </section>
  )
}

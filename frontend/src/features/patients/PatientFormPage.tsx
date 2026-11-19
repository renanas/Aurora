import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError, api } from '../../shared/api/client'
import { messageFor } from '../../shared/api/errors'
import { parseDateString, weekdayFull } from '../../shared/lib/dates'
import { countDigits, formatPhone, parsePrice, priceToInput } from '../../shared/lib/format'
import {
  useActivatePatient,
  useCreatePatient,
  useDeactivatePatient,
  usePatient,
  useUpdatePatient,
  type PatientInput,
} from './api'
import { readInitialSchedule, type ScheduleFields } from './initialSchedule'
import { RECURRENCE_KEY, type Recurrence } from './recurrence'
import RecurrenceSection from './RecurrenceSection'

interface FormValues extends ScheduleFields {
  name: string
  whatsapp: string
  price: string
}

/** Aviso passado para a tela do paciente quando ele foi criado mas o horário não pôde ser salvo. */
interface NavigationState {
  notice?: string
}

const inputClass = 'w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-lg'
const errorClass = 'mt-1 text-base text-red-600'

/** Serve para "novo paciente" (/patients/new) e para "detalhes/edição" (/patients/:id). */
export default function PatientFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const notice = (location.state as NavigationState | null)?.notice ?? null

  const patientQuery = usePatient(id)
  const createPatient = useCreatePatient()
  const updatePatient = useUpdatePatient()
  const deactivatePatient = useDeactivatePatient()
  const activatePatient = useActivatePatient()

  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: { name: '', whatsapp: '', price: '', anchorDate: '', startTime: '', frequency: 'WEEKLY' },
  })
  const chosenDate = parseDateString(watch('anchorDate') ?? '')

  const patient = patientQuery.data
  useEffect(() => {
    if (patient) {
      reset({
        name: patient.name,
        whatsapp: formatPhone(patient.whatsapp),
        price: priceToInput(patient.sessionPrice),
        anchorDate: '',
        startTime: '',
        frequency: 'WEEKLY',
      })
    }
  }, [patient, reset])

  async function onSubmit(values: FormValues) {
    setFormError(null)

    const price = parsePrice(values.price)
    if (price === null) {
      setError('price', { message: 'Use apenas números, por exemplo 180 ou 180,50.' })
      return
    }

    // O horário só existe no cadastro (na edição ele tem seção própria) e é opcional.
    const schedule = isEdit ? ({ kind: 'none' } as const) : readInitialSchedule(values)
    if (schedule.kind === 'invalid') {
      setError(schedule.field, { message: schedule.message })
      return
    }

    const input: PatientInput = { name: values.name.trim(), whatsapp: values.whatsapp, sessionPrice: price }

    let created: { id: string } | null = null
    try {
      if (isEdit) {
        await updatePatient.mutateAsync({ id, input })
      } else {
        created = await createPatient.mutateAsync(input)
      }
    } catch (error) {
      showServerError(error)
      return
    }

    if (created && schedule.kind === 'ok') {
      try {
        await api<Recurrence>(`/patients/${created.id}/recurrence`, { method: 'PUT', body: schedule.input })
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: RECURRENCE_KEY }),
          queryClient.invalidateQueries({ queryKey: ['sessions'] }),
        ])
      } catch (error) {
        // O paciente já existe: abre a tela dele, onde dá para tentar o horário de novo.
        navigate(`/patients/${created.id}`, {
          replace: true,
          state: { notice: `Paciente salvo, mas o horário não: ${messageFor(error)} Defina o horário abaixo.` },
        })
        return
      }
    }
    navigate('/patients')
  }

  function showServerError(error: unknown) {
    if (error instanceof ApiError && error.fieldErrors.length > 0) {
      const fieldMap: Record<string, keyof FormValues> = { name: 'name', whatsapp: 'whatsapp', sessionPrice: 'price' }
      for (const fieldError of error.fieldErrors) {
        const field = fieldMap[fieldError.field]
        if (field) {
          setError(field, { message: fieldError.message })
        }
      }
    }
    setFormError(messageFor(error))
  }

  async function toggleActive() {
    if (!patient) return
    setFormError(null)
    try {
      if (patient.active) {
        if (
          !window.confirm(
            'Inativar este paciente? Ele sai da lista de ativos, o horário recorrente é encerrado e o histórico é mantido.',
          )
        )
          return
        await deactivatePatient.mutateAsync(patient.id)
      } else {
        await activatePatient.mutateAsync(patient.id)
      }
    } catch (error) {
      showServerError(error)
    }
  }

  if (isEdit && patientQuery.isPending) {
    return <p className="text-slate-500">Carregando…</p>
  }
  if (isEdit && patientQuery.error) {
    return (
      <div className="space-y-4">
        <p className="rounded-lg bg-red-50 p-4 text-red-700">{messageFor(patientQuery.error)}</p>
        <Link to="/patients" className="text-indigo-700 underline">
          Voltar para a lista
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{isEdit ? 'Paciente' : 'Novo paciente'}</h1>
        {patient && !patient.active && (
          <span className="rounded-full bg-slate-200 px-3 py-1 text-base text-slate-700">Inativo</span>
        )}
      </div>

      {notice && <p className="rounded-lg bg-amber-50 p-4 text-amber-800">{notice}</p>}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <div>
          <label htmlFor="name" className="mb-1 block text-base font-medium">
            Nome
          </label>
          <input
            id="name"
            className={inputClass}
            autoComplete="off"
            {...register('name', {
              required: 'Informe o nome.',
              maxLength: { value: 150, message: 'No máximo 150 caracteres.' },
            })}
          />
          {errors.name && <p className={errorClass}>{errors.name.message}</p>}
        </div>

        <div>
          <label htmlFor="whatsapp" className="mb-1 block text-base font-medium">
            WhatsApp
          </label>
          <input
            id="whatsapp"
            inputMode="tel"
            placeholder="(11) 99999-8888"
            className={inputClass}
            autoComplete="off"
            {...register('whatsapp', {
              required: 'Informe o WhatsApp.',
              validate: (value) => {
                const digitCount = countDigits(value)
                return (digitCount >= 10 && digitCount <= 15) || 'O WhatsApp deve ter de 10 a 15 dígitos.'
              },
            })}
          />
          {errors.whatsapp && <p className={errorClass}>{errors.whatsapp.message}</p>}
        </div>

        <div>
          <label htmlFor="price" className="mb-1 block text-base font-medium">
            Valor da sessão (R$)
          </label>
          <input
            id="price"
            inputMode="decimal"
            placeholder="180,00"
            className={inputClass}
            autoComplete="off"
            {...register('price', { required: 'Informe o valor da sessão.' })}
          />
          {errors.price && <p className={errorClass}>{errors.price.message}</p>}
        </div>

        {!isEdit && (
          <fieldset className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
            <legend className="px-1 text-lg font-semibold">Horário na agenda (opcional)</legend>
            <p className="text-base text-slate-600">
              Deixe em branco para definir depois. Com data e horário, as sessões já aparecem na agenda.
            </p>

            <div>
              <label htmlFor="anchorDate" className="mb-1 block text-base font-medium">
                Primeira sessão em
              </label>
              <input id="anchorDate" type="date" className={inputClass} {...register('anchorDate')} />
              {chosenDate && <p className="mt-1 text-base text-slate-600">Cai em {weekdayFull(chosenDate)}.</p>}
              {errors.anchorDate && <p className={errorClass}>{errors.anchorDate.message}</p>}
            </div>

            <div>
              <label htmlFor="startTime" className="mb-1 block text-base font-medium">
                Horário
              </label>
              <input id="startTime" type="time" step={300} className={inputClass} {...register('startTime')} />
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
          </fieldset>
        )}

        {formError && <p className="rounded-lg bg-red-50 p-4 text-red-700">{formError}</p>}

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-lg bg-indigo-600 px-6 py-3 text-lg font-medium text-white active:bg-indigo-700 disabled:opacity-50"
          >
            Salvar
          </button>
          <Link to="/patients" className="rounded-lg border border-slate-300 bg-white px-6 py-3 text-lg">
            Cancelar
          </Link>
          {patient && (
            <button
              type="button"
              onClick={toggleActive}
              className="ml-auto rounded-lg border border-slate-300 bg-white px-5 py-3 text-lg text-slate-700"
            >
              {patient.active ? 'Inativar' : 'Reativar'}
            </button>
          )}
        </div>
      </form>

      {patient && <RecurrenceSection patientId={patient.id} patientActive={patient.active} />}
    </div>
  )
}

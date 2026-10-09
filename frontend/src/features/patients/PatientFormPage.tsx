import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/api/client'
import { messageFor } from '../../shared/api/errors'
import { countDigits, formatPhone, parsePrice, priceToInput } from '../../shared/lib/format'
import {
  useActivatePatient,
  useCreatePatient,
  useDeactivatePatient,
  usePatient,
  useUpdatePatient,
  type PatientInput,
} from './api'

interface FormValues {
  name: string
  whatsapp: string
  price: string
}

const inputClass = 'w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-lg'
const errorClass = 'mt-1 text-base text-red-600'

/** Serve para "novo paciente" (/patients/new) e para "detalhes/edição" (/patients/:id). */
export default function PatientFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()

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
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { name: '', whatsapp: '', price: '' } })

  const patient = patientQuery.data
  useEffect(() => {
    if (patient) {
      reset({
        name: patient.name,
        whatsapp: formatPhone(patient.whatsapp),
        price: priceToInput(patient.sessionPrice),
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

    const input: PatientInput = { name: values.name.trim(), whatsapp: values.whatsapp, sessionPrice: price }

    try {
      if (isEdit) {
        await updatePatient.mutateAsync({ id, input })
      } else {
        await createPatient.mutateAsync(input)
      }
      navigate('/patients')
    } catch (error) {
      showServerError(error)
    }
  }

  function showServerError(error: unknown) {
    if (error instanceof ApiError && error.fieldErrors.length > 0) {
      const fieldMap: Record<string, keyof FormValues> = { name: 'name', whatsapp: 'whatsapp', sessionPrice: 'price' }
      for (const fe of error.fieldErrors) {
        const field = fieldMap[fe.field]
        if (field) {
          setError(field, { message: fe.message })
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
        if (!window.confirm('Inativar este paciente? Ele sai da lista de ativos, e o histórico é mantido.')) return
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
              validate: (v) => {
                const n = countDigits(v)
                return (n >= 10 && n <= 15) || 'O WhatsApp deve ter de 10 a 15 dígitos.'
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

      {isEdit && (
        <p className="rounded-lg bg-white p-4 text-slate-500">
          Horário recorrente e próximas sessões aparecem aqui na próxima entrega.
        </p>
      )}
    </div>
  )
}

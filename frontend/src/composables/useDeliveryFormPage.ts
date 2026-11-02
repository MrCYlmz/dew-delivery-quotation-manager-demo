import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ApiError,
  createDelivery,
  deleteDelivery,
  deletePdf,
  errorMessage,
  getDelivery,
  parseExcel,
  updateDelivery,
  uploadExcel,
  uploadPdfs,
} from '../api/client'
import {
  DELIVERY_STATUS_LABELS,
  type DeliveryDetail,
  type DeliveryPdf,
  type DeliveryStatus,
  type DeliveryWriteBody,
  type ParsedPackingList,
  type TransportMethod,
} from '../api/types'
import { arrivalRequired, shippingDateRequired } from '../status'
import { citySuggestions, companySuggestions, invalidateSuggestions } from './useSuggestions'

interface FormModel {
  customer_company: string
  origin_country: string
  origin_city: string
  origin_postal_code: string
  origin_street: string
  destination_country: string
  destination_city: string
  destination_postal_code: string
  destination_street: string
  transport_method: TransportMethod | ''
  status: DeliveryStatus
  shipping_date: string
  expected_arrival_date: string
  arrival_date: string
  offer_price_eur: string
  cost_eur: string
  volume_m3: string
  gross_weight_kg: string
  pallet_count: string
  notes: string
}

type CargoTotalKey = 'volume_m3' | 'gross_weight_kg' | 'pallet_count'

function emptyForm(): FormModel {
  return {
    customer_company: '',
    origin_country: '',
    origin_city: '',
    origin_postal_code: '',
    origin_street: '',
    destination_country: '',
    destination_city: '',
    destination_postal_code: '',
    destination_street: '',
    transport_method: '',
    status: 'TO_DO',
    shipping_date: '',
    expected_arrival_date: '',
    arrival_date: '',
    offer_price_eur: '',
    cost_eur: '',
    volume_m3: '',
    gross_weight_kg: '',
    pallet_count: '',
    notes: '',
  }
}

function numberToInput(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

function formFrom(detail: DeliveryDetail): FormModel {
  return {
    customer_company: detail.customer_company,
    origin_country: detail.origin_country,
    origin_city: detail.origin_city,
    origin_postal_code: detail.origin_postal_code ?? '',
    origin_street: detail.origin_street ?? '',
    destination_country: detail.destination_country,
    destination_city: detail.destination_city,
    destination_postal_code: detail.destination_postal_code ?? '',
    destination_street: detail.destination_street ?? '',
    transport_method: detail.transport_method,
    status: detail.status,
    shipping_date: detail.shipping_date ?? '',
    expected_arrival_date: detail.expected_arrival_date ?? '',
    arrival_date: detail.arrival_date ?? '',
    offer_price_eur: numberToInput(detail.offer_price_eur),
    cost_eur: numberToInput(detail.cost_eur),
    volume_m3: numberToInput(detail.volume_m3),
    gross_weight_kg: numberToInput(detail.gross_weight_kg),
    pallet_count: numberToInput(detail.pallet_count),
    notes: detail.notes ?? '',
  }
}

function parseNumber(raw: string): number | null | 'invalid' {
  const text = raw.trim().replace(',', '.')
  if (!text) return null
  const value = Number(text)
  if (!Number.isFinite(value)) return 'invalid'
  return value
}

const REQUIRED: (keyof FormModel)[] = [
  'customer_company',
  'origin_country',
  'origin_city',
  'destination_country',
  'destination_city',
  'transport_method',
]

const NUMERIC: [keyof FormModel, string][] = [
  ['offer_price_eur', 'Offer price'],
  ['cost_eur', 'Real price'],
  ['volume_m3', 'Volume'],
  ['gross_weight_kg', 'Gross weight'],
  ['pallet_count', 'Pallet count'],
]

function validate(
  form: FormModel,
): { body: DeliveryWriteBody; errors: null } | { body: null; errors: Record<string, string> } {
  const errors: Record<string, string> = {}
  for (const key of REQUIRED) {
    if (!form[key].trim()) errors[key] = 'Required'
  }
  const statusLabel = DELIVERY_STATUS_LABELS[form.status]
  if (shippingDateRequired(form.status) && !form.shipping_date) {
    errors.shipping_date = `Shipping date is required at ${statusLabel}.`
  }
  if (arrivalRequired(form.status) && !form.arrival_date) {
    errors.arrival_date = `Arrival is required at ${statusLabel}.`
  }

  const parsed: Partial<Record<keyof FormModel, number | null>> = {}
  for (const [key, label] of NUMERIC) {
    const value = parseNumber(form[key])
    if (value === 'invalid') errors[key] = `${label} must be a number`
    else if (value !== null && value < 0) errors[key] = `${label} cannot be negative`
    else parsed[key] = value
  }

  if (form.arrival_date && form.shipping_date && form.arrival_date < form.shipping_date) {
    errors.arrival_date = 'Arrival cannot precede shipping'
  }

  if (Object.keys(errors).length > 0 || form.transport_method === '') return { body: null, errors }

  return {
    errors: null,
    body: {
      customer_company: form.customer_company.trim(),
      origin_country: form.origin_country.trim(),
      origin_city: form.origin_city.trim(),
      origin_postal_code: form.origin_postal_code.trim() || null,
      origin_street: form.origin_street.trim() || null,
      destination_country: form.destination_country.trim(),
      destination_city: form.destination_city.trim(),
      destination_postal_code: form.destination_postal_code.trim() || null,
      destination_street: form.destination_street.trim() || null,
      transport_method: form.transport_method,
      status: form.status,
      shipping_date: form.shipping_date || null,
      expected_arrival_date: form.expected_arrival_date || null,
      arrival_date: form.arrival_date || null,
      offer_price_eur: parsed.offer_price_eur ?? null,
      cost_eur: parsed.cost_eur ?? null,
      volume_m3: parsed.volume_m3 ?? null,
      gross_weight_kg: parsed.gross_weight_kg ?? null,
      pallet_count: parsed.pallet_count ?? null,
      notes: form.notes.trim() ? form.notes : null,
    },
  }
}

export function useDeliveryFormPage() {
  const route = useRoute()
  const router = useRouter()

  const deliveryId = computed<number | null>(() => {
    const raw = route.params.id
    const value = Array.isArray(raw) ? raw[0] : raw
    return value ? Number(value) : null
  })
  const isEdit = computed(() => deliveryId.value !== null)

  const form = ref<FormModel>(emptyForm())
  const fieldErrors = ref<Record<string, string>>({})
  const loading = ref(false)
  const saving = ref(false)
  const loadError = ref<string | null>(null)
  const saveError = ref<string | null>(null)
  const confirmingDelete = ref(false)
  const existing = ref<DeliveryDetail | null>(null)

  const excelFile = ref<File | null>(null)
  const parsing = ref(false)
  const parseError = ref<string | null>(null)
  const parsed = ref<ParsedPackingList | null>(null)

  const queuedPdfs = ref<File[]>([])
  const removingPdfId = ref<number | null>(null)
  const pdfError = ref<string | null>(null)

  const companies = companySuggestions()
  const originCities = computed(() => citySuggestions('origin', form.value.origin_country).value)
  const destinationCities = computed(
    () => citySuggestions('destination', form.value.destination_country).value,
  )

  let latestLoad = 0
  let latestParse = 0

  function resetTransient() {
    fieldErrors.value = {}
    saveError.value = null
    loadError.value = null
    confirmingDelete.value = false
    clearPackingList()
    queuedPdfs.value = []
    removingPdfId.value = null
    pdfError.value = null
  }

  async function load() {
    const request = ++latestLoad
    resetTransient()
    const id = deliveryId.value
    if (id === null) {
      existing.value = null
      form.value = emptyForm()
      loading.value = false
      return
    }
    loading.value = true
    try {
      const detail = await getDelivery(id)
      if (request !== latestLoad) return
      existing.value = detail
      form.value = formFrom(detail)
    } catch (err) {
      if (request !== latestLoad) return
      loadError.value = errorMessage(err)
    } finally {
      if (request === latestLoad) loading.value = false
    }
  }

  watch(
    () => route.fullPath,
    () => {
      if (route.name === 'delivery-new' || route.name === 'delivery-edit') void load()
    },
    { immediate: true },
  )

  async function pickPackingList(file: File) {
    const request = ++latestParse
    excelFile.value = file
    parsed.value = null
    parseError.value = null
    parsing.value = true
    try {
      const result = await parseExcel(file)
      if (request !== latestParse) return
      parsed.value = result
      form.value.volume_m3 = numberToInput(result.total_volume_m3)
      form.value.gross_weight_kg = numberToInput(result.total_gross_weight_kg)
      form.value.pallet_count = numberToInput(result.total_pallet_count)
    } catch (err) {
      if (request !== latestParse) return
      excelFile.value = null
      parseError.value = errorMessage(err)
    } finally {
      if (request === latestParse) parsing.value = false
    }
  }

  function clearPackingList() {
    latestParse++
    excelFile.value = null
    parsed.value = null
    parseError.value = null
    parsing.value = false
  }

  function parsedHint(field: CargoTotalKey): string | null {
    const result = parsed.value
    if (!result) return null
    const calculated =
      field === 'volume_m3'
        ? result.total_volume_m3
        : field === 'gross_weight_kg'
          ? result.total_gross_weight_kg
          : result.total_pallet_count
    const typed = parseNumber(form.value[field])
    const overridden = typeof typed === 'number' && typed !== calculated
    return `Parsed from the Excel: ${calculated}${overridden ? ' — you have overridden it' : ''}`
  }

  const parsedHints = computed(() => ({
    volume_m3: parsedHint('volume_m3'),
    gross_weight_kg: parsedHint('gross_weight_kg'),
    pallet_count: parsedHint('pallet_count'),
  }))

  async function save() {
    saveError.value = null
    const { body, errors } = validate(form.value)
    if (!body) {
      fieldErrors.value = errors
      saveError.value = 'Some fields need attention — see the messages below.'
      return
    }
    fieldErrors.value = {}

    saving.value = true
    try {
      const id = deliveryId.value
      const saved = id === null ? await createDelivery(body) : await updateDelivery(id, body)

      if (excelFile.value) {
        await uploadExcel(saved.id, excelFile.value)
      }

      if (queuedPdfs.value.length > 0) {
        await uploadPdfs(saved.id, queuedPdfs.value)
      }

      invalidateSuggestions()
      await router.push({ name: 'delivery-detail', params: { id: saved.id } })
    } catch (err) {
      if (err instanceof ApiError && err.fields) fieldErrors.value = err.fields
      saveError.value = errorMessage(err)
    } finally {
      saving.value = false
    }
  }

  async function deleteRecord() {
    const id = deliveryId.value
    if (id === null) return
    saving.value = true
    saveError.value = null
    try {
      await deleteDelivery(id)
      invalidateSuggestions()
      await router.push({ name: 'deliveries' })
    } catch (err) {
      saveError.value = errorMessage(err)
      confirmingDelete.value = false
    } finally {
      saving.value = false
    }
  }

  async function removePdf(pdf: DeliveryPdf) {
    const record = existing.value
    if (!record) return
    const load = latestLoad
    pdfError.value = null
    removingPdfId.value = pdf.id
    try {
      await deletePdf(record.id, pdf.id)
      if (load !== latestLoad || !existing.value) return
      existing.value = {
        ...existing.value,
        pdfs: existing.value.pdfs.filter((p) => p.id !== pdf.id),
      }
    } catch (err) {
      if (load !== latestLoad) return
      pdfError.value = `${pdf.original_filename} could not be removed. ${errorMessage(err)}`
    } finally {
      if (load === latestLoad) removingPdfId.value = null
    }
  }

  function dismissSaveError() {
    saveError.value = null
  }

  function cancel() {
    const id = deliveryId.value
    if (id !== null) {
      void router.push({ name: 'delivery-detail', params: { id } })
    } else {
      void router.push({ name: 'deliveries' })
    }
  }

  return {
    deliveryId,
    isEdit,
    form,
    fieldErrors,
    loading,
    saving,
    loadError,
    saveError,
    confirmingDelete,
    existing,
    excelFile,
    parsing,
    parseError,
    parsed,
    parsedHints,
    queuedPdfs,
    removingPdfId,
    pdfError,
    companies,
    originCities,
    destinationCities,
    load,
    save,
    deleteRecord,
    pickPackingList,
    clearPackingList,
    removePdf,
    dismissSaveError,
    cancel,
  }
}

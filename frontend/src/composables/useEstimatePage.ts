import { computed, onScopeDispose, ref, watch } from 'vue'
import { errorMessage, getEstimateComparables, parseExcel } from '../api/client'
import type {
  EstimateComparables,
  EstimateComparablesQuery,
  ParsedPackingList,
  TransportMethod,
} from '../api/types'
import { buildEstimatePrompt, type CopyStatus, type EstimateStatus } from '../estimate'

interface EstimateForm {
  transport_method: TransportMethod | ''
  origin_country: string
  origin_city: string
  destination_country: string
  destination_city: string
  planned_shipping_date: string
  pallet_count: string
  volume_m3: string
  gross_weight_kg: string
}

type CargoTotalKey = 'volume_m3' | 'gross_weight_kg' | 'pallet_count'

const CARGO_TOTALS: [CargoTotalKey, string][] = [
  ['volume_m3', 'Volume'],
  ['gross_weight_kg', 'Gross weight'],
  ['pallet_count', 'Pallets'],
]

function emptyForm(): EstimateForm {
  return {
    transport_method: '',
    origin_country: '',
    origin_city: '',
    destination_country: '',
    destination_city: '',
    planned_shipping_date: '',
    pallet_count: '',
    volume_m3: '',
    gross_weight_kg: '',
  }
}

function optionalParam(value: number | null): string | undefined {
  return value === null ? undefined : String(value)
}

export function useEstimatePage() {
  const form = ref<EstimateForm>(emptyForm())

  const found = ref<EstimateComparables | null>(null)
  const finding = ref(false)
  const findError = ref<string | null>(null)

  const excelFile = ref<File | null>(null)
  const parsing = ref(false)
  const parseError = ref<string | null>(null)
  const parsed = ref<ParsedPackingList | null>(null)

  const editedPrompt = ref<string | null>(null)
  const promptEditedFrom = ref('')
  const copied = ref<{ text: string; outcome: CopyStatus } | null>(null)

  let latestFind = 0
  let lookedUpQuery = ''
  let typingPause: ReturnType<typeof setTimeout> | undefined
  let latestParse = 0

  // A total of 0 or left empty counts as not given (F-19).
  const cargo = computed(() => {
    const values: Record<CargoTotalKey, number | null> = {
      volume_m3: null,
      gross_weight_kg: null,
      pallet_count: null,
    }
    const errors: Record<string, string> = {}
    for (const [key, label] of CARGO_TOTALS) {
      const text = form.value[key].trim().replace(',', '.')
      if (!text) continue
      const value = Number(text)
      if (!Number.isFinite(value)) errors[key] = `${label} must be a number`
      else if (value < 0) errors[key] = `${label} cannot be negative`
      else if (value > 0) values[key] = value
    }
    return { values, errors }
  })

  const fieldErrors = computed(() => cargo.value.errors)
  const cargoValid = computed(() => Object.keys(cargo.value.errors).length === 0)

  const requiredSet = computed(
    () =>
      form.value.transport_method !== '' &&
      form.value.origin_country !== '' &&
      form.value.destination_country !== '',
  )

  const query = computed<EstimateComparablesQuery | null>(() => {
    const { transport_method, origin_country, destination_country } = form.value
    if (transport_method === '' || !origin_country || !destination_country || !cargoValid.value) {
      return null
    }
    return {
      transport_method,
      origin_country,
      destination_country,
      volume_m3: optionalParam(cargo.value.values.volume_m3),
      gross_weight_kg: optionalParam(cargo.value.values.gross_weight_kg),
    }
  })

  async function findComparables() {
    const request = ++latestFind
    clearTimeout(typingPause)
    found.value = null
    findError.value = null
    const current = query.value
    lookedUpQuery = JSON.stringify(current)
    if (!current) {
      finding.value = false
      return
    }
    finding.value = true
    try {
      const result = await getEstimateComparables(current)
      if (request !== latestFind) return
      found.value = result
    } catch (err) {
      if (request !== latestFind) return
      findError.value = `Comparable deliveries could not be loaded. ${errorMessage(err)}`
    } finally {
      if (request === latestFind) finding.value = false
    }
  }

  watch(
    [
      () => form.value.transport_method,
      () => form.value.origin_country,
      () => form.value.destination_country,
      cargoValid,
    ],
    () => void findComparables(),
  )

  // Volume and weight are typed digit by digit, so the lookup waits until typing pauses.
  // The pause restarts on every keystroke, even one like "13." that leaves the number unchanged.
  watch([() => form.value.volume_m3, () => form.value.gross_weight_kg], () => {
    clearTimeout(typingPause)
    typingPause = setTimeout(() => {
      if (JSON.stringify(query.value) !== lookedUpQuery) void findComparables()
    }, 300)
  })

  onScopeDispose(() => clearTimeout(typingPause))

  const status = computed<EstimateStatus>(() => {
    if (!requiredSet.value) return 'incomplete'
    if (!cargoValid.value) return 'invalid'
    if (finding.value) return 'loading'
    if (findError.value) return 'failed'
    if (!found.value || found.value.comparables.length === 0) return 'not-enough'
    return 'found'
  })

  const generatedPrompt = computed(() => {
    const result = found.value
    const method = form.value.transport_method
    if (status.value !== 'found' || !result || method === '') return ''
    return buildEstimatePrompt(
      {
        transport_method: method,
        origin_country: form.value.origin_country,
        origin_city: form.value.origin_city,
        destination_country: form.value.destination_country,
        destination_city: form.value.destination_city,
        planned_shipping_date: form.value.planned_shipping_date,
        ...cargo.value.values,
      },
      result,
    )
  })

  const promptText = computed({
    get: () => editedPrompt.value ?? generatedPrompt.value,
    set: (text: string) => {
      if (editedPrompt.value === null) promptEditedFrom.value = generatedPrompt.value
      editedPrompt.value = text
    },
  })

  const promptEdited = computed(() => editedPrompt.value !== null)
  const promptOutOfDate = computed(
    () => promptEdited.value && generatedPrompt.value !== promptEditedFrom.value,
  )

  function rebuildPrompt() {
    editedPrompt.value = null
    promptEditedFrom.value = ''
  }

  // The confirmation belongs to the text that was copied, so any rebuild, edit or new lookup clears it.
  const copyStatus = computed(() =>
    status.value === 'found' && copied.value?.text === promptText.value ? copied.value.outcome : null,
  )

  async function copyPrompt() {
    const text = promptText.value
    try {
      await navigator.clipboard.writeText(text)
      copied.value = { text, outcome: 'copied' }
    } catch {
      copied.value = { text, outcome: 'blocked' }
    }
  }

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
      form.value.volume_m3 = String(result.total_volume_m3)
      form.value.gross_weight_kg = String(result.total_gross_weight_kg)
      form.value.pallet_count = String(result.total_pallet_count)
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
    const typed = cargo.value.values[field]
    const overridden = typed !== null && typed !== calculated
    return `Parsed from the Excel: ${calculated}${overridden ? ' — you have overridden it' : ''}`
  }

  const parsedHints = computed(() => ({
    volume_m3: parsedHint('volume_m3'),
    gross_weight_kg: parsedHint('gross_weight_kg'),
    pallet_count: parsedHint('pallet_count'),
  }))

  return {
    form,
    fieldErrors,
    status,
    found,
    findError,
    excelFile,
    parsing,
    parseError,
    parsed,
    parsedHints,
    promptText,
    promptEdited,
    promptOutOfDate,
    copyStatus,
    findComparables,
    pickPackingList,
    clearPackingList,
    rebuildPrompt,
    copyPrompt,
  }
}

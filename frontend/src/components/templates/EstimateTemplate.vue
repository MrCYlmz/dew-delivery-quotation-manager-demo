<script setup lang="ts">
import ErrorBanner from '../molecules/ErrorBanner.vue'
import CargoTotalsFields from '../organisms/CargoTotalsFields.vue'
import ComparableDeliveries from '../organisms/ComparableDeliveries.vue'
import EstimatePrompt from '../organisms/EstimatePrompt.vue'
import ExcelUpload from '../organisms/ExcelUpload.vue'
import PlaceFields from '../organisms/PlaceFields.vue'
import PotentialDeliveryFields from '../organisms/PotentialDeliveryFields.vue'
import type { EstimateComparables, ParsedPackingList, TransportMethod } from '../../api/types'
import type { CopyStatus, EstimateStatus } from '../../estimate'

defineProps<{
  fieldErrors: Record<string, string>
  status: EstimateStatus
  found: EstimateComparables | null
  findError: string | null
  excelFile: File | null
  parsing: boolean
  parseError: string | null
  parsed: ParsedPackingList | null
  parsedHints: { volume_m3: string | null; gross_weight_kg: string | null; pallet_count: string | null }
  promptEdited: boolean
  promptOutOfDate: boolean
  copyStatus: CopyStatus | null
}>()

const transportMethod = defineModel<TransportMethod | ''>('transportMethod', { required: true })
const plannedShippingDate = defineModel<string>('plannedShippingDate', { required: true })
const originCountry = defineModel<string>('originCountry', { required: true })
const originCity = defineModel<string>('originCity', { required: true })
const destinationCountry = defineModel<string>('destinationCountry', { required: true })
const destinationCity = defineModel<string>('destinationCity', { required: true })
const volume = defineModel<string>('volume', { required: true })
const grossWeight = defineModel<string>('grossWeight', { required: true })
const pallets = defineModel<string>('pallets', { required: true })
const promptText = defineModel<string>('promptText', { required: true })

const emit = defineEmits<{
  retry: []
  'pick-packing-list': [file: File]
  'clear-packing-list': []
  'rebuild-prompt': []
  'copy-prompt': []
}>()
</script>

<template>
  <div class="row-between page-head">
    <h1>Estimate</h1>
  </div>
  <p class="intro muted">
    Describe a potential delivery. The app picks the most similar past deliveries and drafts a
    prompt you can paste into Claude. Nothing on this screen is saved.
  </p>

  <form
    novalidate
    @submit.prevent
  >
    <PotentialDeliveryFields
      v-model:transport-method="transportMethod"
      v-model:planned-shipping-date="plannedShippingDate"
    />

    <div class="two-col">
      <PlaceFields
        v-model:country="originCountry"
        v-model:city="originCity"
        side="origin"
        title="Origin"
      />
      <PlaceFields
        v-model:country="destinationCountry"
        v-model:city="destinationCity"
        side="destination"
        title="Destination"
      />
    </div>

    <ExcelUpload
      :existing-filename="null"
      :delivery-id="null"
      :file="excelFile"
      :parsing="parsing"
      :parse-error="parseError"
      :parsed="parsed"
      preview-only
      @pick="emit('pick-packing-list', $event)"
      @clear="emit('clear-packing-list')"
    />

    <CargoTotalsFields
      v-model:volume="volume"
      v-model:gross-weight="grossWeight"
      v-model:pallets="pallets"
      :field-errors="fieldErrors"
      :parsed-hints="parsedHints"
    />
  </form>

  <ErrorBanner
    v-if="findError"
    :message="findError"
    @retry="emit('retry')"
  />

  <div class="results">
    <ComparableDeliveries
      :status="status"
      :found="found"
      :transport-method="transportMethod"
      :origin-country="originCountry"
      :destination-country="destinationCountry"
    />
    <EstimatePrompt
      v-model:text="promptText"
      :status="status"
      :removed-company-names="found?.removed_company_names ?? 0"
      :edited="promptEdited"
      :out-of-date="promptOutOfDate"
      :copy-status="copyStatus"
      @rebuild="emit('rebuild-prompt')"
      @copy="emit('copy-prompt')"
    />
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.9rem;
}

.intro {
  margin: -0.4rem 0 1rem;
  max-width: 70ch;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0 1.1rem;
}

.results {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 0 1.1rem;
  align-items: start;
}

@media (max-width: 1100px) {
  .results {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

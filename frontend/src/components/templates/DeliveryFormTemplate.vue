<script setup lang="ts">
import ErrorBanner from '../molecules/ErrorBanner.vue'
import AddressFields from '../organisms/AddressFields.vue'
import CargoTotalsFields from '../organisms/CargoTotalsFields.vue'
import DeliveryFields from '../organisms/DeliveryFields.vue'
import DeliveryFormActions from '../organisms/DeliveryFormActions.vue'
import ExcelUpload from '../organisms/ExcelUpload.vue'
import NotesField from '../organisms/NotesField.vue'
import PdfAttachments from '../organisms/PdfAttachments.vue'
import type {
  DeliveryDetail,
  DeliveryPdf,
  DeliveryStatus,
  ParsedPackingList,
  TransportMethod,
} from '../../api/types'

defineProps<{
  isEdit: boolean
  deliveryId: number | null
  existing: DeliveryDetail | null
  loading: boolean
  loadError: string | null
  saving: boolean
  saveError: string | null
  fieldErrors: Record<string, string>
  companies: string[]
  originCities: string[]
  destinationCities: string[]
  excelFile: File | null
  parsing: boolean
  parseError: string | null
  parsed: ParsedPackingList | null
  parsedHints: { volume_m3: string | null; gross_weight_kg: string | null; pallet_count: string | null }
  removingPdfId: number | null
  pdfError: string | null
}>()

const customerCompany = defineModel<string>('customerCompany', { required: true })
const status = defineModel<DeliveryStatus>('status', { required: true })
const transportMethod = defineModel<TransportMethod | ''>('transportMethod', { required: true })
const shippingDate = defineModel<string>('shippingDate', { required: true })
const expectedArrivalDate = defineModel<string>('expectedArrivalDate', { required: true })
const arrivalDate = defineModel<string>('arrivalDate', { required: true })
const offerPriceEur = defineModel<string>('offerPriceEur', { required: true })
const costEur = defineModel<string>('costEur', { required: true })
const originCountry = defineModel<string>('originCountry', { required: true })
const originCity = defineModel<string>('originCity', { required: true })
const originPostalCode = defineModel<string>('originPostalCode', { required: true })
const originStreet = defineModel<string>('originStreet', { required: true })
const destinationCountry = defineModel<string>('destinationCountry', { required: true })
const destinationCity = defineModel<string>('destinationCity', { required: true })
const destinationPostalCode = defineModel<string>('destinationPostalCode', { required: true })
const destinationStreet = defineModel<string>('destinationStreet', { required: true })
const notes = defineModel<string>('notes', { required: true })
const volume = defineModel<string>('volume', { required: true })
const grossWeight = defineModel<string>('grossWeight', { required: true })
const pallets = defineModel<string>('pallets', { required: true })
const queuedPdfs = defineModel<File[]>('queuedPdfs', { required: true })
const confirmingDelete = defineModel<boolean>('confirmingDelete', { required: true })

const emit = defineEmits<{
  retry: []
  submit: []
  cancel: []
  delete: []
  'dismiss-save-error': []
  'pick-packing-list': [file: File]
  'clear-packing-list': []
  'remove-pdf': [pdf: DeliveryPdf]
}>()
</script>

<template>
  <div class="row-between page-head">
    <h1>{{ isEdit ? 'Edit delivery' : 'New delivery' }}</h1>
    <RouterLink to="/deliveries">Back to list</RouterLink>
  </div>

  <ErrorBanner
    v-if="loadError"
    :message="loadError"
    @retry="emit('retry')"
  />
  <p
    v-if="loading"
    class="spinner-text"
  >Loading delivery…</p>

  <form
    v-else
    novalidate
    @submit.prevent="emit('submit')"
  >
    <DeliveryFields
      v-model:customer-company="customerCompany"
      v-model:status="status"
      v-model:transport-method="transportMethod"
      v-model:shipping-date="shippingDate"
      v-model:expected-arrival-date="expectedArrivalDate"
      v-model:arrival-date="arrivalDate"
      v-model:offer-price-eur="offerPriceEur"
      v-model:cost-eur="costEur"
      :field-errors="fieldErrors"
      :companies="companies"
    />

    <div class="two-col">
      <AddressFields
        v-model:country="originCountry"
        v-model:city="originCity"
        v-model:postal-code="originPostalCode"
        v-model:street="originStreet"
        side="origin"
        title="Origin"
        :field-errors="fieldErrors"
        :cities="originCities"
      />
      <AddressFields
        v-model:country="destinationCountry"
        v-model:city="destinationCity"
        v-model:postal-code="destinationPostalCode"
        v-model:street="destinationStreet"
        side="destination"
        title="Destination"
        :field-errors="fieldErrors"
        :cities="destinationCities"
      />
    </div>

    <NotesField v-model="notes" />

    <ExcelUpload
      :existing-filename="existing?.excel_filename ?? null"
      :delivery-id="deliveryId"
      :file="excelFile"
      :parsing="parsing"
      :parse-error="parseError"
      :parsed="parsed"
      :disabled="saving"
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

    <PdfAttachments
      v-model:queued="queuedPdfs"
      :attached="existing?.pdfs ?? []"
      :removing-id="removingPdfId"
      :error="pdfError"
      :disabled="saving"
      @remove="emit('remove-pdf', $event)"
    />

    <ErrorBanner
      v-if="saveError"
      :message="saveError"
      retry-label="Dismiss"
      @retry="emit('dismiss-save-error')"
    />

    <DeliveryFormActions
      v-model:confirming-delete="confirmingDelete"
      :is-edit="isEdit"
      :saving="saving"
      @cancel="emit('cancel')"
      @delete="emit('delete')"
    />
  </form>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.9rem;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0 1.1rem;
}
</style>

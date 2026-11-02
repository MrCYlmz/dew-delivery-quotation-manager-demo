<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import ErrorBanner from '../molecules/ErrorBanner.vue'
import FormField from '../molecules/FormField.vue'
import ModalDialog from '../molecules/ModalDialog.vue'
import { DELIVERY_STATUS_LABELS, type DeliveryStatus } from '../../api/types'

const props = defineProps<{
  company: string
  status: DeliveryStatus
  askShipping: boolean
  currentExpectedArrivalDate: string | null
  currentArrivalDate: string | null
  fieldErrors: Record<string, string>
  error: string | null
  saving: boolean
}>()

const emit = defineEmits<{
  confirm: [dates: { shippingDate: string; expectedArrivalDate: string; arrivalDate: string }]
  cancel: []
  close: []
  'dismiss-error': []
}>()

const id = useId()
const shippingDate = ref('')
const expectedArrivalDate = ref(props.currentExpectedArrivalDate ?? '')
const arrivalDate = ref(props.currentArrivalDate ?? props.currentExpectedArrivalDate ?? '')

const title = computed(() => `Move to ${DELIVERY_STATUS_LABELS[props.status]}`)
const delivered = computed(() => props.status === 'DELIVERED')

const lead = computed(() => {
  if (!delivered.value) {
    return `${props.company} needs a shipping date once it is on the way. Add the expected arrival if the carrier gave one.`
  }
  if (props.askShipping) return `Confirm the day ${props.company} arrived, and when it was shipped.`
  if (props.currentArrivalDate) {
    return `Confirm the day ${props.company} arrived, or correct it if it was not that day.`
  }
  if (props.currentExpectedArrivalDate) {
    return `Confirm the day ${props.company} arrived, or correct it if it was not the expected day.`
  }
  return `Enter the day ${props.company} arrived.`
})

const arrivalHint = computed(() => {
  if (props.currentArrivalDate) return 'Filled in with the arrival already recorded.'
  if (props.currentExpectedArrivalDate) return 'Filled in with the expected arrival.'
  return null
})

// Closing while saving would hide the outcome of a move that still goes through.
function cancel() {
  if (!props.saving) emit('cancel')
}
</script>

<template>
  <ModalDialog
    :title="title"
    @cancel="cancel"
    @close="emit('close')"
  >
    <p class="lead">{{ lead }}</p>
    <form
      novalidate
      @submit.prevent="emit('confirm', { shippingDate, expectedArrivalDate, arrivalDate })"
    >
      <ErrorBanner
        v-if="error"
        :message="error"
        retry-label="Dismiss"
        @retry="emit('dismiss-error')"
      />
      <FormField
        v-if="askShipping"
        :id="`${id}-shipping`"
        v-slot="{ describedBy }"
        label="Shipping date"
        required
        :error="fieldErrors.shipping_date"
      >
        <input
          :id="`${id}-shipping`"
          v-model="shippingDate"
          type="date"
          :aria-invalid="fieldErrors.shipping_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
      <FormField
        v-if="!delivered"
        :id="`${id}-expected`"
        v-slot="{ describedBy }"
        label="Expected arrival"
        :error="fieldErrors.expected_arrival_date"
        hint="Optional. Leave it empty if the carrier has not given one yet."
      >
        <input
          :id="`${id}-expected`"
          v-model="expectedArrivalDate"
          type="date"
          :aria-invalid="fieldErrors.expected_arrival_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
      <FormField
        v-else
        :id="`${id}-arrival`"
        v-slot="{ describedBy }"
        label="Arrival"
        required
        :error="fieldErrors.arrival_date"
        :hint="arrivalHint"
      >
        <input
          :id="`${id}-arrival`"
          v-model="arrivalDate"
          type="date"
          :aria-invalid="fieldErrors.arrival_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
      <div class="dialog-actions">
        <button
          type="button"
          :disabled="saving"
          @click="emit('cancel')"
        >Cancel</button>
        <button
          type="submit"
          class="primary"
          :disabled="saving"
        >{{ title }}</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.lead {
  margin: 0 0 0.9rem;
  font-size: 0.9rem;
  color: var(--text-muted);
}

.field + .field {
  margin-top: 0.7rem;
}

.dialog-actions {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
  margin-top: 1.1rem;
}
</style>

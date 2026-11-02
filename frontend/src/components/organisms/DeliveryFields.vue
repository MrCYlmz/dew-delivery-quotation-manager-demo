<script setup lang="ts">
import { computed, useId } from 'vue'
import FieldGrid from '../atoms/FieldGrid.vue'
import AutocompleteInput from '../molecules/AutocompleteInput.vue'
import FormField from '../molecules/FormField.vue'
import { arrivalRequired, offerPriceExpected, shippingDateRequired } from '../../status'
import {
  DELIVERY_STATUSES,
  DELIVERY_STATUS_LABELS,
  TRANSPORT_METHODS,
  TRANSPORT_METHOD_LABELS,
  type DeliveryStatus,
  type TransportMethod,
} from '../../api/types'

defineProps<{
  fieldErrors: Record<string, string>
  companies: string[]
}>()

const customerCompany = defineModel<string>('customerCompany', { required: true })
const status = defineModel<DeliveryStatus>('status', { required: true })
const transportMethod = defineModel<TransportMethod | ''>('transportMethod', { required: true })
const shippingDate = defineModel<string>('shippingDate', { required: true })
const expectedArrivalDate = defineModel<string>('expectedArrivalDate', { required: true })
const arrivalDate = defineModel<string>('arrivalDate', { required: true })
const offerPriceEur = defineModel<string>('offerPriceEur', { required: true })
const costEur = defineModel<string>('costEur', { required: true })

const headingId = useId()

const offerPriceWarning = computed(() =>
  offerPriceExpected(status.value) && !offerPriceEur.value.trim()
    ? 'No offer price yet. Add the accepted offer to compare it with the real price later.'
    : null,
)
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Delivery</h3>
    <FieldGrid>
      <FormField
        id="customer_company"
        v-slot="{ describedBy }"
        class="wide"
        label="Customer company"
        required
        :error="fieldErrors.customer_company"
      >
        <AutocompleteInput
          id="customer_company"
          v-model="customerCompany"
          :options="companies"
          :invalid="!!fieldErrors.customer_company"
          :described-by="describedBy"
          placeholder="The European company requesting the delivery"
          autocomplete-hint="New company — it will join the list after saving."
        />
      </FormField>

      <FormField
        id="status"
        v-slot="{ describedBy }"
        label="Status"
        required
        :error="fieldErrors.status"
      >
        <select
          id="status"
          v-model="status"
          :aria-invalid="fieldErrors.status ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
          <option
            v-for="option in DELIVERY_STATUSES"
            :key="option"
            :value="option"
          >
            {{ DELIVERY_STATUS_LABELS[option] }}
          </option>
        </select>
      </FormField>

      <FormField
        id="transport_method"
        v-slot="{ describedBy }"
        label="Transport method"
        required
        :error="fieldErrors.transport_method"
      >
        <select
          id="transport_method"
          v-model="transportMethod"
          :aria-invalid="fieldErrors.transport_method ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
          <option value="">Choose…</option>
          <option
            v-for="m in TRANSPORT_METHODS"
            :key="m"
            :value="m"
          >
            {{ TRANSPORT_METHOD_LABELS[m] }}
          </option>
        </select>
      </FormField>

      <FormField
        id="shipping_date"
        v-slot="{ describedBy }"
        label="Shipping date"
        :required="shippingDateRequired(status)"
        :error="fieldErrors.shipping_date"
      >
        <input
          id="shipping_date"
          v-model="shippingDate"
          type="date"
          :aria-invalid="fieldErrors.shipping_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>

      <FormField
        id="expected_arrival_date"
        v-slot="{ describedBy }"
        label="Expected arrival"
        :error="fieldErrors.expected_arrival_date"
      >
        <input
          id="expected_arrival_date"
          v-model="expectedArrivalDate"
          type="date"
          :aria-invalid="fieldErrors.expected_arrival_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>

      <FormField
        id="arrival_date"
        v-slot="{ describedBy }"
        label="Arrival"
        :required="arrivalRequired(status)"
        :error="fieldErrors.arrival_date"
      >
        <input
          id="arrival_date"
          v-model="arrivalDate"
          type="date"
          :aria-invalid="fieldErrors.arrival_date ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>

      <FormField
        id="offer_price_eur"
        v-slot="{ describedBy }"
        label="Offer price (EUR)"
        :error="fieldErrors.offer_price_eur"
        :warning="offerPriceWarning"
      >
        <input
          id="offer_price_eur"
          v-model="offerPriceEur"
          type="text"
          inputmode="decimal"
          placeholder="0.00"
          :aria-invalid="fieldErrors.offer_price_eur ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>

      <FormField
        id="cost_eur"
        v-slot="{ describedBy }"
        label="Real price (EUR)"
        :error="fieldErrors.cost_eur"
      >
        <input
          id="cost_eur"
          v-model="costEur"
          type="text"
          inputmode="decimal"
          placeholder="0.00"
          :aria-invalid="fieldErrors.cost_eur ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
    </FieldGrid>
  </section>
</template>

<style scoped>
.field.wide {
  grid-column: span 2;
}

@media (max-width: 720px) {
  .field.wide {
    grid-column: span 1;
  }
}
</style>

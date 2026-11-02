<script setup lang="ts">
import { useId } from 'vue'
import FieldGrid from '../atoms/FieldGrid.vue'
import FormField from '../molecules/FormField.vue'
import { TRANSPORT_METHODS, TRANSPORT_METHOD_LABELS, type TransportMethod } from '../../api/types'

const transportMethod = defineModel<TransportMethod | ''>('transportMethod', { required: true })
const plannedShippingDate = defineModel<string>('plannedShippingDate', { required: true })

const headingId = useId()
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Potential delivery</h3>
    <FieldGrid>
      <FormField
        id="transport_method"
        label="Transport method"
        required
      >
        <select
          id="transport_method"
          v-model="transportMethod"
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
        id="planned_shipping_date"
        label="Planned shipping date"
      >
        <input
          id="planned_shipping_date"
          v-model="plannedShippingDate"
          type="date"
        >
      </FormField>
    </FieldGrid>
  </section>
</template>

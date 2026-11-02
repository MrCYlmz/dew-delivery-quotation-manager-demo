<script setup lang="ts">
import { computed, useId } from 'vue'
import { formatArrivalDifference, formatCost, formatDate, formatPriceDifference } from '../../format'
import { DELIVERY_STATUS_LABELS, TRANSPORT_METHOD_LABELS, type DeliveryDetail } from '../../api/types'

const props = defineProps<{ detail: DeliveryDetail }>()

const headingId = useId()

const arrivalDifference = computed(() => {
  const { expected_arrival_date: expected, arrival_date: real } = props.detail
  return expected === null || real === null ? null : formatArrivalDifference(expected, real)
})

const difference = computed(() => {
  const { offer_price_eur: offer, cost_eur: real } = props.detail
  return offer === null || real === null ? null : formatPriceDifference(offer, real)
})
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Delivery</h3>
    <dl class="meta">
      <dt>Customer company</dt>
      <dd>{{ detail.customer_company }}</dd>
      <dt>Status</dt>
      <dd>{{ DELIVERY_STATUS_LABELS[detail.status] }}</dd>
      <dt>Transport method</dt>
      <dd>{{ TRANSPORT_METHOD_LABELS[detail.transport_method] ?? detail.transport_method }}</dd>
      <dt>Shipping date</dt>
      <dd>{{ formatDate(detail.shipping_date) }}</dd>
      <dt>Expected arrival</dt>
      <dd>{{ formatDate(detail.expected_arrival_date) }}</dd>
      <dt>Arrival</dt>
      <dd>
        {{ formatDate(detail.arrival_date) }}<template v-if="arrivalDifference"> · {{ arrivalDifference }}</template>
      </dd>
      <dt>Offer price</dt>
      <dd class="cost">{{ formatCost(detail.offer_price_eur) }}</dd>
      <dt>Real price</dt>
      <dd class="cost">{{ formatCost(detail.cost_eur) }}</dd>
      <template v-if="difference">
        <dt>Difference</dt>
        <dd class="cost">{{ difference }}</dd>
      </template>
    </dl>
  </section>
</template>

<style scoped>
dl.meta {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.3rem 0.9rem;
  margin: 0;
}

dl.meta dt {
  color: var(--text-muted);
  font-size: 0.85rem;
}

dl.meta dd {
  margin: 0;
}

dl.meta dd.cost {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>

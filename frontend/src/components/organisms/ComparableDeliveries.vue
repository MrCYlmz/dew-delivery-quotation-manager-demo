<script setup lang="ts">
import { computed, useId } from 'vue'
import { TRANSPORT_METHOD_LABELS, type EstimateComparables, type TransportMethod } from '../../api/types'
import { countryName } from '../../countries'
import { METHOD_ONLY_WARNING, type EstimateStatus } from '../../estimate'
import { formatCost, formatDate, formatNumber } from '../../format'

const props = defineProps<{
  status: EstimateStatus
  found: EstimateComparables | null
  transportMethod: TransportMethod | ''
  originCountry: string
  destinationCountry: string
}>()

const headingId = useId()

const methodLabel = computed(() =>
  props.transportMethod === '' ? '' : TRANSPORT_METHOD_LABELS[props.transportMethod],
)

const fallbackNote = computed(() => {
  if (props.found?.fallback === 'ORIGIN_AND_DESTINATION_IGNORED') return METHOD_ONLY_WARNING
  if (props.found?.fallback === 'DESTINATION_IGNORED') {
    return `Fewer than 3 deliveries on this route, so the destination country is ignored: these are ${
      methodLabel.value
    } deliveries from ${countryName(props.originCountry)} to any destination.`
  }
  return null
})

function place(country: string, city: string): string {
  return [city, countryName(country)].filter(Boolean).join(', ')
}
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
    aria-live="polite"
  >
    <h3 :id="headingId">Comparable deliveries</h3>

    <p
      v-if="status === 'incomplete'"
      class="muted"
    >
      Choose the transport method, origin country and destination country to see comparable
      deliveries.
    </p>
    <p
      v-else-if="status === 'invalid'"
      class="muted"
    >Fix the cargo totals to see comparable deliveries.</p>
    <p
      v-else-if="status === 'loading'"
      class="spinner-text"
    >Finding comparable deliveries…</p>
    <p
      v-else-if="status === 'failed'"
      class="muted"
    >No comparables to show.</p>
    <div
      v-else-if="status === 'not-enough' || !found"
      class="not-enough"
    >
      <strong>Not enough comparable deliveries</strong>
      <span class="muted small">
        The archive has no delivery with this transport method on any route yet, so there is
        nothing to build a prompt from.
      </span>
    </div>

    <template v-else>
      <p
        v-if="fallbackNote"
        class="fallback-note"
        role="note"
      >{{ fallbackNote }}</p>
      <p class="basis">
        <template v-if="found.fallback === 'NONE'">
          Same method and route: {{ methodLabel }}, {{ countryName(originCountry) }} to
          {{ countryName(destinationCountry) }}.
        </template>
        <template v-if="found.chargeable_kg === null">No cargo size given: newest first.</template>
        <template v-else>
          Closest in size and newest first. Chargeable weight of this delivery:
          <strong>{{ formatNumber(found.chargeable_kg) }} kg</strong>.
        </template>
        <span class="muted">{{ found.comparables.length }} shown, best match first.</span>
      </p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th
                class="num"
                scope="col"
              >#</th>
              <th scope="col">Record</th>
              <th scope="col">Route</th>
              <th scope="col">Shipped</th>
              <th
                class="num"
                scope="col"
              >Age</th>
              <th
                class="num"
                scope="col"
              >Chargeable kg</th>
              <th
                class="num"
                scope="col"
              >Price</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(comparable, rank) in found.comparables"
              :key="comparable.id"
            >
              <td class="num">{{ rank + 1 }}</td>
              <td class="nowrap">
                <RouterLink
                  :to="{ name: 'delivery-detail', params: { id: comparable.id } }"
                  target="_blank"
                  rel="noopener"
                >
                  Delivery {{ comparable.id }}<span class="sr-only"> (opens in a new tab)</span>
                </RouterLink>
              </td>
              <td class="route-cell">
                <span>{{ place(comparable.origin_country, comparable.origin_city) }}</span>
                <span class="muted">to {{ place(comparable.destination_country, comparable.destination_city) }}</span>
              </td>
              <td class="nowrap">{{ formatDate(comparable.shipping_date) }}</td>
              <td class="num nowrap">{{ comparable.age_months }} mo</td>
              <td class="num">{{ formatNumber(comparable.chargeable_kg) }}</td>
              <td
                v-if="comparable.cost_eur !== null"
                class="num nowrap"
              >{{ formatCost(comparable.cost_eur) }}</td>
              <td
                v-else
                class="num nowrap"
              >{{ formatCost(comparable.offer_price_eur) }} <span class="muted">(offer)</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="field-hint">
        Records open in a new tab, so this form stays as it is. Volume, gross weight and notes of
        each record are in the prompt.
      </p>
    </template>
  </section>
</template>

<style scoped>
.basis {
  margin: 0 0 0.7rem;
  font-size: 0.9rem;
}

.fallback-note {
  margin: 0 0 0.8rem;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--warn);
  background: var(--warn-soft);
  color: var(--warn);
  border-radius: var(--radius);
  font-size: 0.88rem;
}

.not-enough {
  margin: 0.2rem 0 0;
  padding: 0.8rem 0.9rem;
  border: 1px solid var(--border-strong);
  background: var(--surface-alt);
  border-radius: var(--radius);
}

.not-enough strong {
  display: block;
  font-size: 1rem;
}

.route-cell span {
  display: block;
  white-space: nowrap;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>

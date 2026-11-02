<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  DELIVERY_STATUSES,
  DELIVERY_STATUS_LABELS,
  TRANSPORT_METHODS,
  TRANSPORT_METHOD_LABELS,
  type DeliveryFilters,
  type DeliveryStatus,
} from '../../api/types'
import { countryName } from '../../countries'
import { FILTER_LABELS, activeFilterEntries, type ActiveFilter } from '../../filters'

const props = defineProps<{
  filters: DeliveryFilters
  companies: string[]
  originCountries: string[]
  destinationCountries: string[]
  originCities: string[]
  destinationCities: string[]
}>()
const emit = defineEmits<{ change: [filters: DeliveryFilters] }>()

const draft = ref<DeliveryFilters>({ ...props.filters })

const active = computed(() => activeFilterEntries(props.filters))

function normalise(filters: DeliveryFilters): DeliveryFilters {
  const out: DeliveryFilters = {}
  for (const [key, value] of Object.entries(filters)) {
    const trimmed = typeof value === 'string' ? value.trim() : value
    if (trimmed) out[key as keyof DeliveryFilters] = trimmed as never
  }
  return out
}

function serialise(filters: DeliveryFilters): string {
  const entries = Object.entries(normalise(filters)).sort(([a], [b]) => a.localeCompare(b))
  return JSON.stringify(entries)
}

watch(
  () => props.filters,
  (next) => {
    if (serialise(next) !== serialise(draft.value)) draft.value = { ...next }
  },
  { deep: true },
)

watch(
  draft,
  (next) => {
    if (serialise(next) !== serialise(props.filters)) emit('change', normalise(next))
  },
  { deep: true },
)

function onCountryChange(side: 'origin' | 'destination') {
  if (side === 'origin') draft.value.origin_city = undefined
  else draft.value.destination_city = undefined
}

function toggleStatus(status: DeliveryStatus) {
  const ticked = draft.value.status ?? []
  const next = ticked.includes(status)
    ? ticked.filter((other) => other !== status)
    : DELIVERY_STATUSES.filter((known) => known === status || ticked.includes(known))
  draft.value = { ...draft.value, status: next.length > 0 ? next : undefined }
}

function removeFilter(entry: ActiveFilter) {
  if (entry.key === 'status') toggleStatus(entry.value)
  else draft.value = { ...draft.value, [entry.key]: undefined }
}

function clearAll() {
  draft.value = {}
}

function chipText(entry: ActiveFilter): string {
  if (entry.key === 'status') return DELIVERY_STATUS_LABELS[entry.value]
  const { key, value } = entry
  if (key === 'transport_method') {
    return TRANSPORT_METHOD_LABELS[value as keyof typeof TRANSPORT_METHOD_LABELS] ?? value
  }
  if (key === 'origin_country' || key === 'destination_country') return countryName(value)
  if (key === 'cost_min' || key === 'cost_max') return `€${value}`
  return value
}

function chipTitle(entry: ActiveFilter): string {
  if (entry.key === 'status') return `Remove ${chipText(entry)} from Status filter`
  return `Remove ${FILTER_LABELS[entry.key]} filter`
}
</script>

<template>
  <section
    class="panel filter-bar"
    aria-label="Filters"
  >
    <div class="grid">
      <div class="field">
        <label for="f-company">{{ FILTER_LABELS.customer_company }}</label>
        <select
          id="f-company"
          v-model="draft.customer_company"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="name in companies"
            :key="name"
            :value="name"
          >{{ name }}</option>
        </select>
      </div>

      <div class="field">
        <label for="f-origin-country">{{ FILTER_LABELS.origin_country }}</label>
        <select
          id="f-origin-country"
          v-model="draft.origin_country"
          @change="onCountryChange('origin')"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="c in originCountries"
            :key="c"
            :value="c"
          >{{ countryName(c) }}</option>
        </select>
      </div>

      <div class="field">
        <label for="f-origin-city">{{ FILTER_LABELS.origin_city }}</label>
        <select
          id="f-origin-city"
          v-model="draft.origin_city"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="c in originCities"
            :key="c"
            :value="c"
          >{{ c }}</option>
        </select>
      </div>

      <div class="field">
        <label for="f-dest-country">{{ FILTER_LABELS.destination_country }}</label>
        <select
          id="f-dest-country"
          v-model="draft.destination_country"
          @change="onCountryChange('destination')"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="c in destinationCountries"
            :key="c"
            :value="c"
          >
            {{ countryName(c) }}
          </option>
        </select>
      </div>

      <div class="field">
        <label for="f-dest-city">{{ FILTER_LABELS.destination_city }}</label>
        <select
          id="f-dest-city"
          v-model="draft.destination_city"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="c in destinationCities"
            :key="c"
            :value="c"
          >{{ c }}</option>
        </select>
      </div>

      <div class="field">
        <label for="f-method">{{ FILTER_LABELS.transport_method }}</label>
        <select
          id="f-method"
          v-model="draft.transport_method"
        >
          <option :value="undefined">Any</option>
          <option
            v-for="m in TRANSPORT_METHODS"
            :key="m"
            :value="m"
          >
            {{ TRANSPORT_METHOD_LABELS[m] }}
          </option>
        </select>
      </div>

      <fieldset class="field span-2">
        <legend>Shipping date</legend>
        <div class="pair">
          <input
            v-model.lazy="draft.shipping_date_from"
            type="date"
            aria-label="Shipped from"
          >
          <span class="dash">to</span>
          <input
            v-model.lazy="draft.shipping_date_to"
            type="date"
            aria-label="Shipped to"
          >
        </div>
      </fieldset>

      <fieldset class="field span-2">
        <legend>Arrival date</legend>
        <div class="pair">
          <input
            v-model.lazy="draft.arrival_date_from"
            type="date"
            aria-label="Arrived from"
          >
          <span class="dash">to</span>
          <input
            v-model.lazy="draft.arrival_date_to"
            type="date"
            aria-label="Arrived to"
          >
        </div>
      </fieldset>

      <fieldset class="field span-2">
        <legend>Real price EUR</legend>
        <div class="pair">
          <input
            v-model.lazy="draft.cost_min"
            type="number"
            min="0"
            step="0.01"
            placeholder="min"
            aria-label="Real price minimum"
          >
          <span class="dash">to</span>
          <input
            v-model.lazy="draft.cost_max"
            type="number"
            min="0"
            step="0.01"
            placeholder="max"
            aria-label="Real price maximum"
          >
        </div>
      </fieldset>

      <fieldset class="field status-filter">
        <legend>{{ FILTER_LABELS.status }}</legend>
        <div class="status-checks">
          <label
            v-for="status in DELIVERY_STATUSES"
            :key="status"
            class="check"
          >
            <input
              type="checkbox"
              :checked="draft.status?.includes(status) ?? false"
              @change="toggleStatus(status)"
            >
            {{ DELIVERY_STATUS_LABELS[status] }}
          </label>
        </div>
      </fieldset>
    </div>

    <div
      v-if="active.length"
      class="active-row"
    >
      <span class="small muted">Active filters:</span>
      <button
        v-for="entry in active"
        :key="`${entry.key}-${entry.value}`"
        type="button"
        class="chip"
        :title="chipTitle(entry)"
        @click="removeFilter(entry)"
      >
        {{ FILTER_LABELS[entry.key] }}: {{ chipText(entry) }}
        <span aria-hidden="true">✕</span>
      </button>
      <button
        type="button"
        class="link clear-all"
        @click="clearAll"
      >Clear all</button>
    </div>
    <p
      v-else
      class="small muted no-filters"
    >
      No filters — showing every delivery. Combine any of the above to narrow to comparables.
    </p>
  </section>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0.6rem 0.8rem;
}

.field {
  min-width: 0;
}

.span-2 {
  grid-column: span 2;
}

@media (max-width: 720px) {
  .span-2 {
    grid-column: span 1;
  }
}

fieldset {
  border: none;
  margin: 0;
  padding: 0;
}

legend {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 0.2rem;
  padding: 0;
}

.pair {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.dash {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.status-filter {
  grid-column: 1 / -1;
}

.status-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.9rem;
  padding-top: 0.15rem;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
  font-weight: 400;
  color: var(--text);
  margin: 0;
}

.check input {
  width: auto;
  margin: 0;
}

.active-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.85rem;
  padding-top: 0.7rem;
  border-top: 1px solid var(--border);
}

.chip {
  padding: 0.12rem 0.5rem;
  font-size: 0.82rem;
  border-radius: 999px;
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--text);
}

.clear-all {
  margin-left: 0.3rem;
}

.no-filters {
  margin: 0.85rem 0 0;
  padding-top: 0.7rem;
  border-top: 1px solid var(--border);
}
</style>

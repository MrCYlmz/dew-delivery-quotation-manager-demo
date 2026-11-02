<script setup lang="ts">
import ErrorBanner from '../molecules/ErrorBanner.vue'
import DeliveryTable from '../organisms/DeliveryTable.vue'
import FilterBar from '../organisms/FilterBar.vue'
import type { DeliveryFilters, DeliveryListRow, SortKey, SortOrder } from '../../api/types'

defineProps<{
  rows: DeliveryListRow[]
  loading: boolean
  error: string | null
  filters: DeliveryFilters
  activeCount: number
  sort: SortKey
  order: SortOrder
  companies: string[]
  originCountries: string[]
  destinationCountries: string[]
  originCities: string[]
  destinationCities: string[]
}>()

const emit = defineEmits<{
  retry: []
  'filter-change': [filters: DeliveryFilters]
  sort: [key: SortKey]
  open: [id: number]
}>()
</script>

<template>
  <div class="row-between page-head">
    <h1>Deliveries</h1>
    <p
      v-if="!loading && !error"
      class="muted small"
    >
      {{ rows.length }} {{ rows.length === 1 ? 'delivery' : 'deliveries' }}
    </p>
  </div>

  <ErrorBanner
    v-if="error"
    :message="error"
    @retry="emit('retry')"
  />

  <FilterBar
    :filters="filters"
    :companies="companies"
    :origin-countries="originCountries"
    :destination-countries="destinationCountries"
    :origin-cities="originCities"
    :destination-cities="destinationCities"
    @change="emit('filter-change', $event)"
  />

  <div class="panel">
    <p
      v-if="loading"
      class="spinner-text"
    >Loading deliveries…</p>
    <p
      v-else-if="error"
      class="muted"
    >
      The list could not be loaded, so nothing is shown. This is not an empty archive.
    </p>
    <p
      v-else-if="rows.length === 0 && activeCount > 0"
      class="spinner-text"
    >
      No delivery matches these {{ activeCount }} filters. Remove one above to widen the search.
    </p>
    <p
      v-else-if="rows.length === 0"
      class="spinner-text"
    >
      No deliveries recorded yet.
      <RouterLink to="/deliveries/new">Add the first one.</RouterLink>
    </p>
    <DeliveryTable
      v-else
      :rows="rows"
      :sort="sort"
      :order="order"
      @sort="emit('sort', $event)"
      @open="emit('open', $event)"
    />
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.9rem;
}
</style>

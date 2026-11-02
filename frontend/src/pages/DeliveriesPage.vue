<script setup lang="ts">
import DeliveriesTemplate from '../components/templates/DeliveriesTemplate.vue'
import { useDeliveriesPage } from '../composables/useDeliveriesPage'

const {
  rows,
  loading,
  error,
  filters,
  sortState,
  activeCount,
  companies,
  originCountries,
  destinationCountries,
  originCities,
  destinationCities,
  load,
  changeSort,
  changeFilters,
  openDelivery,
} = useDeliveriesPage()
</script>

<template>
  <DeliveriesTemplate
    :rows="rows"
    :loading="loading"
    :error="error"
    :filters="filters"
    :active-count="activeCount"
    :sort="sortState.sort"
    :order="sortState.order"
    :companies="companies"
    :origin-countries="originCountries"
    :destination-countries="destinationCountries"
    :origin-cities="originCities"
    :destination-cities="destinationCities"
    @retry="load"
    @filter-change="changeFilters"
    @sort="changeSort"
    @open="openDelivery"
  />
</template>

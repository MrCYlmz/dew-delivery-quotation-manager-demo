import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage, listDeliveries } from '../api/client'
import type { DeliveryFilters, DeliveryListRow, SortKey } from '../api/types'
import {
  DEFAULT_ORDER,
  countActive,
  filtersFromRoute,
  sortFromRoute,
  toApiQuery,
  toRouteQuery,
} from '../filters'
import { citySuggestions, companySuggestions, countrySuggestions } from './useSuggestions'

export function useDeliveriesPage() {
  const route = useRoute()
  const router = useRouter()

  const rows = ref<DeliveryListRow[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const filters = computed<DeliveryFilters>(() => filtersFromRoute(route.query))
  const sortState = computed(() => sortFromRoute(route.query))
  const activeCount = computed(() => countActive(filters.value))

  const companies = companySuggestions()
  const originCountries = countrySuggestions('origin')
  const destinationCountries = countrySuggestions('destination')
  const originCities = computed(() => citySuggestions('origin', filters.value.origin_country).value)
  const destinationCities = computed(
    () => citySuggestions('destination', filters.value.destination_country).value,
  )

  let latestRequest = 0

  async function load() {
    const request = ++latestRequest
    loading.value = true
    error.value = null
    const query = toApiQuery(filters.value, sortState.value.sort, sortState.value.order)
    try {
      const result = await listDeliveries(query)
      if (request !== latestRequest) return
      rows.value = result
    } catch (err) {
      if (request !== latestRequest) return
      rows.value = []
      error.value = errorMessage(err)
    } finally {
      if (request === latestRequest) loading.value = false
    }
  }

  watch(
    () => route.fullPath,
    () => {
      if (route.name === 'deliveries') void load()
    },
    { immediate: true },
  )

  function changeSort(key: SortKey) {
    const current = sortState.value
    const order = current.sort === key && current.order === 'desc' ? 'asc' : DEFAULT_ORDER
    void router.replace({ name: 'deliveries', query: toRouteQuery(filters.value, key, order) })
  }

  function changeFilters(next: DeliveryFilters) {
    void router.replace({
      name: 'deliveries',
      query: toRouteQuery(next, sortState.value.sort, sortState.value.order),
    })
  }

  function openDelivery(id: number) {
    void router.push({ name: 'delivery-detail', params: { id } })
  }

  return {
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
  }
}

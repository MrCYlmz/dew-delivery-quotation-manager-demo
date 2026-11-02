import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import {
  DELIVERY_STATUSES,
  SORT_KEYS,
  TRANSPORT_METHODS,
  type DeliveryFilters,
  type DeliveryQuery,
  type DeliveryStatus,
  type SortKey,
  type SortOrder,
  type TransportMethod,
} from './api/types'

export const DEFAULT_SORT: SortKey = 'shipping_date'
export const DEFAULT_ORDER: SortOrder = 'desc'

export const FILTER_KEYS = [
  'customer_company',
  'origin_country',
  'origin_city',
  'destination_country',
  'destination_city',
  'transport_method',
  'shipping_date_from',
  'shipping_date_to',
  'arrival_date_from',
  'arrival_date_to',
  'cost_min',
  'cost_max',
] as const satisfies readonly (keyof DeliveryFilters)[]

export type FilterKey = (typeof FILTER_KEYS)[number]

export type ActiveFilter =
  | { key: FilterKey; value: string }
  | { key: 'status'; value: DeliveryStatus }

export const FILTER_LABELS: Record<ActiveFilter['key'], string> = {
  customer_company: 'Company',
  origin_country: 'Origin country',
  origin_city: 'Origin city',
  destination_country: 'Destination country',
  destination_city: 'Destination city',
  transport_method: 'Method',
  shipping_date_from: 'Shipped from',
  shipping_date_to: 'Shipped to',
  arrival_date_from: 'Arrived from',
  arrival_date_to: 'Arrived to',
  cost_min: 'Real price min',
  cost_max: 'Real price max',
  status: 'Status',
}

function single(value: LocationQuery[string]): string {
  if (Array.isArray(value)) return value[0] ?? ''
  return value ?? ''
}

export function filtersFromRoute(query: LocationQuery): DeliveryFilters {
  const filters: DeliveryFilters = {}
  for (const key of FILTER_KEYS) {
    const value = single(query[key]).trim()
    if (!value) continue
    if (key === 'transport_method') {
      if ((TRANSPORT_METHODS as readonly string[]).includes(value)) {
        filters.transport_method = value as TransportMethod
      }
      continue
    }
    filters[key] = value
  }
  const ticked = [query.status].flat()
  const status = DELIVERY_STATUSES.filter((known) => ticked.includes(known))
  if (status.length > 0) filters.status = status
  return filters
}

export function sortFromRoute(query: LocationQuery): { sort: SortKey; order: SortOrder } {
  const sort = single(query.sort)
  const order = single(query.order)
  return {
    sort: (SORT_KEYS as readonly string[]).includes(sort) ? (sort as SortKey) : DEFAULT_SORT,
    order: order === 'asc' ? 'asc' : DEFAULT_ORDER,
  }
}

export function toApiQuery(
  filters: DeliveryFilters,
  sort: SortKey,
  order: SortOrder,
): DeliveryQuery {
  return { ...filters, sort, order }
}

export function toRouteQuery(
  filters: DeliveryFilters,
  sort: SortKey,
  order: SortOrder,
): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  for (const key of FILTER_KEYS) {
    const value = filters[key]
    if (value) query[key] = value
  }
  if (filters.status?.length) query.status = filters.status
  if (sort !== DEFAULT_SORT) query.sort = sort
  if (order !== DEFAULT_ORDER) query.order = order
  return query
}

export function activeFilterEntries(filters: DeliveryFilters): ActiveFilter[] {
  const entries: ActiveFilter[] = FILTER_KEYS.flatMap((key) => {
    const value = filters[key]
    return value ? [{ key, value: String(value) }] : []
  })
  for (const status of filters.status ?? []) entries.push({ key: 'status', value: status })
  return entries
}

// The ticked statuses are one filter, however many there are.
export function countActive(filters: DeliveryFilters): number {
  const set = FILTER_KEYS.filter((key) => filters[key]).length
  return filters.status?.length ? set + 1 : set
}

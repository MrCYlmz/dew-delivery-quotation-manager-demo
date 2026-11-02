export const TRANSPORT_METHODS = ['SEA', 'AIR', 'ROAD', 'RAIL'] as const
export type TransportMethod = (typeof TRANSPORT_METHODS)[number]

export const TRANSPORT_METHOD_LABELS: Record<TransportMethod, string> = {
  SEA: 'Sea',
  AIR: 'Air',
  ROAD: 'Road',
  RAIL: 'Rail',
}

export const DELIVERY_STATUSES = [
  'TO_DO',
  'OFFERS_RECEIVED',
  'OFFER_ACCEPTED',
  'ON_THE_WAY',
  'DELIVERED',
] as const
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number]

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  TO_DO: 'To do',
  OFFERS_RECEIVED: 'Offers received',
  OFFER_ACCEPTED: 'Offer accepted',
  ON_THE_WAY: 'On the way',
  DELIVERED: 'Delivered',
}

export interface DeliveryListRow {
  id: number
  customer_company: string
  origin_country: string
  origin_city: string
  destination_country: string
  destination_city: string
  transport_method: TransportMethod
  status: DeliveryStatus
  shipping_date: string | null
  arrival_date: string | null
  cost_eur: number | null
  volume_m3: number | null
  gross_weight_kg: number | null
  pallet_count: number | null
  has_notes: boolean
  pdf_count: number
}

export interface DeliveryItem {
  source_row: number
  description: string | null
  article_no: string | null
  length_mm: number | null
  width_mm: number | null
  height_mm: number | null
  pallet_count: number | null
  volume_m3: number | null
  gross_weight_kg: number | null
  net_weight_kg: number | null
  volume_mismatch: boolean
}

export interface DeliveryPdf {
  id: number
  original_filename: string
  size_bytes: number | null
  sort_order: number
  uploaded_at: string
}

export interface TotalsFlags {
  volume_m3: boolean
  gross_weight_kg: boolean
  pallet_count: boolean
}

export interface ItemSums {
  volume_m3: number | null
  gross_weight_kg: number | null
  pallet_count: number | null
}

export interface DeliveryDetail extends DeliveryListRow {
  origin_postal_code: string | null
  origin_street: string | null
  destination_postal_code: string | null
  destination_street: string | null
  notes: string | null
  excel_filename: string | null
  offer_price_eur: number | null
  expected_arrival_date: string | null
  moved_to_delivered_on: string | null
  totals_edited: TotalsFlags
  item_sums: ItemSums
  items: DeliveryItem[]
  pdfs: DeliveryPdf[]
}

export interface DeliveryWriteBody {
  customer_company: string
  origin_country: string
  origin_city: string
  origin_postal_code: string | null
  origin_street: string | null
  destination_country: string
  destination_city: string
  destination_postal_code: string | null
  destination_street: string | null
  transport_method: TransportMethod
  status: DeliveryStatus
  shipping_date: string | null
  expected_arrival_date: string | null
  arrival_date: string | null
  offer_price_eur: number | null
  cost_eur: number | null
  volume_m3: number | null
  gross_weight_kg: number | null
  pallet_count: number | null
  notes: string | null
}

export interface BoardCard {
  id: number
  status: DeliveryStatus
  customer_company: string
  origin_country: string
  origin_city: string
  destination_country: string
  destination_city: string
  transport_method: TransportMethod
  shipping_date: string | null
  expected_arrival_date: string | null
  arrival_date: string | null
  days_late: number | null
  offer_price_eur: number | null
}

export interface DeliveryMoveBody {
  status: DeliveryStatus
  shipping_date?: string
  expected_arrival_date?: string
  arrival_date?: string
}

export const SORT_KEYS = [
  'shipping_date',
  'arrival_date',
  'cost_eur',
  'volume_m3',
  'gross_weight_kg',
] as const
export type SortKey = (typeof SORT_KEYS)[number]
export type SortOrder = 'asc' | 'desc'

export interface DeliveryFilters {
  customer_company?: string
  origin_country?: string
  origin_city?: string
  destination_country?: string
  destination_city?: string
  transport_method?: TransportMethod
  shipping_date_from?: string
  shipping_date_to?: string
  arrival_date_from?: string
  arrival_date_to?: string
  cost_min?: string
  cost_max?: string
  status?: DeliveryStatus[]
}

export interface DeliveryQuery extends DeliveryFilters {
  sort?: SortKey
  order?: SortOrder
}

export interface ParsedPackingList {
  total_volume_m3: number
  total_gross_weight_kg: number
  total_pallet_count: number
  items: DeliveryItem[]
  volume_mismatch_count: number
  sheet_name: string
}

export interface ApiErrorBody {
  error?: string
  detail?: string
  fields?: Record<string, string>
}

export type AddressSide = 'origin' | 'destination'

export interface EstimateComparablesQuery {
  transport_method: TransportMethod
  origin_country: string
  destination_country: string
  volume_m3?: string
  gross_weight_kg?: string
}

export type ComparablesFallback = 'NONE' | 'DESTINATION_IGNORED' | 'ORIGIN_AND_DESTINATION_IGNORED'

export interface EstimateComparable {
  id: number
  origin_country: string
  origin_city: string
  destination_country: string
  destination_city: string
  transport_method: TransportMethod
  shipping_date: string | null
  expected_arrival_date: string | null
  arrival_date: string | null
  age_months: number
  volume_m3: number | null
  gross_weight_kg: number | null
  pallet_count: number | null
  chargeable_kg: number | null
  cost_eur: number | null
  offer_price_eur: number | null
  notes: string | null
}

export interface EstimateComparables {
  chargeable_kg: number | null
  fallback: ComparablesFallback
  removed_company_names: number
  comparables: EstimateComparable[]
}

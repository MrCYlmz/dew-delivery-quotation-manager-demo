import {
  TRANSPORT_METHOD_LABELS,
  type EstimateComparable,
  type EstimateComparables,
  type TransportMethod,
} from './api/types'
import { countryName } from './countries'
import { formatPlainNumber, isoDate } from './format'

export type EstimateStatus = 'incomplete' | 'invalid' | 'loading' | 'failed' | 'not-enough' | 'found'

export type CopyStatus = 'copied' | 'blocked'

export const METHOD_ONLY_WARNING =
  'Fewer than 3 deliveries on this route; other routes with the same method are included.'

export interface PotentialDelivery {
  transport_method: TransportMethod
  origin_country: string
  origin_city: string
  destination_country: string
  destination_city: string
  planned_shipping_date: string
  pallet_count: number | null
  volume_m3: number | null
  gross_weight_kg: number | null
}

const NOT_GIVEN = 'not given'
const MISSING_CELL = '-'

function place(country: string, city: string): string {
  return [city.trim(), countryName(country)].filter(Boolean).join(', ')
}

function quantity(value: number | null, unit: string): string {
  return value === null ? NOT_GIVEN : `${formatPlainNumber(value)}${unit}`
}

// A note keeps to one table row, so its line breaks and pipes must not reach the markdown table.
function cell(text: string | null): string {
  if (text === null || text.trim() === '') return MISSING_CELL
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .join(' / ')
    .replaceAll('|', '/')
}

function numberCell(value: number | null): string {
  return value === null ? MISSING_CELL : formatPlainNumber(value)
}

function arrivalCell(comparable: EstimateComparable): string {
  if (comparable.arrival_date) return comparable.arrival_date
  if (comparable.expected_arrival_date) return `${comparable.expected_arrival_date} (expected)`
  return MISSING_CELL
}

// The backend leaves out comparables with neither price, so one of the two is always set.
function costCell(comparable: EstimateComparable): string {
  if (comparable.cost_eur !== null) return formatPlainNumber(comparable.cost_eur)
  return `${numberCell(comparable.offer_price_eur)} (offer)`
}

function fallbackWarning(found: EstimateComparables): string | null {
  if (found.fallback === 'ORIGIN_AND_DESTINATION_IGNORED') return `Warning: ${METHOD_ONLY_WARNING}`
  if (found.fallback === 'DESTINATION_IGNORED') {
    return 'Warning: Fewer than 3 past deliveries on this route; the destination country was ignored.'
  }
  return null
}

export function buildEstimatePrompt(delivery: PotentialDelivery, found: EstimateComparables): string {
  const route = `${place(delivery.origin_country, delivery.origin_city)} -> ${place(
    delivery.destination_country,
    delivery.destination_city,
  )}`
  const lines = [
    'You are a freight cost analyst helping a freight broker estimate the buy cost of a potential delivery.',
    '',
    `Today's date: ${isoDate(new Date())}`,
    '',
    'Potential delivery',
    `- Route: ${route}`,
    `- Method: ${TRANSPORT_METHOD_LABELS[delivery.transport_method]}`,
    `- Planned shipping date: ${delivery.planned_shipping_date || NOT_GIVEN}`,
    `- Pallets: ${quantity(delivery.pallet_count, '')}`,
    `- Volume: ${quantity(delivery.volume_m3, ' m³')}`,
    `- Gross weight: ${quantity(delivery.gross_weight_kg, ' kg')}`,
    found.chargeable_kg === null
      ? '- Cargo size unknown'
      : `- Chargeable weight: ${formatPlainNumber(found.chargeable_kg)} kg`,
  ]

  const warning = fallbackWarning(found)
  if (warning) lines.push('', warning)

  lines.push(
    '',
    "Comparable past deliveries (cost = the broker's buy cost)",
    '',
    '| Ref no. | Route | Method | Shipped | Arrived | Age (months) | m³ | Gross kg | Chargeable kg | Cost EUR (buy cost) | Notes |',
    '|---|---|---|---|---|---|---|---|---|---|---|',
  )
  for (const comparable of found.comparables) {
    const comparableRoute = `${place(comparable.origin_country, comparable.origin_city)} -> ${place(
      comparable.destination_country,
      comparable.destination_city,
    )}`
    const cells = [
      String(comparable.id),
      cell(comparableRoute),
      TRANSPORT_METHOD_LABELS[comparable.transport_method],
      comparable.shipping_date ?? MISSING_CELL,
      arrivalCell(comparable),
      String(comparable.age_months),
      numberCell(comparable.volume_m3),
      numberCell(comparable.gross_weight_kg),
      numberCell(comparable.chargeable_kg),
      costCell(comparable),
      cell(comparable.notes),
    ]
    lines.push(`| ${cells.join(' | ')} |`)
  }

  lines.push('', `A cell with ${MISSING_CELL} means not given.`)
  if (found.comparables.some((comparable) => comparable.cost_eur === null)) {
    lines.push(
      'A cost marked (offer) is the accepted offer from the delivery company, not a final cost: the delivery has no real price yet.',
    )
  }
  lines.push(
    '',
    'Answer in this format:',
    '1. Per ref: what the cost seems to include, and any anomalies.',
    '2. Refs to ignore, and why.',
    "3. Market check: search the web for current rates and surcharges on this route and method (e.g. freight indices, carrier notices), list each source with its link and date, and say how today's market compares with the comparables' dates.",
    '4. Expected buy cost in EUR as low / likely / high, based on the comparables adjusted by item 3.',
    '5. Refs that weighed most.',
    '6. Warnings.',
    '',
    'Keep apart what comes from the data above and what comes from the web.',
  )
  return lines.join('\n')
}

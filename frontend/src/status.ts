import type { DeliveryStatus } from './api/types'

const SHIPPING_DATE_REQUIRED: readonly DeliveryStatus[] = ['ON_THE_WAY', 'DELIVERED']
const OFFER_PRICE_EXPECTED: readonly DeliveryStatus[] = ['OFFER_ACCEPTED', 'ON_THE_WAY', 'DELIVERED']

export const MISSING_OFFER_PRICE = 'No offer price'

export function shippingDateRequired(status: DeliveryStatus): boolean {
  return SHIPPING_DATE_REQUIRED.includes(status)
}

// The real arrival; the expected arrival is never required.
export function arrivalRequired(status: DeliveryStatus): boolean {
  return status === 'DELIVERED'
}

export function offerPriceExpected(status: DeliveryStatus): boolean {
  return OFFER_PRICE_EXPECTED.includes(status)
}

// Delivered always asks, so Alice confirms the real arrival day instead of the expected one.
export function moveAsksForDates(delivery: { shipping_date: string | null }, status: DeliveryStatus): boolean {
  if (status === 'DELIVERED') return true
  return shippingDateRequired(status) && !delivery.shipping_date
}

export type LateLevel = 1 | 2 | 3

export function lateLevel(daysLate: number): LateLevel {
  if (daysLate >= 8) return 3
  if (daysLate >= 4) return 2
  return 1
}

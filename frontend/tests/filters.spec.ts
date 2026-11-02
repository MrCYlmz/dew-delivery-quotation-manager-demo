import { describe, expect, it } from 'vitest'
import {
  DEFAULT_ORDER,
  DEFAULT_SORT,
  FILTER_KEYS,
  activeFilterEntries,
  countActive,
  filtersFromRoute,
  sortFromRoute,
  toApiQuery,
  toRouteQuery,
} from '../src/filters'

describe('filters', () => {
  it('sorts by newest shipping date by default (S-1 AC 8)', () => {
    expect(DEFAULT_SORT).toBe('shipping_date')
    expect(DEFAULT_ORDER).toBe('desc')
  })

  describe('filtersFromRoute', () => {
    it('reads no filters from an empty query (S-1 AC 8)', () => {
      expect(filtersFromRoute({})).toEqual({})
    })

    it('reads every filter key from the query (S-1 AC 8)', () => {
      const query = {
        customer_company: 'Example Interiors',
        origin_country: 'CN',
        origin_city: 'Ningbo',
        destination_country: 'DE',
        destination_city: 'Munich',
        transport_method: 'SEA',
        shipping_date_from: '2026-01-01',
        shipping_date_to: '2026-12-31',
        arrival_date_from: '2026-02-01',
        arrival_date_to: '2026-11-30',
        cost_min: '1000',
        cost_max: '5000',
      }
      expect(filtersFromRoute(query)).toEqual(query)
    })

    it('trims values and drops empty ones (S-1 AC 8)', () => {
      expect(
        filtersFromRoute({ customer_company: '  Example Interiors ', origin_city: '   ', cost_max: '' }),
      ).toEqual({ customer_company: 'Example Interiors' })
    })

    it('keeps a zero cost bound, which is a real filter (S-1 AC 8)', () => {
      expect(filtersFromRoute({ cost_min: '0', cost_max: '0' })).toEqual({
        cost_min: '0',
        cost_max: '0',
      })
    })

    it('drops missing values and takes the first of a repeated key (S-1 AC 8)', () => {
      expect(
        filtersFromRoute({ origin_city: null, destination_city: ['Munich', 'Lyon'], origin_country: [] }),
      ).toEqual({ destination_city: 'Munich' })
    })

    it('drops a transport method outside the four of the contract (S-1 AC 8)', () => {
      expect(filtersFromRoute({ transport_method: 'OTHER' })).toEqual({})
      expect(filtersFromRoute({ transport_method: 'sea' })).toEqual({})
      expect(filtersFromRoute({ transport_method: 'RAIL' })).toEqual({ transport_method: 'RAIL' })
    })

    it('reads one or several ticked statuses (S-9 AC 21)', () => {
      expect(filtersFromRoute({ status: 'TO_DO' })).toEqual({ status: ['TO_DO'] })
      expect(filtersFromRoute({ status: ['ON_THE_WAY', 'DELIVERED'] })).toEqual({
        status: ['ON_THE_WAY', 'DELIVERED'],
      })
    })

    it('drops a blank or unknown status, like an unknown transport method (S-9 AC 21)', () => {
      expect(filtersFromRoute({ status: '' })).toEqual({})
      expect(filtersFromRoute({ status: 'LOST' })).toEqual({})
      expect(filtersFromRoute({ status: 'delivered' })).toEqual({})
      expect(filtersFromRoute({ status: ['', 'LOST', null, 'OFFER_ACCEPTED'] })).toEqual({
        status: ['OFFER_ACCEPTED'],
      })
    })

    it('ignores query keys that are not filters (S-1 AC 8)', () => {
      expect(filtersFromRoute({ sort: 'cost_eur', page: '2', notes: 'x' })).toEqual({})
    })
  })

  describe('sortFromRoute', () => {
    it('falls back to the default sort for an empty query (S-1 AC 8)', () => {
      expect(sortFromRoute({})).toEqual({ sort: 'shipping_date', order: 'desc' })
    })

    it('reads each of the five sortable keys and either order (S-1 AC 8)', () => {
      for (const sort of ['shipping_date', 'arrival_date', 'cost_eur', 'volume_m3', 'gross_weight_kg']) {
        expect(sortFromRoute({ sort, order: 'asc' })).toEqual({ sort, order: 'asc' })
      }
      expect(sortFromRoute({ sort: 'cost_eur', order: 'desc' })).toEqual({
        sort: 'cost_eur',
        order: 'desc',
      })
    })

    it('falls back to the default for an unknown key or order (S-1 AC 8)', () => {
      expect(sortFromRoute({ sort: 'pallet_count', order: 'sideways' })).toEqual({
        sort: 'shipping_date',
        order: 'desc',
      })
      expect(sortFromRoute({ sort: '', order: '' })).toEqual({ sort: 'shipping_date', order: 'desc' })
    })

    it('takes the first of a repeated sort key (S-1 AC 8)', () => {
      expect(sortFromRoute({ sort: ['cost_eur', 'volume_m3'], order: ['asc'] })).toEqual({
        sort: 'cost_eur',
        order: 'asc',
      })
    })
  })

  describe('toApiQuery', () => {
    it('always sends the sort explicitly, also the default (S-1 AC 8)', () => {
      expect(toApiQuery({}, 'shipping_date', 'desc')).toEqual({
        sort: 'shipping_date',
        order: 'desc',
      })
    })

    it('sends the filters together with the sort (S-1 AC 8)', () => {
      expect(toApiQuery({ origin_country: 'CN', cost_min: '0' }, 'cost_eur', 'asc')).toEqual({
        origin_country: 'CN',
        cost_min: '0',
        sort: 'cost_eur',
        order: 'asc',
      })
    })
  })

  describe('toRouteQuery', () => {
    it('gives a clean URL when no filter is set and the sort is the default (S-1 AC 8)', () => {
      expect(toRouteQuery({}, 'shipping_date', 'desc')).toEqual({})
    })

    it('puts the filters and a non-default sort into the URL (S-1 AC 8)', () => {
      expect(toRouteQuery({ transport_method: 'AIR', cost_min: '0' }, 'volume_m3', 'asc')).toEqual({
        transport_method: 'AIR',
        cost_min: '0',
        sort: 'volume_m3',
        order: 'asc',
      })
    })

    it('omits empty filter values (S-1 AC 8)', () => {
      expect(toRouteQuery({ origin_city: '', destination_city: undefined }, 'shipping_date', 'desc')).toEqual({})
    })

    it('round-trips through filtersFromRoute and sortFromRoute (S-1 AC 8)', () => {
      const filters = { customer_company: 'Example Interiors', shipping_date_from: '2026-01-01' }
      const query = toRouteQuery(filters, 'cost_eur', 'desc') as Record<string, string>
      expect(filtersFromRoute(query)).toEqual(filters)
      expect(sortFromRoute(query)).toEqual({ sort: 'cost_eur', order: 'desc' })
    })
  })

  describe('activeFilterEntries and countActive', () => {
    it('lists no active filter for empty filters (S-1 AC 8)', () => {
      expect(activeFilterEntries({})).toEqual([])
      expect(countActive({})).toBe(0)
    })

    it('lists the set filters in filter-bar order, skipping empty ones (S-1 AC 8)', () => {
      const filters = { cost_max: '5000', customer_company: 'Example Interiors', origin_city: '' }
      expect(activeFilterEntries(filters)).toEqual([
        { key: 'customer_company', value: 'Example Interiors' },
        { key: 'cost_max', value: '5000' },
      ])
      expect(countActive(filters)).toBe(2)
    })

    it('counts a zero cost bound as active (S-1 AC 8)', () => {
      expect(countActive({ cost_min: '0' })).toBe(1)
    })

    it('counts every filter key when all are set (S-1 AC 8)', () => {
      const all = Object.fromEntries(FILTER_KEYS.map((key) => [key, key === 'transport_method' ? 'SEA' : 'x']))
      expect(countActive(all)).toBe(12)
    })
  })
})

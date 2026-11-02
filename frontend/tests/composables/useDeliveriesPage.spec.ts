import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { toValue } from 'vue'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryListRow } from '../../src/api/types'
import { useDeliveriesPage } from '../../src/composables/useDeliveriesPage'
import { mountViewModel, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { listRow } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  listDeliveries: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const listDeliveries = vi.mocked(client.listDeliveries)

const vietnamAir = listRow({
  id: 2,
  customer_company: 'Nordlicht Interiors',
  origin_country: 'VN',
  origin_city: 'Ho Chi Minh City',
  transport_method: 'AIR',
})

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
})

afterEach(unmountAll)

describe('useDeliveriesPage', () => {
  it('is loading until the list arrives, then holds its rows (S-2 AC 10)', async () => {
    const list = deferred<DeliveryListRow[]>()
    listDeliveries.mockReturnValue(list.promise)

    const { viewModel } = await mountViewModel('/deliveries', useDeliveriesPage)

    expect(listDeliveries).toHaveBeenCalledWith({ sort: 'shipping_date', order: 'desc' })
    expect(toValue(viewModel.loading)).toBe(true)

    list.resolve([listRow(), vietnamAir])
    await flushPromises()

    expect(toValue(viewModel.loading)).toBe(false)
    expect(toValue(viewModel.error)).toBeNull()
    expect(toValue(viewModel.rows)).toEqual([listRow(), vietnamAir])
  })

  it('holds the error message and no rows when the list fails (S-2 AC 10)', async () => {
    listDeliveries.mockRejectedValue(
      new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }),
    )

    const { viewModel } = await mountViewModel('/deliveries', useDeliveriesPage)

    expect(toValue(viewModel.loading)).toBe(false)
    expect(toValue(viewModel.error)).toContain('The server could not handle the request (HTTP 500).')
    expect(toValue(viewModel.error)).toContain('Database unavailable')
    expect(toValue(viewModel.rows)).toEqual([])
  })

  it('clears the error when loading again succeeds (S-2 AC 10)', async () => {
    listDeliveries
      .mockRejectedValueOnce(new ApiError(503, 'unavailable', { error: 'unavailable' }))
      .mockResolvedValueOnce([listRow()])

    const { viewModel } = await mountViewModel('/deliveries', useDeliveriesPage)
    await viewModel.load()

    expect(toValue(viewModel.error)).toBeNull()
    expect(toValue(viewModel.rows)).toEqual([listRow()])
  })

  it('loads the list again with the filters of a new URL (S-2 AC 10)', async () => {
    listDeliveries.mockResolvedValueOnce([listRow(), vietnamAir]).mockResolvedValueOnce([vietnamAir])

    const { viewModel, router } = await mountViewModel('/deliveries', useDeliveriesPage)
    await router.replace('/deliveries?transport_method=AIR')
    await flushPromises()

    expect(listDeliveries).toHaveBeenLastCalledWith({
      transport_method: 'AIR',
      sort: 'shipping_date',
      order: 'desc',
    })
    expect(toValue(viewModel.rows)).toEqual([vietnamAir])
  })

  describe('stale responses', () => {
    it('keeps the newer rows when a superseded list arrives late (S-2 AC 5, AC 10)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      const byAir = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockReturnValueOnce(byAir.promise)

      const { viewModel, router } = await mountViewModel('/deliveries', useDeliveriesPage)
      await router.replace('/deliveries?transport_method=AIR')
      await flushPromises()
      byAir.resolve([vietnamAir])
      await flushPromises()
      unfiltered.resolve([listRow(), vietnamAir])
      await flushPromises()

      expect(toValue(viewModel.rows)).toEqual([vietnamAir])
      expect(toValue(viewModel.loading)).toBe(false)
    })

    it('stays loading when a superseded list arrives before the newer one (S-2 AC 5, AC 10)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      const byAir = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockReturnValueOnce(byAir.promise)

      const { viewModel, router } = await mountViewModel('/deliveries', useDeliveriesPage)
      await router.replace('/deliveries?transport_method=AIR')
      await flushPromises()
      unfiltered.resolve([listRow(), vietnamAir])
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(true)
      expect(toValue(viewModel.rows)).not.toContainEqual(listRow())

      byAir.resolve([vietnamAir])
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.rows)).toEqual([vietnamAir])
    })

    it('ignores the error of a superseded list request (S-2 AC 5, AC 10)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockResolvedValueOnce([vietnamAir])

      const { viewModel, router } = await mountViewModel('/deliveries', useDeliveriesPage)
      await router.replace('/deliveries?transport_method=AIR')
      await flushPromises()
      unfiltered.reject(new ApiError(500, 'internal', { error: 'internal' }))
      await flushPromises()

      expect(toValue(viewModel.error)).toBeNull()
      expect(toValue(viewModel.rows)).toEqual([vietnamAir])
    })
  })
})

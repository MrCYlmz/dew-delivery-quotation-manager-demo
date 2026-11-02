import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryListRow } from '../../src/api/types'
import { button, control, link, renderApp, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { deliveryDetail, listRow } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  listDeliveries: vi.fn(),
  getDelivery: vi.fn(),
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
  destination_country: 'FR',
  destination_city: 'Lyon',
  transport_method: 'AIR',
  shipping_date: '2026-03-12',
  arrival_date: null,
  cost_eur: 0,
  volume_m3: null,
  gross_weight_kg: null,
  pallet_count: null,
  has_notes: false,
  pdf_count: 0,
})

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
})

afterEach(unmountAll)

describe('DeliveriesPage', () => {
  it('shows a loading message until the list arrives (S-1 AC 4)', async () => {
    listDeliveries.mockReturnValue(new Promise<DeliveryListRow[]>(() => {}))

    const { wrapper } = await renderApp('/deliveries')

    expect(wrapper.text()).toContain('Loading deliveries…')
  })

  it('renders every delivery the API returns, formatted for display (S-1 AC 4)', async () => {
    listDeliveries.mockResolvedValue([listRow(), vietnamAir])

    const { wrapper } = await renderApp('/deliveries')

    expect(listDeliveries).toHaveBeenCalledWith({ sort: 'shipping_date', order: 'desc' })
    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)

    const first = rows[0].text()
    expect(first).toContain('Example Interiors')
    expect(first).toContain('notes')
    expect(first).toContain('Ningbo, China')
    expect(first).toContain('Munich, Germany')
    expect(first).toContain('Sea')
    expect(first).toContain('15 Jan 2026')
    expect(first).toContain('02 Mar 2026')
    expect(first).toContain('€4,210.00')
    expect(first).toContain('7.42')
    expect(first).toContain('1,580')

    const second = rows[1].text()
    expect(second).toContain('Nordlicht Interiors')
    expect(second).not.toContain('notes')
    expect(second).toContain('Ho Chi Minh City, Vietnam')
    expect(second).toContain('Lyon, France')
    expect(second).toContain('Air')
    expect(second).toContain('€0.00')
    expect(second).toContain('—')

    expect(wrapper.text()).toContain('2 deliveries')
  })

  it('shows the empty archive state with a link to add the first delivery (S-1 AC 4)', async () => {
    listDeliveries.mockResolvedValue([])

    const { wrapper, router } = await renderApp('/deliveries')

    expect(wrapper.find('tbody').exists()).toBe(false)
    expect(wrapper.text()).toContain('No deliveries recorded yet.')

    await link(wrapper, 'Add the first one.').trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/new'))
  })

  it('refetches with the chosen filter and shows the narrowed list (S-1 AC 4)', async () => {
    listDeliveries.mockResolvedValueOnce([listRow(), vietnamAir]).mockResolvedValueOnce([vietnamAir])

    const { wrapper, router } = await renderApp('/deliveries')
    await control(wrapper, 'Method').setValue('AIR')

    await vi.waitFor(() =>
      expect(listDeliveries).toHaveBeenLastCalledWith({
        transport_method: 'AIR',
        sort: 'shipping_date',
        order: 'desc',
      }),
    )
    await vi.waitFor(() => expect(wrapper.findAll('tbody tr')).toHaveLength(1))
    expect(wrapper.find('tbody').text()).toContain('Nordlicht Interiors')
    expect(wrapper.find('tbody').text()).not.toContain('Example Interiors')
    expect(wrapper.text()).toContain('Method: Air')
    expect(router.currentRoute.value.path).toBe('/deliveries')
    expect(router.currentRoute.value.query).toEqual({ transport_method: 'AIR' })
  })

  it('applies filters from the URL and says when nothing matches them (S-1 AC 4)', async () => {
    listDeliveries.mockResolvedValue([])

    const { wrapper } = await renderApp('/deliveries?transport_method=RAIL&cost_min=0')

    expect(listDeliveries).toHaveBeenCalledWith({
      transport_method: 'RAIL',
      cost_min: '0',
      sort: 'shipping_date',
      order: 'desc',
    })
    expect(wrapper.text()).toContain('No delivery matches these 2 filters.')
    expect(wrapper.text()).not.toContain('No deliveries recorded yet.')
  })

  it('opens a delivery when its row is clicked (S-1 AC 4)', async () => {
    listDeliveries.mockResolvedValue([listRow(), vietnamAir])
    vi.mocked(client.getDelivery).mockResolvedValue(deliveryDetail())

    const { wrapper, router } = await renderApp('/deliveries')
    await wrapper.find('[aria-label="Open delivery for Example Interiors"]').trigger('click')

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/1'))
    expect(client.getDelivery).toHaveBeenCalledWith(1)
  })

  it('shows the API error instead of an empty list (S-1 AC 4)', async () => {
    listDeliveries.mockRejectedValue(
      new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }),
    )

    const { wrapper } = await renderApp('/deliveries')

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('The server could not handle the request (HTTP 500).')
    expect(alert.text()).toContain('Database unavailable')
    expect(wrapper.text()).toContain('This is not an empty archive.')
    expect(wrapper.text()).not.toContain('No deliveries recorded yet.')
    expect(wrapper.find('tbody').exists()).toBe(false)
  })

  it('clears the earlier list when a filtered refetch fails (S-1 AC 4)', async () => {
    listDeliveries
      .mockResolvedValueOnce([listRow(), vietnamAir])
      .mockRejectedValueOnce(new ApiError(500, 'internal', { error: 'internal' }))

    const { wrapper } = await renderApp('/deliveries')
    await control(wrapper, 'Method').setValue('AIR')

    await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))
    expect(wrapper.find('tbody').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Example Interiors')
  })

  it('loads the list again when Alice retries after an error (S-1 AC 4)', async () => {
    listDeliveries
      .mockRejectedValueOnce(new ApiError(503, 'unavailable', { error: 'unavailable' }))
      .mockResolvedValueOnce([listRow()])

    const { wrapper } = await renderApp('/deliveries')
    await button(wrapper, 'Try again').trigger('click')

    await vi.waitFor(() => expect(wrapper.findAll('tbody tr')).toHaveLength(1))
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('1 delivery')
  })

  describe('status and real price', () => {
    const onTheWay = listRow({
      id: 3,
      customer_company: 'Valmora Ceramics',
      origin_city: 'Chaozhou',
      destination_country: 'IT',
      destination_city: 'Milan',
      transport_method: 'ROAD',
      status: 'ON_THE_WAY',
      shipping_date: '2026-10-27',
      arrival_date: '2026-11-09',
      cost_eur: null,
    })
    const toDo = listRow({
      id: 4,
      customer_company: 'Sun Garden Furniture',
      status: 'TO_DO',
      shipping_date: null,
      arrival_date: null,
      cost_eur: null,
    })

    function chip(wrapper: VueWrapper, text: string) {
      const match = wrapper.findAll('button').find((b) => b.text().includes(text))
      if (!match) throw new Error(`No filter chip "${text}"`)
      return match
    }

    it('shows the status of each delivery in its row (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow(), onTheWay, toDo])

      const { wrapper } = await renderApp('/deliveries')

      const headers = wrapper.findAll('thead th').map((th) => th.text())
      expect(headers).toContain('Status')
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('Delivered')
      expect(rows[1].text()).toContain('On the way')
      expect(rows[2].text()).toContain('To do')
    })

    it('shows a missing real price and missing dates as "—", not as zero (S-9 AC 3, AC 4)', async () => {
      listDeliveries.mockResolvedValue([onTheWay, toDo])

      const { wrapper } = await renderApp('/deliveries')

      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('—')
      expect(rows[0].text()).not.toContain('€')
      expect(rows[1].text()).not.toContain('€')
      expect(rows[1].text().match(/—/g)?.length).toBeGreaterThanOrEqual(3)
      expect(wrapper.text()).not.toMatch(/null|undefined|NaN|01 Jan 1970/)
    })

    it('shows the real arrival in the Arrived column, empty for a delivery not yet arrived (S-10 AC 17)', async () => {
      listDeliveries.mockResolvedValue([listRow(), { ...onTheWay, arrival_date: null }])

      const { wrapper } = await renderApp('/deliveries')

      const headers = wrapper.findAll('thead th').map((th) => th.text())
      const arrived = headers.findIndex((h) => h.startsWith('Arrived'))
      expect(arrived).toBeGreaterThanOrEqual(0)
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].findAll('td')[arrived].text()).toBe('02 Mar 2026')
      expect(rows[1].findAll('td')[arrived].text()).toBe('—')
      expect(wrapper.find('tbody').text()).not.toMatch(/expected/i)
    })

    it('filters and sorts on the real arrival through the Arrived filter and column (S-10 AC 17)', async () => {
      listDeliveries.mockResolvedValue([listRow()])

      const { wrapper } = await renderApp('/deliveries')
      await control(wrapper, 'Arrived from').setValue('2026-08-01')
      await control(wrapper, 'Arrived from').trigger('change')

      await vi.waitFor(() =>
        expect(listDeliveries).toHaveBeenLastCalledWith(
          expect.objectContaining({ arrival_date_from: '2026-08-01' }),
        ),
      )

      await button(wrapper, 'Arrived').trigger('click')

      await vi.waitFor(() =>
        expect(listDeliveries).toHaveBeenLastCalledWith(
          expect.objectContaining({ arrival_date_from: '2026-08-01', sort: 'arrival_date' }),
        ),
      )
    })

    it('labels the cost column and its filter "Real price" (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow()])

      const { wrapper } = await renderApp('/deliveries?cost_min=1000')

      const headers = wrapper.findAll('thead th').map((th) => th.text())
      expect(headers.some((h) => h.startsWith('Real price'))).toBe(true)
      expect(wrapper.text()).not.toMatch(/\bCost\b/i)
    })

    it('offers every status as a filter, none ticked at first (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow()])

      const { wrapper } = await renderApp('/deliveries')

      for (const status of ['To do', 'Offers received', 'Offer accepted', 'On the way', 'Delivered']) {
        expect(control(wrapper, status).element.checked, status).toBe(false)
      }
      expect(listDeliveries).toHaveBeenCalledWith({ sort: 'shipping_date', order: 'desc' })
    })

    it('filters by several ticked statuses at once and keeps them in the URL (S-9 AC 20, AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow(), onTheWay])

      const { wrapper, router } = await renderApp('/deliveries')
      await control(wrapper, 'On the way').setValue(true)
      await control(wrapper, 'Delivered').setValue(true)

      await vi.waitFor(() =>
        expect(listDeliveries).toHaveBeenLastCalledWith({
          status: ['ON_THE_WAY', 'DELIVERED'],
          sort: 'shipping_date',
          order: 'desc',
        }),
      )
      expect(router.currentRoute.value.path).toBe('/deliveries')
      expect(router.currentRoute.value.query).toEqual({ status: ['ON_THE_WAY', 'DELIVERED'] })
      expect(wrapper.text()).toContain('Status: On the way')
      expect(wrapper.text()).toContain('Status: Delivered')
    })

    it('reads the ticked statuses from the URL and combines them with the other filters (S-9 AC 20, AC 21)', async () => {
      listDeliveries.mockResolvedValue([onTheWay])

      const { wrapper } = await renderApp(
        '/deliveries?status=ON_THE_WAY&status=DELIVERED&transport_method=SEA',
      )

      expect(listDeliveries).toHaveBeenCalledWith({
        transport_method: 'SEA',
        status: ['ON_THE_WAY', 'DELIVERED'],
        sort: 'shipping_date',
        order: 'desc',
      })
      expect(control(wrapper, 'On the way').element.checked).toBe(true)
      expect(control(wrapper, 'Delivered').element.checked).toBe(true)
      expect(control(wrapper, 'To do').element.checked).toBe(false)
    })

    it('reads a single ticked status from the URL (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([toDo])

      await renderApp('/deliveries?status=TO_DO')

      expect(listDeliveries).toHaveBeenCalledWith({
        status: ['TO_DO'],
        sort: 'shipping_date',
        order: 'desc',
      })
    })

    it('drops an unknown status from the URL and keeps the known ones (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([onTheWay])

      const { wrapper } = await renderApp('/deliveries?status=LOST&status=ON_THE_WAY&status=on_the_way')

      expect(listDeliveries).toHaveBeenCalledWith({
        status: ['ON_THE_WAY'],
        sort: 'shipping_date',
        order: 'desc',
      })
      expect(control(wrapper, 'On the way').element.checked).toBe(true)
      expect(wrapper.text()).not.toContain('LOST')
    })

    it('shows every status when the URL holds only an unknown or blank status (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow(), onTheWay, toDo])

      const { wrapper } = await renderApp('/deliveries?status=LOST&status=')

      expect(listDeliveries).toHaveBeenCalledWith({ sort: 'shipping_date', order: 'desc' })
      for (const status of ['To do', 'Offers received', 'Offer accepted', 'On the way', 'Delivered']) {
        expect(control(wrapper, status).element.checked, status).toBe(false)
      }
      expect(wrapper.text()).not.toContain('Status:')
    })

    it('shows every status again once the last status is unticked (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow(), onTheWay, toDo])

      const { wrapper, router } = await renderApp('/deliveries?status=ON_THE_WAY')
      await control(wrapper, 'On the way').setValue(false)

      await vi.waitFor(() =>
        expect(listDeliveries).toHaveBeenLastCalledWith({ sort: 'shipping_date', order: 'desc' }),
      )
      expect(router.currentRoute.value.query).toEqual({})
    })

    it('removes one status from the filter through its chip (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([listRow(), onTheWay])

      const { wrapper, router } = await renderApp('/deliveries?status=ON_THE_WAY&status=DELIVERED')
      await chip(wrapper, 'Status: On the way').trigger('click')

      await vi.waitFor(() =>
        expect(listDeliveries).toHaveBeenLastCalledWith({
          status: ['DELIVERED'],
          sort: 'shipping_date',
          order: 'desc',
        }),
      )
      expect(control(wrapper, 'On the way').element.checked).toBe(false)
      expect(control(wrapper, 'Delivered').element.checked).toBe(true)
      expect([router.currentRoute.value.query.status].flat()).toEqual(['DELIVERED'])
    })

    it('counts the status filter as one filter when nothing matches (S-9 AC 21)', async () => {
      listDeliveries.mockResolvedValue([])

      const { wrapper } = await renderApp(
        '/deliveries?status=ON_THE_WAY&status=DELIVERED&transport_method=SEA',
      )

      expect(wrapper.text()).toContain('No delivery matches these 2 filters.')
    })
  })

  describe('overlapping requests', () => {
    it('keeps the newer filter\'s rows when the earlier list arrives late (S-2 AC 5)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      const byAir = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockReturnValueOnce(byAir.promise)

      const { wrapper } = await renderApp('/deliveries')
      await control(wrapper, 'Method').setValue('AIR')
      await vi.waitFor(() => expect(listDeliveries).toHaveBeenCalledTimes(2))

      byAir.resolve([vietnamAir])
      await flushPromises()
      unfiltered.resolve([listRow(), vietnamAir])
      await flushPromises()

      expect(wrapper.findAll('tbody tr')).toHaveLength(1)
      expect(wrapper.find('tbody').text()).toContain('Nordlicht Interiors')
      expect(wrapper.text()).not.toContain('Example Interiors')
      expect(wrapper.text()).toContain('1 delivery')
    })

    it('keeps loading when the earlier list arrives before the newer one (S-2 AC 5)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      const byAir = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockReturnValueOnce(byAir.promise)

      const { wrapper } = await renderApp('/deliveries')
      await control(wrapper, 'Method').setValue('AIR')
      await vi.waitFor(() => expect(listDeliveries).toHaveBeenCalledTimes(2))

      unfiltered.resolve([listRow(), vietnamAir])
      await flushPromises()

      expect(wrapper.text()).toContain('Loading deliveries…')
      expect(wrapper.find('tbody').exists()).toBe(false)

      byAir.resolve([vietnamAir])
      await flushPromises()

      expect(wrapper.findAll('tbody tr')).toHaveLength(1)
      expect(wrapper.text()).not.toContain('Example Interiors')
    })

    it('ignores the error of a list request a newer filter replaced (S-2 AC 5)', async () => {
      const unfiltered = deferred<DeliveryListRow[]>()
      listDeliveries.mockReturnValueOnce(unfiltered.promise).mockResolvedValueOnce([vietnamAir])

      const { wrapper } = await renderApp('/deliveries')
      await control(wrapper, 'Method').setValue('AIR')
      await vi.waitFor(() => expect(wrapper.findAll('tbody tr')).toHaveLength(1))

      unfiltered.reject(new ApiError(500, 'internal', { error: 'internal' }))
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.findAll('tbody tr')).toHaveLength(1)
      expect(wrapper.find('tbody').text()).toContain('Nordlicht Interiors')
    })
  })
})

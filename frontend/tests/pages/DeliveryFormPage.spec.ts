import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import type { Router } from 'vue-router'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryDetail, ParsedPackingList } from '../../src/api/types'
import {
  button,
  control,
  fieldOf,
  link,
  pickFiles,
  renderApp,
  section,
  unmountAll,
} from '../support/app'
import { deferred } from '../support/deferred'
import {
  deliveryDetail,
  deliveryItem,
  deliveryPdf,
  parsedPackingList,
} from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  listDeliveries: vi.fn(),
  getDelivery: vi.fn(),
  createDelivery: vi.fn(),
  updateDelivery: vi.fn(),
  deleteDelivery: vi.fn(),
  uploadPdfs: vi.fn(),
  deletePdf: vi.fn(),
  uploadExcel: vi.fn(),
  parseExcel: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const createDelivery = vi.mocked(client.createDelivery)
const updateDelivery = vi.mocked(client.updateDelivery)
const getDelivery = vi.mocked(client.getDelivery)

const created = deliveryDetail({ id: 42 })

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.suggestCompanies).mockResolvedValue(['Example Interiors'])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
  vi.mocked(client.listDeliveries).mockResolvedValue([])
  createDelivery.mockResolvedValue(created)
  getDelivery.mockResolvedValue(created)
})

afterEach(unmountAll)

async function fillRequiredFields(wrapper: VueWrapper) {
  await control(wrapper, 'Customer company').setValue('Nordlicht Interiors')
  await control(wrapper, 'Transport method').setValue('AIR')
  await control(wrapper, 'Shipping date').setValue('2026-03-12')
  await control(wrapper, 'Real price (EUR)').setValue('1250,5')
  const origin = section(wrapper, 'Origin')
  await control(origin, 'Country').setValue('VN')
  await control(origin, 'City').setValue('Ho Chi Minh City')
  const destination = section(wrapper, 'Destination')
  await control(destination, 'Country').setValue('FR')
  await control(destination, 'City').setValue('Lyon')
}

async function submit(wrapper: VueWrapper, label: string) {
  await button(wrapper, label).trigger('click')
}

describe('DeliveryFormPage', () => {
  describe('create and edit', () => {
    it('opens an empty form for a new delivery (S-1 AC 5)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')

      expect(wrapper.find('h1').text()).toBe('New delivery')
      expect(control(wrapper, 'Customer company').element.value).toBe('')
      expect(control<HTMLSelectElement>(wrapper, 'Transport method').element.value).toBe('')
      expect(control(wrapper, 'Real price (EUR)').element.value).toBe('')
      expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('')
      expect(button(wrapper, 'Create delivery').exists()).toBe(true)
      expect(wrapper.findAll('button').map((b) => b.text())).not.toContain('Delete delivery')
      expect(getDelivery).not.toHaveBeenCalled()
    })

    it('shows a loading message while the record to edit is fetched (S-1 AC 5)', async () => {
      getDelivery.mockReturnValue(new Promise<DeliveryDetail>(() => {}))

      const { wrapper } = await renderApp('/deliveries/1/edit')

      expect(wrapper.text()).toContain('Loading delivery…')
    })

    it('pre-fills the form with the existing record when editing (S-1 AC 5, S-10 AC 7)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ expected_arrival_date: '2026-08-15' }))

      const { wrapper } = await renderApp('/deliveries/1/edit')

      expect(getDelivery).toHaveBeenCalledWith(1)
      expect(wrapper.find('h1').text()).toBe('Edit delivery')
      expect(control(wrapper, 'Customer company').element.value).toBe('Example Interiors')
      expect(control<HTMLSelectElement>(wrapper, 'Transport method').element.value).toBe('SEA')
      expect(control(wrapper, 'Shipping date').element.value).toBe('2026-01-15')
      expect(control(wrapper, 'Expected arrival').element.value).toBe('2026-08-15')
      expect(control(wrapper, 'Arrival').element.value).toBe('2026-03-02')
      expect(control(wrapper, 'Real price (EUR)').element.value).toBe('4210')

      const origin = section(wrapper, 'Origin')
      const originCountry = control<HTMLSelectElement>(origin, 'Country').element
      expect(originCountry.value).toBe('CN')
      expect(originCountry.selectedOptions[0].text.trim()).toBe('China')
      expect(control(origin, 'City').element.value).toBe('Ningbo')
      expect(control(origin, 'Postal code').element.value).toBe('315000')
      expect(control(origin, 'Street').element.value).toBe('88 Harbour Road')

      const destination = section(wrapper, 'Destination')
      expect(control<HTMLSelectElement>(destination, 'Country').element.value).toBe('DE')
      expect(control(destination, 'City').element.value).toBe('Munich')
      expect(control(destination, 'Postal code').element.value).toBe('80331')
      expect(control(destination, 'Street').element.value).toBe('Marienplatz 1')

      const cargo = section(wrapper, 'Cargo totals')
      expect(control(cargo, 'Volume (m³)').element.value).toBe('7.42')
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('1580')
      expect(control(cargo, 'Pallets').element.value).toBe('6')

      expect(control<HTMLTextAreaElement>(wrapper, 'Notes about this delivery').element.value).toBe(
        'Price included a tail-lift truck for the last mile.\nCustomer paid the port storage.',
      )
      expect(section(wrapper, 'Packing list (Excel)').text()).toContain(
        'QX20260115 packing list.xlsx',
      )
      expect(section(wrapper, 'Delivery documents (PDF)').text()).toContain(
        'QX20260115 delivery note.pdf',
      )
      expect(button(wrapper, 'Save changes').exists()).toBe(true)
    })

    it('shows the API error when the record to edit cannot be loaded (S-1 AC 5)', async () => {
      getDelivery.mockRejectedValue(new ApiError(404, 'not_found', { error: 'not_found' }))

      const { wrapper } = await renderApp('/deliveries/999/edit')

      expect(wrapper.find('[role="alert"]').text()).toContain('Not found.')
    })
  })

  describe('validation', () => {
    it('blocks submit and marks every empty required field (S-1 AC 5, S-9 AC 3, AC 4)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/deliveries/new')
      expect(wrapper.find('[role="alert"]').text()).toContain('Some fields need attention')
      for (const label of ['Customer company', 'Transport method']) {
        expect(fieldOf(wrapper, label).text(), label).toContain('Required')
      }
      for (const label of ['Shipping date', 'Expected arrival', 'Arrival', 'Offer price (EUR)', 'Real price (EUR)']) {
        expect(fieldOf(wrapper, label).text(), label).not.toMatch(/required/i)
      }
      for (const side of ['Origin', 'Destination']) {
        expect(fieldOf(section(wrapper, side), 'Country').text(), side).toContain('Required')
        expect(fieldOf(section(wrapper, side), 'City').text(), side).toContain('Required')
      }
      expect(fieldOf(section(wrapper, 'Origin'), 'Street').text()).not.toContain('Required')
    })

    it('blocks submit on a real price that is not a number (S-1 AC 5, S-9 AC 3)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillRequiredFields(wrapper)
      await control(wrapper, 'Real price (EUR)').setValue('about 1200')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(fieldOf(wrapper, 'Real price (EUR)').text()).toContain('must be a number')
    })

    it('blocks submit on a negative cargo total (S-1 AC 5)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillRequiredFields(wrapper)
      await control(section(wrapper, 'Cargo totals'), 'Volume (m³)').setValue('-1')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(fieldOf(section(wrapper, 'Cargo totals'), 'Volume (m³)').text()).toContain(
        'Volume cannot be negative',
      )
    })

    it('blocks submit when arrival precedes shipping (S-1 AC 5)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillRequiredFields(wrapper)
      await control(wrapper, 'Arrival').setValue('2026-03-01')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(fieldOf(wrapper, 'Arrival').text()).toContain('Arrival cannot precede shipping')
    })
  })

  describe('submit', () => {
    it('sends the entered values and opens the created record (S-1 AC 5, S-9 AC 18, S-10 AC 1, AC 7)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')
      await fillRequiredFields(wrapper)
      await control(wrapper, 'Expected arrival').setValue('2026-03-13')
      await control(wrapper, 'Arrival').setValue('2026-03-14')
      await control(section(wrapper, 'Destination'), 'Postal code').setValue('69002')
      await control(section(wrapper, 'Destination'), 'Street').setValue('12 Rue de la République')
      await control(section(wrapper, 'Cargo totals'), 'Pallets').setValue('0')
      await control(wrapper, 'Notes about this delivery').setValue('Urgent, flown in two lots.')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith({
        customer_company: 'Nordlicht Interiors',
        origin_country: 'VN',
        origin_city: 'Ho Chi Minh City',
        origin_postal_code: null,
        origin_street: null,
        destination_country: 'FR',
        destination_city: 'Lyon',
        destination_postal_code: '69002',
        destination_street: '12 Rue de la République',
        transport_method: 'AIR',
        status: 'TO_DO',
        shipping_date: '2026-03-12',
        expected_arrival_date: '2026-03-13',
        arrival_date: '2026-03-14',
        offer_price_eur: null,
        cost_eur: 1250.5,
        volume_m3: null,
        gross_weight_kg: null,
        pallet_count: 0,
        notes: 'Urgent, flown in two lots.',
      })
      expect(client.uploadExcel).not.toHaveBeenCalled()
      expect(client.uploadPdfs).not.toHaveBeenCalled()
      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/42'))
    })

    it('saves an edited record and opens it (S-1 AC 5, S-9 AC 18, S-10 AC 1, AC 7)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ expected_arrival_date: '2026-08-15' }))
      updateDelivery.mockResolvedValue(deliveryDetail({ cost_eur: 4500 }))

      const { wrapper, router } = await renderApp('/deliveries/1/edit')
      await control(wrapper, 'Real price (EUR)').setValue('4500')
      await submit(wrapper, 'Save changes')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(updateDelivery).toHaveBeenCalledWith(1, {
        customer_company: 'Example Interiors',
        origin_country: 'CN',
        origin_city: 'Ningbo',
        origin_postal_code: '315000',
        origin_street: '88 Harbour Road',
        destination_country: 'DE',
        destination_city: 'Munich',
        destination_postal_code: '80331',
        destination_street: 'Marienplatz 1',
        transport_method: 'SEA',
        status: 'DELIVERED',
        shipping_date: '2026-01-15',
        expected_arrival_date: '2026-08-15',
        arrival_date: '2026-03-02',
        offer_price_eur: null,
        cost_eur: 4500,
        volume_m3: 7.42,
        gross_weight_kg: 1580,
        pallet_count: 6,
        notes: 'Price included a tail-lift truck for the last mile.\nCustomer paid the port storage.',
      })
      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/1'))
    })

    it('shows the field errors of a 400 response at their fields (S-1 AC 5)', async () => {
      createDelivery.mockRejectedValue(
        new ApiError(400, 'validation', {
          error: 'validation',
          fields: { customer_company: 'must be at most 120 characters' },
        }),
      )

      const { wrapper, router } = await renderApp('/deliveries/new')
      await fillRequiredFields(wrapper)
      await submit(wrapper, 'Create delivery')

      await vi.waitFor(() =>
        expect(fieldOf(wrapper, 'Customer company').text()).toContain(
          'must be at most 120 characters',
        ),
      )
      expect(control(wrapper, 'Customer company').attributes('aria-invalid')).toBe('true')
      expect(fieldOf(wrapper, 'Real price (EUR)').text()).not.toContain('must be')
      expect(wrapper.find('[role="alert"]').text()).toContain('The server rejected the data.')
      expect(router.currentRoute.value.fullPath).toBe('/deliveries/new')
      expect(button(wrapper, 'Create delivery').element.disabled).toBe(false)
    })
  })

  describe('status and offer price', () => {
    async function fillDeliveryToDo(wrapper: VueWrapper) {
      await control(wrapper, 'Customer company').setValue('Sun Garden Furniture')
      await control(wrapper, 'Transport method').setValue('SEA')
      const origin = section(wrapper, 'Origin')
      await control(origin, 'Country').setValue('CN')
      await control(origin, 'City').setValue('Foshan')
      const destination = section(wrapper, 'Destination')
      await control(destination, 'Country').setValue('NL')
      await control(destination, 'City').setValue('Rotterdam')
    }

    it('opens a new delivery at To do, with every status to pick in board order (S-9 AC 1, AC 18)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')

      const status = control<HTMLSelectElement>(wrapper, 'Status')
      expect(status.element.value).toBe('TO_DO')
      expect(status.findAll('option').map((o) => [o.element.value, o.text()])).toEqual([
        ['TO_DO', 'To do'],
        ['OFFERS_RECEIVED', 'Offers received'],
        ['OFFER_ACCEPTED', 'Offer accepted'],
        ['ON_THE_WAY', 'On the way'],
        ['DELIVERED', 'Delivered'],
      ])
      expect(control(wrapper, 'Offer price (EUR)').element.value).toBe('')
    })

    it('saves a To do delivery without dates, offer price or real price (S-9 AC 2, AC 3, AC 4, AC 18)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({
          customer_company: 'Sun Garden Furniture',
          status: 'TO_DO',
          shipping_date: null,
          expected_arrival_date: null,
          arrival_date: null,
          offer_price_eur: null,
          cost_eur: null,
        }),
      )
    })

    it('sends the chosen status and the offer price (S-9 AC 2, AC 18)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('OFFER_ACCEPTED')
      await control(wrapper, 'Offer price (EUR)').setValue('3900,5')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'OFFER_ACCEPTED', offer_price_eur: 3900.5, cost_eur: null }),
      )
    })

    it('blocks submit on an offer price that is not a number (S-9 AC 2)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Offer price (EUR)').setValue('about 3900')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(fieldOf(wrapper, 'Offer price (EUR)').text()).toContain('must be a number')
    })

    it('refuses a save at On the way without a shipping date, naming it, and asks for no arrival (S-10 AC 2, AC 7)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('ON_THE_WAY')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/deliveries/new')
      expect(fieldOf(wrapper, 'Shipping date').text()).toContain('Shipping date is required at On the way.')
      expect(fieldOf(wrapper, 'Expected arrival').text()).not.toMatch(/required/i)
      expect(fieldOf(wrapper, 'Arrival').text()).not.toMatch(/required/i)
      expect(fieldOf(wrapper, 'Real price (EUR)').text()).not.toMatch(/required/i)
    })

    it('saves at On the way with the shipping date only, both arrivals empty (S-10 AC 2, AC 7)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('ON_THE_WAY')
      await control(wrapper, 'Shipping date').setValue('2026-10-27')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'ON_THE_WAY',
          shipping_date: '2026-10-27',
          expected_arrival_date: null,
          arrival_date: null,
        }),
      )
    })

    it('saves at On the way with the expected arrival (S-10 AC 1, AC 7)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('ON_THE_WAY')
      await control(wrapper, 'Shipping date').setValue('2026-10-27')
      await control(wrapper, 'Expected arrival').setValue('2026-11-09')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'ON_THE_WAY',
          shipping_date: '2026-10-27',
          expected_arrival_date: '2026-11-09',
          arrival_date: null,
        }),
      )
    })

    it('refuses a save at Delivered without dates, naming the shipping date and the arrival but not the expected one (S-10 AC 2, AC 7)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('DELIVERED')
      await control(wrapper, 'Expected arrival').setValue('2026-11-09')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/deliveries/new')
      expect(fieldOf(wrapper, 'Shipping date').text()).toContain('Shipping date is required at Delivered.')
      expect(fieldOf(wrapper, 'Arrival').text()).toContain('Arrival is required at Delivered.')
      expect(fieldOf(wrapper, 'Expected arrival').text()).not.toMatch(/required/i)
      expect(fieldOf(wrapper, 'Real price (EUR)').text()).not.toMatch(/required/i)
    })

    it('saves at Delivered with the shipping date and the arrival, without an expected arrival (S-10 AC 2, AC 7)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('DELIVERED')
      await control(wrapper, 'Shipping date').setValue('2026-10-27')
      await control(wrapper, 'Arrival').setValue('2026-11-09')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'DELIVERED',
          shipping_date: '2026-10-27',
          expected_arrival_date: null,
          arrival_date: '2026-11-09',
        }),
      )
    })

    for (const [status, label] of [
      ['TO_DO', 'To do'],
      ['OFFERS_RECEIVED', 'Offers received'],
      ['OFFER_ACCEPTED', 'Offer accepted'],
    ] as const) {
      it(`saves at ${label} without dates (S-9 AC 4, S-10 AC 2)`, async () => {
        const { wrapper } = await renderApp('/deliveries/new')
        await fillDeliveryToDo(wrapper)
        await control(wrapper, 'Status').setValue(status)

        await submit(wrapper, 'Create delivery')

        expect(createDelivery).toHaveBeenCalledWith(
          expect.objectContaining({ status, shipping_date: null, expected_arrival_date: null, arrival_date: null }),
        )
      })
    }

    it('asks for the arrival when a Delivered record without one is saved (S-9 AC 4, AC 6, S-10 AC 7)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({ status: 'DELIVERED', expected_arrival_date: '2026-08-15', arrival_date: null }),
      )

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await control(wrapper, 'Notes about this delivery').setValue('Port storage paid by us.')
      await submit(wrapper, 'Save changes')

      expect(updateDelivery).not.toHaveBeenCalled()
      expect(fieldOf(wrapper, 'Arrival').text()).toContain('Arrival is required at Delivered.')
      expect(fieldOf(wrapper, 'Shipping date').text()).not.toMatch(/required/i)
    })

    it('shows a missing date the server names at its field (S-9 AC 4)', async () => {
      updateDelivery.mockRejectedValue(
        new ApiError(400, 'validation', { error: 'validation', fields: { arrival_date: 'required' } }),
      )
      getDelivery.mockResolvedValue(deliveryDetail({ status: 'DELIVERED' }))

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await submit(wrapper, 'Save changes')

      await vi.waitFor(() => expect(fieldOf(wrapper, 'Arrival').text()).toMatch(/required/i))
      expect(control(wrapper, 'Arrival').attributes('aria-invalid')).toBe('true')
    })

    it('saves a Delivered record without a real price (S-9 AC 3)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail())
      updateDelivery.mockResolvedValue(deliveryDetail({ cost_eur: null }))

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await control(wrapper, 'Real price (EUR)').setValue('')
      await submit(wrapper, 'Save changes')

      expect(updateDelivery).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ status: 'DELIVERED', cost_eur: null }),
      )
    })

    it('pre-fills the status and the offer price when editing (S-9 AC 18)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({ status: 'ON_THE_WAY', offer_price_eur: 3900, cost_eur: null }),
      )

      const { wrapper } = await renderApp('/deliveries/1/edit')

      expect(control<HTMLSelectElement>(wrapper, 'Status').element.value).toBe('ON_THE_WAY')
      expect(control(wrapper, 'Offer price (EUR)').element.value).toBe('3900')
      expect(control(wrapper, 'Real price (EUR)').element.value).toBe('')
    })

    it('keeps the status and the offer price when an edit is saved unchanged (S-9 AC 18)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ status: 'ON_THE_WAY', offer_price_eur: 3900 }))
      updateDelivery.mockResolvedValue(deliveryDetail({ status: 'ON_THE_WAY', offer_price_eur: 3900 }))

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await submit(wrapper, 'Save changes')

      expect(updateDelivery).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ status: 'ON_THE_WAY', offer_price_eur: 3900 }),
      )
    })

    it('hints at a missing offer price from Offer accepted on (S-9 AC 12, AC 18)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')

      for (const status of ['TO_DO', 'OFFERS_RECEIVED']) {
        await control(wrapper, 'Status').setValue(status)
        expect(fieldOf(wrapper, 'Offer price (EUR)').text(), status).not.toContain('No offer price')
      }
      for (const status of ['OFFER_ACCEPTED', 'ON_THE_WAY', 'DELIVERED']) {
        await control(wrapper, 'Status').setValue(status)
        expect(fieldOf(wrapper, 'Offer price (EUR)').text(), status).toContain('No offer price')
      }

      await control(wrapper, 'Offer price (EUR)').setValue('3900')
      expect(fieldOf(wrapper, 'Offer price (EUR)').text()).not.toContain('No offer price')
    })

    it('saves without an offer price despite the hint (S-9 AC 12, AC 18)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      await fillDeliveryToDo(wrapper)
      await control(wrapper, 'Status').setValue('OFFER_ACCEPTED')
      expect(fieldOf(wrapper, 'Offer price (EUR)').text()).toContain('No offer price')

      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'OFFER_ACCEPTED', offer_price_eur: null }),
      )
    })
  })

  describe('leaving the form', () => {
    it('leads "Back to list" to the list at /deliveries (S-9 AC 20)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')

      await link(wrapper, 'Back to list').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
    })

    it('returns to the list at /deliveries when a new delivery is cancelled (S-9 AC 20)', async () => {
      const { wrapper, router } = await renderApp('/deliveries/new')

      await button(wrapper, 'Cancel').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
      expect(createDelivery).not.toHaveBeenCalled()
    })

    it('returns to the list at /deliveries after a delete (S-9 AC 20)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail())
      vi.mocked(client.deleteDelivery).mockResolvedValue(undefined)

      const { wrapper, router } = await renderApp('/deliveries/1/edit')
      await button(wrapper, 'Delete delivery').trigger('click')
      await button(wrapper, 'Yes, delete').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
    })
  })

  describe('Excel upload', () => {
    const packingList = new File(['xlsx'], 'QX20260115 packing list.xlsx')

    it('fills the cargo totals and reports the parsed item rows (S-1 AC 5)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())

      const { wrapper } = await renderApp('/deliveries/new')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])

      expect(client.parseExcel).toHaveBeenCalledWith(packingList)
      const excel = section(wrapper, 'Packing list (Excel)')
      await vi.waitFor(() =>
        expect(excel.text()).toMatch(/Read 2 item rows from sheet PACKING LIST/),
      )
      expect(excel.text()).toContain('Volume 7.42 m³')
      expect(excel.text()).toContain('Gross weight 1,580 kg')
      expect(excel.text()).toContain('Pallets 6')
      expect(excel.text()).toContain('Every row with dimensions passes the volume cross-check.')

      const cargo = section(wrapper, 'Cargo totals')
      expect(control(cargo, 'Volume (m³)').element.value).toBe('7.42')
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('1580')
      expect(control(cargo, 'Pallets').element.value).toBe('6')
      expect(fieldOf(cargo, 'Volume (m³)').text()).toContain('Parsed from the Excel: 7.42')
    })

    it('reports rows that fail the volume cross-check without blocking (S-1 AC 5)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList({ volume_mismatch_count: 1 }))

      const { wrapper } = await renderApp('/deliveries/new')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])

      await vi.waitFor(() =>
        expect(section(wrapper, 'Packing list (Excel)').text()).toContain(
          '1 row states a volume that disagrees',
        ),
      )
    })

    it('saves overridden totals and uploads the Excel to the saved record (S-1 AC 5)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())
      vi.mocked(client.uploadExcel).mockResolvedValue(created)

      const { wrapper, router } = await renderApp('/deliveries/new')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
      const cargo = section(wrapper, 'Cargo totals')
      await vi.waitFor(() => expect(control(cargo, 'Volume (m³)').element.value).toBe('7.42'))
      await control(cargo, 'Volume (m³)').setValue('14')
      expect(fieldOf(cargo, 'Volume (m³)').text()).toContain('you have overridden it')

      await fillRequiredFields(wrapper)
      await submit(wrapper, 'Create delivery')

      expect(createDelivery).toHaveBeenCalledWith(
        expect.objectContaining({ volume_m3: 14, gross_weight_kg: 1580, pallet_count: 6 }),
      )
      await vi.waitFor(() => expect(client.uploadExcel).toHaveBeenCalledWith(42, packingList))
      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/42'))
    })

    it('shows a 422 parse failure and leaves the totals untouched (S-1 AC 5)', async () => {
      vi.mocked(client.parseExcel).mockRejectedValue(
        new ApiError(422, 'parse_failed', {
          error: 'parse_failed',
          detail: 'No sheet has a header row with the columns CTNS and G.W.',
        }),
      )

      const { wrapper } = await renderApp('/deliveries/new')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])

      const excel = section(wrapper, 'Packing list (Excel)')
      await vi.waitFor(() => expect(excel.text()).toContain('That file could not be parsed.'))
      expect(excel.find('[role="alert"]').text()).toContain(
        'No sheet has a header row with the columns CTNS and G.W.',
      )
      const cargo = section(wrapper, 'Cargo totals')
      expect(control(cargo, 'Volume (m³)').element.value).toBe('')
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('')
      expect(control(cargo, 'Pallets').element.value).toBe('')
    })

    describe('after a 422 parse failure', () => {
      const parseFailure = () =>
        new ApiError(422, 'parse_failed', {
          error: 'parse_failed',
          detail: 'No sheet has a header row with the columns CTNS and G.W.',
        })

      it('creates the record without uploading the failed file (S-2 AC 9)', async () => {
        vi.mocked(client.parseExcel).mockRejectedValue(parseFailure())

        const { wrapper, router } = await renderApp('/deliveries/new')
        await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
        const excel = section(wrapper, 'Packing list (Excel)')
        await vi.waitFor(() => expect(excel.text()).toContain('That file could not be parsed.'))

        await fillRequiredFields(wrapper)
        expect(excel.find('[role="alert"]').text()).toContain(
          'No sheet has a header row with the columns CTNS and G.W.',
        )
        await submit(wrapper, 'Create delivery')

        await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/42'))
        expect(createDelivery).toHaveBeenCalledTimes(1)
        expect(client.uploadExcel).not.toHaveBeenCalled()
      })

      it('updates the record without uploading the failed file (S-2 AC 9)', async () => {
        getDelivery.mockResolvedValue(deliveryDetail())
        updateDelivery.mockResolvedValue(deliveryDetail())
        vi.mocked(client.parseExcel).mockRejectedValue(parseFailure())

        const { wrapper, router } = await renderApp('/deliveries/1/edit')
        await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
        await vi.waitFor(() =>
          expect(section(wrapper, 'Packing list (Excel)').text()).toContain(
            'That file could not be parsed.',
          ),
        )
        await submit(wrapper, 'Save changes')

        await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/1'))
        expect(updateDelivery).toHaveBeenCalledTimes(1)
        expect(client.uploadExcel).not.toHaveBeenCalled()
      })

      it('keeps the parse error visible until Alice picks another file (S-2 AC 9)', async () => {
        const fixedPackingList = new File(['xlsx'], 'QX20260115 packing list fixed.xlsx')
        vi.mocked(client.parseExcel)
          .mockRejectedValueOnce(parseFailure())
          .mockResolvedValueOnce(parsedPackingList())
        vi.mocked(client.uploadExcel).mockResolvedValue(created)

        const { wrapper } = await renderApp('/deliveries/new')
        const picker = control(wrapper, 'Packing list Excel file')
        await pickFiles(picker, [packingList])
        const excel = section(wrapper, 'Packing list (Excel)')
        await vi.waitFor(() => expect(excel.text()).toContain('That file could not be parsed.'))

        await fillRequiredFields(wrapper)
        expect(excel.text()).toContain('That file could not be parsed.')

        await pickFiles(picker, [fixedPackingList])
        await vi.waitFor(() =>
          expect(excel.text()).toMatch(/Read 2 item rows from sheet PACKING LIST/),
        )
        expect(excel.text()).not.toContain('That file could not be parsed.')
        expect(excel.find('[role="alert"]').exists()).toBe(false)

        await submit(wrapper, 'Create delivery')
        await vi.waitFor(() => expect(client.uploadExcel).toHaveBeenCalledWith(42, fixedPackingList))
      })
    })
  })

  describe('PDFs', () => {
    const deliveryNote = new File(['%PDF-1.4'], 'delivery note.pdf', { type: 'application/pdf' })
    const customsForm = new File(['%PDF-1.4 customs'], 'customs form.pdf', {
      type: 'application/pdf',
    })

    function fileEntry(wrapper: VueWrapper, filename: string) {
      const entry = section(wrapper, 'Delivery documents (PDF)')
        .findAll('li')
        .find((li) => li.text().includes(filename))
      if (!entry) throw new Error(`No PDF entry "${filename}"`)
      return entry
    }

    it('queues picked PDFs and uploads the remaining ones on save (S-1 AC 5)', async () => {
      vi.mocked(client.uploadPdfs).mockResolvedValue(undefined)

      const { wrapper, router } = await renderApp('/deliveries/new')
      const documents = section(wrapper, 'Delivery documents (PDF)')
      expect(documents.text()).toContain('No documents attached.')

      await pickFiles(control(wrapper, 'Delivery document PDFs'), [deliveryNote, customsForm])
      expect(documents.text()).toContain('2 files')
      expect(fileEntry(wrapper, 'delivery note.pdf').text()).toContain('uploads on save')
      expect(fileEntry(wrapper, 'customs form.pdf').text()).toContain('uploads on save')

      await button(fileEntry(wrapper, 'customs form.pdf'), 'Remove').trigger('click')
      expect(documents.text()).not.toContain('customs form.pdf')
      expect(documents.text()).toContain('1 file')

      await fillRequiredFields(wrapper)
      await submit(wrapper, 'Create delivery')

      await vi.waitFor(() =>
        expect(client.uploadPdfs).toHaveBeenCalledWith(42, [deliveryNote]),
      )
      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/42'))
    })

    it('keeps earlier picks when more PDFs are picked (S-1 AC 5)', async () => {
      const { wrapper } = await renderApp('/deliveries/new')
      const picker = control(wrapper, 'Delivery document PDFs')

      await pickFiles(picker, [deliveryNote])
      await pickFiles(picker, [customsForm])

      expect(section(wrapper, 'Delivery documents (PDF)').text()).toContain('2 files')
    })

    it('removes an attached PDF from the record straight away (S-1 AC 5)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({
          pdfs: [deliveryPdf(), deliveryPdf({ id: 8, original_filename: 'Bill of lading.pdf' })],
        }),
      )
      vi.mocked(client.deletePdf).mockResolvedValue(undefined)

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await button(fileEntry(wrapper, 'QX20260115 delivery note.pdf'), 'Remove').trigger('click')

      expect(client.deletePdf).toHaveBeenCalledWith(1, 7)
      const documents = section(wrapper, 'Delivery documents (PDF)')
      await vi.waitFor(() => expect(documents.text()).not.toContain('QX20260115 delivery note.pdf'))
      expect(documents.text()).toContain('Bill of lading.pdf')
      expect(documents.text()).toContain('1 file')
    })

    it('shows why an attached PDF could not be removed and keeps it listed (S-1 AC 5)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail())
      vi.mocked(client.deletePdf).mockRejectedValue(
        new ApiError(500, 'internal', { error: 'internal' }),
      )

      const { wrapper } = await renderApp('/deliveries/1/edit')
      await button(fileEntry(wrapper, 'QX20260115 delivery note.pdf'), 'Remove').trigger('click')

      const documents = section(wrapper, 'Delivery documents (PDF)')
      await vi.waitFor(() =>
        expect(documents.find('[role="alert"]').text()).toContain(
          'QX20260115 delivery note.pdf could not be removed.',
        ),
      )
      expect(fileEntry(wrapper, 'QX20260115 delivery note.pdf').exists()).toBe(true)
    })
  })

  describe('overlapping requests', () => {
    it('keeps the new form empty when the record Alice left arrives late (S-2 AC 5)', async () => {
      const record = deferred<DeliveryDetail>()
      getDelivery.mockReturnValue(record.promise)

      const { wrapper, router } = await renderApp('/deliveries/1/edit')
      await router.push('/deliveries/new')
      await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('New delivery'))
      await control(wrapper, 'Customer company').setValue('Nordlicht Interiors')

      record.resolve(deliveryDetail())
      await flushPromises()

      expect(wrapper.find('h1').text()).toBe('New delivery')
      expect(control(wrapper, 'Customer company').element.value).toBe('Nordlicht Interiors')
      expect(control(wrapper, 'Real price (EUR)').element.value).toBe('')
      expect(control(section(wrapper, 'Origin'), 'City').element.value).toBe('')
      expect(section(wrapper, 'Packing list (Excel)').text()).not.toContain(
        'QX20260115 packing list.xlsx',
      )
      expect(wrapper.findAll('button').map((b) => b.text())).not.toContain('Delete delivery')
    })

    it('shows no load error on the new form when the record Alice left fails late (S-2 AC 5)', async () => {
      const record = deferred<DeliveryDetail>()
      getDelivery.mockReturnValue(record.promise)

      const { wrapper, router } = await renderApp('/deliveries/1/edit')
      await router.push('/deliveries/new')

      record.reject(new ApiError(500, 'internal', { error: 'internal' }))
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(button(wrapper, 'Create delivery').exists()).toBe(true)
    })

    describe('a PDF removal still running when Alice leaves for "New delivery"', () => {
      const billOfLading = deliveryPdf({ id: 8, original_filename: 'Bill of lading.pdf' })

      async function startRemovalThenLeave(wrapper: VueWrapper, router: Router) {
        const documents = section(wrapper, 'Delivery documents (PDF)')
        const entry = documents
          .findAll('li')
          .find((li) => li.text().includes('QX20260115 delivery note.pdf'))
        if (!entry) throw new Error('No PDF entry "QX20260115 delivery note.pdf"')
        await button(entry, 'Remove').trigger('click')
        await router.push('/deliveries/new')
        await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('New delivery'))
      }

      it('keeps the new form free of the old record when the removal finishes late (S-2 AC 5)', async () => {
        const removal = deferred<void>()
        getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
        vi.mocked(client.deletePdf).mockReturnValue(removal.promise)

        const { wrapper, router } = await renderApp('/deliveries/1/edit')
        await startRemovalThenLeave(wrapper, router)

        removal.resolve()
        await flushPromises()

        const documents = section(wrapper, 'Delivery documents (PDF)')
        expect(documents.text()).toContain('No documents attached.')
        expect(documents.text()).not.toContain('Bill of lading.pdf')
        expect(documents.findAll('button').map((b) => b.text())).not.toContain('Remove')
        expect(section(wrapper, 'Packing list (Excel)').text()).not.toContain(
          'QX20260115 packing list.xlsx',
        )
      })

      it('shows no removal error on the new form when the removal fails late (S-2 AC 5)', async () => {
        const removal = deferred<void>()
        getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
        vi.mocked(client.deletePdf).mockReturnValue(removal.promise)

        const { wrapper, router } = await renderApp('/deliveries/1/edit')
        await startRemovalThenLeave(wrapper, router)

        removal.reject(new ApiError(500, 'internal', { error: 'internal' }))
        await flushPromises()

        expect(wrapper.find('[role="alert"]').exists()).toBe(false)
        expect(section(wrapper, 'Delivery documents (PDF)').text()).not.toContain(
          'could not be removed',
        )
      })
    })

    describe('a second Excel picked while the first is parsing', () => {
      const firstPackingList = new File(['xlsx'], 'QX20260115 packing list.xlsx')
      const secondPackingList = new File(['xlsx 2'], 'QX20260122 packing list.xlsx')
      const secondParse = parsedPackingList({
        total_volume_m3: 8.5,
        total_gross_weight_kg: 2010,
        total_pallet_count: 7,
        sheet_name: 'PL 0712',
        items: [deliveryItem()],
      })

      it('keeps the second file\'s totals when the first parse arrives late (S-2 AC 5)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        vi.mocked(client.parseExcel)
          .mockReturnValueOnce(firstParse.promise)
          .mockResolvedValueOnce(secondParse)
        vi.mocked(client.uploadExcel).mockResolvedValue(created)

        const { wrapper } = await renderApp('/deliveries/new')
        const picker = control(wrapper, 'Packing list Excel file')
        await pickFiles(picker, [firstPackingList])
        await pickFiles(picker, [secondPackingList])
        const excel = section(wrapper, 'Packing list (Excel)')
        await vi.waitFor(() => expect(excel.text()).toMatch(/Read 1 item rows from sheet PL 0712/))

        firstParse.resolve(parsedPackingList())
        await flushPromises()

        expect(excel.text()).toMatch(/Read 1 item rows from sheet PL 0712/)
        const cargo = section(wrapper, 'Cargo totals')
        expect(control(cargo, 'Volume (m³)').element.value).toBe('8.5')
        expect(control(cargo, 'Gross weight (kg)').element.value).toBe('2010')
        expect(control(cargo, 'Pallets').element.value).toBe('7')

        await fillRequiredFields(wrapper)
        await submit(wrapper, 'Create delivery')
        await vi.waitFor(() =>
          expect(client.uploadExcel).toHaveBeenCalledWith(42, secondPackingList),
        )
      })

      it('keeps parsing the second file when the first parse arrives before it (S-2 AC 5)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        const pendingSecondParse = deferred<ParsedPackingList>()
        vi.mocked(client.parseExcel)
          .mockReturnValueOnce(firstParse.promise)
          .mockReturnValueOnce(pendingSecondParse.promise)

        const { wrapper } = await renderApp('/deliveries/new')
        const picker = control(wrapper, 'Packing list Excel file')
        await pickFiles(picker, [firstPackingList])
        await pickFiles(picker, [secondPackingList])

        firstParse.resolve(parsedPackingList())
        await flushPromises()

        const excel = section(wrapper, 'Packing list (Excel)')
        expect(excel.text()).toContain('Parsing QX20260122 packing list.xlsx…')
        expect(excel.text()).not.toContain('Read 2 item rows')
        expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('')

        pendingSecondParse.resolve(secondParse)
        await flushPromises()

        expect(excel.text()).toMatch(/Read 1 item rows from sheet PL 0712/)
        expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('8.5')
      })

      it('ignores a parse failure of the first file once the second is read (S-2 AC 5)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        vi.mocked(client.parseExcel)
          .mockReturnValueOnce(firstParse.promise)
          .mockResolvedValueOnce(secondParse)

        const { wrapper } = await renderApp('/deliveries/new')
        const picker = control(wrapper, 'Packing list Excel file')
        await pickFiles(picker, [firstPackingList])
        await pickFiles(picker, [secondPackingList])
        const excel = section(wrapper, 'Packing list (Excel)')
        await vi.waitFor(() => expect(excel.text()).toMatch(/sheet PL 0712/))

        firstParse.reject(
          new ApiError(422, 'parse_failed', { error: 'parse_failed', detail: 'No packing list' }),
        )
        await flushPromises()

        expect(excel.text()).not.toContain('That file could not be parsed.')
        expect(excel.text()).toMatch(/Read 1 item rows from sheet PL 0712/)
        expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('8.5')
      })
    })
  })
})

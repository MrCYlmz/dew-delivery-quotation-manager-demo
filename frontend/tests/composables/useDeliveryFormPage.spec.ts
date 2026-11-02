import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { toValue } from 'vue'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryDetail, ParsedPackingList } from '../../src/api/types'
import { useDeliveryFormPage } from '../../src/composables/useDeliveryFormPage'
import { mountViewModel, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { deliveryDetail, deliveryItem, deliveryPdf, parsedPackingList } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  getDelivery: vi.fn(),
  createDelivery: vi.fn(),
  updateDelivery: vi.fn(),
  uploadExcel: vi.fn(),
  uploadPdfs: vi.fn(),
  deletePdf: vi.fn(),
  parseExcel: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const getDelivery = vi.mocked(client.getDelivery)
const createDelivery = vi.mocked(client.createDelivery)
const parseExcel = vi.mocked(client.parseExcel)

const packingList = new File(['xlsx'], 'QX20260115 packing list.xlsx')

const requiredFields = {
  customer_company: 'Nordlicht Interiors',
  origin_country: 'VN',
  origin_city: 'Ho Chi Minh City',
  destination_country: 'FR',
  destination_city: 'Lyon',
  transport_method: 'AIR' as const,
  shipping_date: '2026-03-12',
  cost_eur: '1250.5',
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
  createDelivery.mockResolvedValue(deliveryDetail({ id: 42 }))
})

afterEach(unmountAll)

describe('useDeliveryFormPage', () => {
  describe('loading the record', () => {
    it('starts an empty form for a new delivery without loading anything (S-2 AC 10)', async () => {
      const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)

      expect(getDelivery).not.toHaveBeenCalled()
      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.loadError)).toBeNull()
      expect(toValue(viewModel.form).customer_company).toBe('')
    })

    it('starts a new delivery at To do without an offer price (S-9 AC 18)', async () => {
      const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)

      expect(toValue(viewModel.form).status).toBe('TO_DO')
      expect(toValue(viewModel.form).offer_price_eur).toBe('')
    })

    it('is loading until the record to edit arrives, then fills the form (S-2 AC 10)', async () => {
      const record = deferred<DeliveryDetail>()
      getDelivery.mockReturnValue(record.promise)

      const { viewModel } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)

      expect(getDelivery).toHaveBeenCalledWith(1)
      expect(toValue(viewModel.loading)).toBe(true)

      record.resolve(deliveryDetail())
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.form).customer_company).toBe('Example Interiors')
      expect(toValue(viewModel.form).cost_eur).toBe('4210')
    })

    it('holds the error when the record to edit cannot be loaded (S-2 AC 10)', async () => {
      getDelivery.mockRejectedValue(new ApiError(404, 'not_found', { error: 'not_found' }))

      const { viewModel } = await mountViewModel('/deliveries/999/edit', useDeliveryFormPage)

      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.loadError)).toContain('Not found.')
    })
  })

  describe('saving', () => {
    it('holds the field errors and the message of a 400 response (S-2 AC 10)', async () => {
      createDelivery.mockRejectedValue(
        new ApiError(400, 'validation', {
          error: 'validation',
          fields: { customer_company: 'must be at most 120 characters' },
        }),
      )

      const { viewModel, router } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
      Object.assign(toValue(viewModel.form), requiredFields)
      await viewModel.save()

      expect(toValue(viewModel.fieldErrors).customer_company).toBe('must be at most 120 characters')
      expect(toValue(viewModel.saveError)).toContain('The server rejected the data.')
      expect(toValue(viewModel.saving)).toBe(false)
      expect(router.currentRoute.value.fullPath).toBe('/deliveries/new')
    })
  })

  describe('Excel parse', () => {
    it('is parsing until the packing list is read, then fills the cargo totals (S-2 AC 3, AC 10)', async () => {
      const parsed = deferred<ParsedPackingList>()
      parseExcel.mockReturnValue(parsed.promise)

      const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
      const picking = viewModel.pickPackingList(packingList)

      expect(parseExcel).toHaveBeenCalledWith(packingList)
      expect(toValue(viewModel.parsing)).toBe(true)

      parsed.resolve(parsedPackingList())
      await picking

      expect(toValue(viewModel.parsing)).toBe(false)
      expect(toValue(viewModel.parseError)).toBeNull()
      expect(toValue(viewModel.form).volume_m3).toBe('7.42')
      expect(toValue(viewModel.form).gross_weight_kg).toBe('1580')
      expect(toValue(viewModel.form).pallet_count).toBe('6')
    })

    it('holds the parser\'s message after a 422 and saves without the failed file (S-2 AC 9, AC 10)', async () => {
      parseExcel.mockRejectedValue(
        new ApiError(422, 'parse_failed', {
          error: 'parse_failed',
          detail: 'No sheet has a header row with the columns CTNS and G.W.',
        }),
      )

      const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
      await viewModel.pickPackingList(packingList)

      expect(toValue(viewModel.parsing)).toBe(false)
      expect(toValue(viewModel.parseError)).toContain(
        'No sheet has a header row with the columns CTNS and G.W.',
      )
      expect(toValue(viewModel.form).volume_m3).toBe('')

      Object.assign(toValue(viewModel.form), requiredFields)
      await viewModel.save()

      expect(createDelivery).toHaveBeenCalledTimes(1)
      expect(client.uploadExcel).not.toHaveBeenCalled()
      expect(toValue(viewModel.parseError)).toContain(
        'No sheet has a header row with the columns CTNS and G.W.',
      )
    })
  })

  describe('removing an attached PDF', () => {
    const billOfLading = deliveryPdf({ id: 8, original_filename: 'Bill of lading.pdf' })

    it('drops the removed PDF from the record (S-2 AC 3, AC 10)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
      vi.mocked(client.deletePdf).mockResolvedValue(undefined)

      const { viewModel } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
      await viewModel.removePdf(deliveryPdf())

      expect(client.deletePdf).toHaveBeenCalledWith(1, 7)
      expect(toValue(viewModel.existing)?.pdfs).toEqual([billOfLading])
      expect(toValue(viewModel.pdfError)).toBeNull()
    })

    it('holds the error and keeps the PDF when it cannot be removed (S-2 AC 3, AC 10)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
      vi.mocked(client.deletePdf).mockRejectedValue(
        new ApiError(500, 'internal', { error: 'internal' }),
      )

      const { viewModel } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
      await viewModel.removePdf(deliveryPdf())

      expect(toValue(viewModel.pdfError)).toContain(
        'QX20260115 delivery note.pdf could not be removed.',
      )
      expect(toValue(viewModel.existing)?.pdfs).toEqual([deliveryPdf(), billOfLading])
    })
  })

  describe('stale responses', () => {
    it('keeps the new form empty when the record it left arrives late (S-2 AC 5, AC 10)', async () => {
      const record = deferred<DeliveryDetail>()
      getDelivery.mockReturnValue(record.promise)

      const { viewModel, router } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
      await router.push('/deliveries/new')
      await flushPromises()
      toValue(viewModel.form).customer_company = 'Nordlicht Interiors'

      record.resolve(deliveryDetail())
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.existing)).toBeNull()
      expect(toValue(viewModel.form).customer_company).toBe('Nordlicht Interiors')
      expect(toValue(viewModel.form).cost_eur).toBe('')
    })

    it('ignores the load error of the record it left (S-2 AC 5, AC 10)', async () => {
      const record = deferred<DeliveryDetail>()
      getDelivery.mockReturnValue(record.promise)

      const { viewModel, router } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
      await router.push('/deliveries/new')
      await flushPromises()

      record.reject(new ApiError(500, 'internal', { error: 'internal' }))
      await flushPromises()

      expect(toValue(viewModel.loadError)).toBeNull()
      expect(toValue(viewModel.loading)).toBe(false)
    })

    describe('a PDF removal still running when the form is left for a new delivery', () => {
      const billOfLading = deliveryPdf({ id: 8, original_filename: 'Bill of lading.pdf' })

      it('keeps the new form free of the old record when the removal finishes late (S-2 AC 5, AC 10)', async () => {
        const removal = deferred<void>()
        getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
        vi.mocked(client.deletePdf).mockReturnValue(removal.promise)

        const { viewModel, router } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
        const removing = viewModel.removePdf(deliveryPdf())
        await router.push('/deliveries/new')
        await flushPromises()

        removal.resolve()
        await removing

        expect(toValue(viewModel.existing)).toBeNull()
        expect(toValue(viewModel.pdfError)).toBeNull()
      })

      it('ignores the error of a removal that finishes after the form was left (S-2 AC 5, AC 10)', async () => {
        const removal = deferred<void>()
        getDelivery.mockResolvedValue(deliveryDetail({ pdfs: [deliveryPdf(), billOfLading] }))
        vi.mocked(client.deletePdf).mockReturnValue(removal.promise)

        const { viewModel, router } = await mountViewModel('/deliveries/1/edit', useDeliveryFormPage)
        const removing = viewModel.removePdf(deliveryPdf())
        await router.push('/deliveries/new')
        await flushPromises()

        removal.reject(new ApiError(500, 'internal', { error: 'internal' }))
        await removing

        expect(toValue(viewModel.pdfError)).toBeNull()
        expect(toValue(viewModel.existing)).toBeNull()
      })
    })

    describe('a second packing list picked while the first is parsing', () => {
      const secondPackingList = new File(['xlsx 2'], 'QX20260122 packing list.xlsx')
      const secondParse = parsedPackingList({
        total_volume_m3: 8.5,
        total_gross_weight_kg: 2010,
        total_pallet_count: 7,
        items: [deliveryItem()],
      })

      it('keeps the second file\'s totals when the first parse arrives late (S-2 AC 5, AC 10)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        parseExcel.mockReturnValueOnce(firstParse.promise).mockResolvedValueOnce(secondParse)
        vi.mocked(client.uploadExcel).mockResolvedValue(deliveryDetail({ id: 42 }))

        const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
        const firstPicking = viewModel.pickPackingList(packingList)
        await viewModel.pickPackingList(secondPackingList)
        firstParse.resolve(parsedPackingList())
        await firstPicking

        expect(toValue(viewModel.parsing)).toBe(false)
        expect(toValue(viewModel.form).volume_m3).toBe('8.5')
        expect(toValue(viewModel.form).gross_weight_kg).toBe('2010')
        expect(toValue(viewModel.form).pallet_count).toBe('7')

        Object.assign(toValue(viewModel.form), requiredFields)
        await viewModel.save()

        expect(client.uploadExcel).toHaveBeenCalledTimes(1)
        expect(client.uploadExcel).toHaveBeenCalledWith(42, secondPackingList)
      })

      it('stays parsing when the first parse arrives before the second (S-2 AC 5, AC 10)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        const pendingSecondParse = deferred<ParsedPackingList>()
        parseExcel
          .mockReturnValueOnce(firstParse.promise)
          .mockReturnValueOnce(pendingSecondParse.promise)

        const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
        const firstPicking = viewModel.pickPackingList(packingList)
        const secondPicking = viewModel.pickPackingList(secondPackingList)
        firstParse.resolve(parsedPackingList())
        await firstPicking

        expect(toValue(viewModel.parsing)).toBe(true)
        expect(toValue(viewModel.form).volume_m3).toBe('')

        pendingSecondParse.resolve(secondParse)
        await secondPicking

        expect(toValue(viewModel.parsing)).toBe(false)
        expect(toValue(viewModel.form).volume_m3).toBe('8.5')
      })

      it('ignores a parse failure of the first file once the second is read (S-2 AC 5, AC 10)', async () => {
        const firstParse = deferred<ParsedPackingList>()
        parseExcel.mockReturnValueOnce(firstParse.promise).mockResolvedValueOnce(secondParse)
        vi.mocked(client.uploadExcel).mockResolvedValue(deliveryDetail({ id: 42 }))

        const { viewModel } = await mountViewModel('/deliveries/new', useDeliveryFormPage)
        const firstPicking = viewModel.pickPackingList(packingList)
        await viewModel.pickPackingList(secondPackingList)
        firstParse.reject(
          new ApiError(422, 'parse_failed', { error: 'parse_failed', detail: 'No packing list' }),
        )
        await firstPicking

        expect(toValue(viewModel.parseError)).toBeNull()
        expect(toValue(viewModel.form).volume_m3).toBe('8.5')

        Object.assign(toValue(viewModel.form), requiredFields)
        await viewModel.save()

        expect(client.uploadExcel).toHaveBeenCalledWith(42, secondPackingList)
      })
    })
  })
})

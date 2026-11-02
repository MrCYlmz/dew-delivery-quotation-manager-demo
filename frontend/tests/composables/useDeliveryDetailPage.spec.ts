import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { toValue } from 'vue'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryDetail } from '../../src/api/types'
import { useDeliveryDetailPage } from '../../src/composables/useDeliveryDetailPage'
import { mountViewModel, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { deliveryDetail } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  getDelivery: vi.fn(),
}))

const getDelivery = vi.mocked(client.getDelivery)

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(unmountAll)

describe('useDeliveryDetailPage', () => {
  it('is loading until the record arrives, then holds it (S-2 AC 10)', async () => {
    const record = deferred<DeliveryDetail>()
    getDelivery.mockReturnValue(record.promise)

    const { viewModel } = await mountViewModel('/deliveries/1', useDeliveryDetailPage)

    expect(getDelivery).toHaveBeenCalledWith(1)
    expect(toValue(viewModel.loading)).toBe(true)

    record.resolve(deliveryDetail())
    await flushPromises()

    expect(toValue(viewModel.loading)).toBe(false)
    expect(toValue(viewModel.error)).toBeNull()
    expect(toValue(viewModel.detail)).toEqual(deliveryDetail())
  })

  it('holds "Not found" and no record for an unknown id (S-2 AC 10)', async () => {
    getDelivery.mockRejectedValue(new ApiError(404, 'not_found', { error: 'not_found' }))

    const { viewModel } = await mountViewModel('/deliveries/999', useDeliveryDetailPage)

    expect(toValue(viewModel.loading)).toBe(false)
    expect(toValue(viewModel.error)).toContain('Not found.')
    expect(toValue(viewModel.detail)).toBeNull()
  })

  it('clears the error when loading again succeeds (S-2 AC 10)', async () => {
    getDelivery
      .mockRejectedValueOnce(new ApiError(503, 'unavailable', { error: 'unavailable' }))
      .mockResolvedValueOnce(deliveryDetail())

    const { viewModel } = await mountViewModel('/deliveries/1', useDeliveryDetailPage)
    await viewModel.load()

    expect(toValue(viewModel.error)).toBeNull()
    expect(toValue(viewModel.detail)).toEqual(deliveryDetail())
  })

  describe('stale responses', () => {
    const otherRecord = deliveryDetail({ id: 2, customer_company: 'Nordlicht Interiors' })

    it('keeps the newer record when the one opened before it arrives late (S-2 AC 10)', async () => {
      const firstRecord = deferred<DeliveryDetail>()
      getDelivery.mockReturnValueOnce(firstRecord.promise).mockResolvedValueOnce(otherRecord)

      const { viewModel, router } = await mountViewModel('/deliveries/1', useDeliveryDetailPage)
      await router.push('/deliveries/2')
      await flushPromises()
      firstRecord.resolve(deliveryDetail())
      await flushPromises()

      expect(getDelivery).toHaveBeenLastCalledWith(2)
      expect(toValue(viewModel.detail)).toEqual(otherRecord)
      expect(toValue(viewModel.loading)).toBe(false)
    })

    it('stays loading when a superseded record arrives before the newer one (S-2 AC 10)', async () => {
      const firstRecord = deferred<DeliveryDetail>()
      const secondRecord = deferred<DeliveryDetail>()
      getDelivery.mockReturnValueOnce(firstRecord.promise).mockReturnValueOnce(secondRecord.promise)

      const { viewModel, router } = await mountViewModel('/deliveries/1', useDeliveryDetailPage)
      await router.push('/deliveries/2')
      await flushPromises()
      firstRecord.resolve(deliveryDetail())
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(true)
      expect(toValue(viewModel.detail)).toBeNull()

      secondRecord.resolve(otherRecord)
      await flushPromises()

      expect(toValue(viewModel.loading)).toBe(false)
      expect(toValue(viewModel.detail)).toEqual(otherRecord)
    })

    it('ignores the error of a superseded load once a retry has succeeded (S-2 AC 10)', async () => {
      const firstAttempt = deferred<DeliveryDetail>()
      getDelivery.mockReturnValueOnce(firstAttempt.promise).mockResolvedValueOnce(deliveryDetail())

      const { viewModel } = await mountViewModel('/deliveries/1', useDeliveryDetailPage)
      await viewModel.load()
      firstAttempt.reject(new ApiError(503, 'unavailable', { error: 'unavailable' }))
      await flushPromises()

      expect(toValue(viewModel.error)).toBeNull()
      expect(toValue(viewModel.detail)).toEqual(deliveryDetail())
      expect(toValue(viewModel.loading)).toBe(false)
    })
  })
})

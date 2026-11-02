import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import * as client from '../../src/api/client'
import { control, renderApp, section, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  listDeliveries: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const suggestCities = vi.mocked(client.suggestCities)

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.listDeliveries).mockResolvedValue([])
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue(['CN', 'VN', 'JP', 'KR'])
  suggestCities.mockResolvedValue([])
})

afterEach(unmountAll)

describe('city suggestions', () => {
  it('offers the cities of the newer country when the earlier list arrives late, on the filter bar (S-2 AC 5)', async () => {
    const chineseCities = deferred<string[]>()
    suggestCities.mockImplementation((_field, country) => {
      if (country === 'CN') return chineseCities.promise
      if (country === 'VN') return Promise.resolve(['Ho Chi Minh City'])
      return Promise.resolve([])
    })

    const { wrapper } = await renderApp('/deliveries')
    await control<HTMLSelectElement>(wrapper, 'Origin country').setValue('CN')
    await control<HTMLSelectElement>(wrapper, 'Origin country').setValue('VN')
    await vi.waitFor(() => expect(suggestCities).toHaveBeenCalledWith('origin', 'VN'))
    await flushPromises()

    chineseCities.resolve(['Ningbo'])
    await flushPromises()

    const cities = control<HTMLSelectElement>(wrapper, 'Origin city')
    expect(cities.findAll('option').map((o) => o.text())).toEqual(['Any', 'Ho Chi Minh City'])
  })

  it('offers the cities of the newer country when the earlier list arrives late, on the entry form (S-2 AC 5)', async () => {
    const japaneseCities = deferred<string[]>()
    suggestCities.mockImplementation((_field, country) => {
      if (country === 'JP') return japaneseCities.promise
      if (country === 'KR') return Promise.resolve(['Busan'])
      return Promise.resolve([])
    })

    const { wrapper } = await renderApp('/deliveries/new')
    const origin = section(wrapper, 'Origin')
    await control<HTMLSelectElement>(origin, 'Country').setValue('JP')
    await control<HTMLSelectElement>(origin, 'Country').setValue('KR')
    await vi.waitFor(() => expect(suggestCities).toHaveBeenCalledWith('origin', 'KR'))
    await flushPromises()

    japaneseCities.resolve(['Osaka'])
    await flushPromises()
    await control(origin, 'City').trigger('focus')

    expect(origin.findAll('[role="option"]').map((o) => o.text())).toEqual(['Busan'])
  })
})

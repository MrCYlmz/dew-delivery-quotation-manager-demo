import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { EstimateComparables, TransportMethod } from '../../src/api/types'
import { COUNTRIES } from '../../src/countries'
import { button, control, fieldOf, link, pickFiles, renderApp, section, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { estimateComparable, estimateComparables, parsedPackingList } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  getBoard: vi.fn(),
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
  getEstimateComparables: vi.fn(),
}))

const getEstimateComparables = vi.mocked(client.getEstimateComparables)
const writeText = vi.fn<(text: string) => Promise<void>>()

const packingList = new File(['xlsx'], 'Offer 2026-11 packing list.xlsx')

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 9, 26, 10, 0) })
  vi.mocked(client.getBoard).mockResolvedValue([])
  vi.mocked(client.listDeliveries).mockResolvedValue([])
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
  getEstimateComparables.mockResolvedValue(estimateComparables())
  writeText.mockResolvedValue()
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
})

afterEach(() => {
  unmountAll()
  vi.useRealTimers()
})

interface Route {
  method: TransportMethod
  origin: string
  destination: string
}

async function setRoute(wrapper: VueWrapper, route: Route) {
  await control(wrapper, 'Transport method').setValue(route.method)
  await control(section(wrapper, 'Origin'), 'Country').setValue(route.origin)
  await control(section(wrapper, 'Destination'), 'Country').setValue(route.destination)
}

const seaChinaGermany: Route = { method: 'SEA', origin: 'CN', destination: 'DE' }

async function fillPotentialDelivery(wrapper: VueWrapper) {
  await setRoute(wrapper, seaChinaGermany)
  await control(section(wrapper, 'Origin'), 'City').setValue('Ningbo')
  await control(section(wrapper, 'Destination'), 'City').setValue('Munich')
  await control(wrapper, 'Planned shipping date').setValue('2026-11-20')
  const cargo = section(wrapper, 'Cargo totals')
  await control(cargo, 'Pallets').setValue('6')
  await control(cargo, 'Volume (m³)').setValue('5')
  await control(cargo, 'Gross weight (kg)').setValue('900')
}

function comparablesList(wrapper: VueWrapper) {
  return section(wrapper, 'Comparable deliveries')
}

function recordIds(wrapper: VueWrapper): number[] {
  return comparablesList(wrapper)
    .findAll('a')
    .map((a) => /\/deliveries\/(\d+)$/.exec(a.attributes('href') ?? '')?.[1])
    .filter((id): id is string => id !== undefined)
    .map(Number)
}

function hasPromptBox(wrapper: VueWrapper): boolean {
  return wrapper.findAll('textarea').length > 0
}

function promptBox(wrapper: VueWrapper) {
  return control<HTMLTextAreaElement>(wrapper, 'Prompt for Claude')
}

function promptText(wrapper: VueWrapper): string {
  return promptBox(wrapper).element.value
}

async function promptReady(wrapper: VueWrapper): Promise<void> {
  await vi.waitFor(() => expect(hasPromptBox(wrapper)).toBe(true))
}

function canCopy(wrapper: VueWrapper): boolean {
  const copy = wrapper.findAll<HTMLButtonElement>('button').find((b) => b.text() === 'Copy prompt')
  return copy !== undefined && !copy.element.disabled
}

function lastQuery(): Record<string, string | number | undefined> {
  const query = getEstimateComparables.mock.lastCall?.[0]
  if (!query) throw new Error('No comparables request was made')
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== ''),
  )
}

function asNumbers(query: Record<string, string | number | undefined>) {
  return Object.fromEntries(
    Object.entries(query).map(([key, value]) =>
      ['volume_m3', 'gross_weight_kg'].includes(key) ? [key, Number(value)] : [key, value],
    ),
  )
}

function tableLines(prompt: string): string[][] {
  return prompt
    .split('\n')
    .filter((line) => line.trim().startsWith('|') && !/^\|[\s|:-]+\|?$/.test(line.trim()))
    .map((line) =>
      line
        .trim()
        .replace(/^\||\|$/g, '')
        .split('|')
        .map((cell) => cell.trim()),
    )
}

function rowFor(prompt: string, ref: number): string[] {
  const row = tableLines(prompt).find((cells) => cells[0] === String(ref))
  if (!row) throw new Error(`No prompt table row for ref ${ref}`)
  return row
}

describe('EstimatePage', () => {
  describe('navigation', () => {
    it('opens the Estimate screen from the main navigation (S-7 AC 1)', async () => {
      const { wrapper, router } = await renderApp('/')

      await link(wrapper, 'Estimate').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/estimate'))
      await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('Estimate'))
    })

    it('leads back to the deliveries list from the main navigation (S-7 AC 1, S-9 AC 20)', async () => {
      const { wrapper, router } = await renderApp('/estimate')

      await link(wrapper, 'Deliveries').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
    })
  })

  describe('estimate form', () => {
    it('offers exactly the four transport methods (S-7 AC 2)', async () => {
      const { wrapper } = await renderApp('/estimate')

      const options = control<HTMLSelectElement>(wrapper, 'Transport method')
        .findAll('option')
        .filter((o) => o.element.value !== '')
        .map((o) => [o.element.value, o.text()])

      expect(options).toEqual([
        ['SEA', 'Sea'],
        ['AIR', 'Air'],
        ['ROAD', 'Road'],
        ['RAIL', 'Rail'],
      ])
    })

    it('offers the delivery form country list on both sides, by name (S-7 AC 2)', async () => {
      const { wrapper } = await renderApp('/estimate')

      for (const side of ['Origin', 'Destination']) {
        const select = control<HTMLSelectElement>(section(wrapper, side), 'Country')
        const codes = select
          .findAll('option')
          .map((o) => o.element.value)
          .filter(Boolean)
        expect(codes, side).toEqual(expect.arrayContaining(COUNTRIES.map((c) => c.code)))
        expect(select.find('option[value="CN"]').text().trim(), side).toBe('China')
      }
    })

    it('asks for comparables only once method, origin and destination country are set (S-7 AC 2)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await control(wrapper, 'Transport method').setValue('SEA')
      await control(section(wrapper, 'Origin'), 'Country').setValue('CN')
      await flushPromises()

      expect(getEstimateComparables).not.toHaveBeenCalled()
      expect(hasPromptBox(wrapper)).toBe(false)
      expect(canCopy(wrapper)).toBe(false)

      await control(section(wrapper, 'Destination'), 'Country').setValue('DE')

      await vi.waitFor(() => expect(getEstimateComparables).toHaveBeenCalled())
      expect(lastQuery()).toEqual({
        transport_method: 'SEA',
        origin_country: 'CN',
        destination_country: 'DE',
      })
    })

    it('opens with every optional field empty (S-7 AC 3)', async () => {
      const { wrapper } = await renderApp('/estimate')

      expect(control(section(wrapper, 'Origin'), 'City').element.value).toBe('')
      expect(control(section(wrapper, 'Destination'), 'City').element.value).toBe('')
      expect(control(wrapper, 'Planned shipping date').element.value).toBe('')
      const cargo = section(wrapper, 'Cargo totals')
      expect(control(cargo, 'Pallets').element.value).toBe('')
      expect(control(cargo, 'Volume (m³)').element.value).toBe('')
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('')
    })

    it('sends volume and gross weight with the route, but not cities, date or pallets (S-7 AC 3)', async () => {
      const { wrapper } = await renderApp('/estimate')

      await fillPotentialDelivery(wrapper)

      await vi.waitFor(() =>
        expect(asNumbers(lastQuery())).toEqual({
          transport_method: 'SEA',
          origin_country: 'CN',
          destination_country: 'DE',
          volume_m3: 5,
          gross_weight_kg: 900,
        }),
      )
    })

    it('asks again with the new size when a cargo total changes (S-7 AC 7)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)
      await vi.waitFor(() => expect(asNumbers(lastQuery()).volume_m3).toBe(5))

      await control(section(wrapper, 'Cargo totals'), 'Gross weight (kg)').setValue('')
      await control(section(wrapper, 'Cargo totals'), 'Volume (m³)').setValue('7.5')

      await vi.waitFor(() =>
        expect(asNumbers(lastQuery())).toEqual({
          transport_method: 'SEA',
          origin_country: 'CN',
          destination_country: 'DE',
          volume_m3: 7.5,
        }),
      )
    })

    it('treats a total of 0 as not given and leaves it out of the request (S-7 AC 4a)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      const cargo = section(wrapper, 'Cargo totals')
      await control(cargo, 'Volume (m³)').setValue('0')
      await control(cargo, 'Gross weight (kg)').setValue('900')

      await vi.waitFor(() =>
        expect(asNumbers(lastQuery())).toEqual({
          transport_method: 'SEA',
          origin_country: 'CN',
          destination_country: 'DE',
          gross_weight_kg: 900,
        }),
      )
      expect(control(cargo, 'Volume (m³)').attributes('aria-invalid')).not.toBe('true')
    })

    for (const [label, value] of [
      ['Volume (m³)', '-2'],
      ['Gross weight (kg)', 'about 900'],
      ['Pallets', '-1'],
    ]) {
      it(`shows a field error for ${label} "${value}" and looks nothing up until it is fixed (S-7 AC 4a)`, async () => {
        const { wrapper } = await renderApp('/estimate')
        const cargo = section(wrapper, 'Cargo totals')
        await control(cargo, label).setValue(value)
        await setRoute(wrapper, seaChinaGermany)
        await flushPromises()

        await vi.waitFor(() => expect(control(cargo, label).attributes('aria-invalid')).toBe('true'))
        expect(fieldOf(cargo, label).text()).not.toBe('')
        expect(getEstimateComparables).not.toHaveBeenCalled()
        expect(hasPromptBox(wrapper)).toBe(false)
        expect(canCopy(wrapper)).toBe(false)

        await control(cargo, label).setValue('3')

        await vi.waitFor(() => expect(getEstimateComparables).toHaveBeenCalled())
        expect(control(cargo, label).attributes('aria-invalid')).not.toBe('true')
        await promptReady(wrapper)
      })
    }
  })

  describe('packing-list Excel', () => {
    it('fills pallets, volume and gross weight from the parsed totals (S-7 AC 4)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())

      const { wrapper } = await renderApp('/estimate')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])

      expect(client.parseExcel).toHaveBeenCalledWith(packingList)
      const cargo = section(wrapper, 'Cargo totals')
      await vi.waitFor(() => expect(control(cargo, 'Volume (m³)').element.value).toBe('7.42'))
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('1580')
      expect(control(cargo, 'Pallets').element.value).toBe('6')
      expect(section(wrapper, 'Packing list (Excel)').text()).toMatch(
        /Read 2 item rows from sheet PACKING LIST/,
      )
    })

    it('lets Alice edit the parsed totals and asks with her values (S-7 AC 4)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())

      const { wrapper } = await renderApp('/estimate')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
      const cargo = section(wrapper, 'Cargo totals')
      await vi.waitFor(() => expect(control(cargo, 'Volume (m³)').element.value).toBe('7.42'))
      await control(cargo, 'Volume (m³)').setValue('14')
      await setRoute(wrapper, seaChinaGermany)

      await vi.waitFor(() =>
        expect(asNumbers(lastQuery())).toMatchObject({ volume_m3: 14, gross_weight_kg: 1580 }),
      )
      expect(control(cargo, 'Volume (m³)').element.value).toBe('14')
    })

    it('creates and changes no delivery record when a packing list is used (S-7 AC 4)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())

      const { wrapper } = await renderApp('/estimate')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
      await vi.waitFor(() =>
        expect(control(section(wrapper, 'Cargo totals'), 'Pallets').element.value).toBe('6'),
      )
      await setRoute(wrapper, seaChinaGermany)
      await promptReady(wrapper)

      expect(client.createDelivery).not.toHaveBeenCalled()
      expect(client.updateDelivery).not.toHaveBeenCalled()
      expect(client.uploadExcel).not.toHaveBeenCalled()
      expect(client.uploadPdfs).not.toHaveBeenCalled()
    })

    it('shows a parse failure and leaves the totals empty (S-7 AC 4)', async () => {
      vi.mocked(client.parseExcel).mockRejectedValue(
        new ApiError(422, 'parse_failed', {
          error: 'parse_failed',
          detail: 'No sheet has a header row with the columns CTNS and G.W.',
        }),
      )

      const { wrapper } = await renderApp('/estimate')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])

      const excel = section(wrapper, 'Packing list (Excel)')
      await vi.waitFor(() =>
        expect(excel.find('[role="alert"]').text()).toContain(
          'No sheet has a header row with the columns CTNS and G.W.',
        ),
      )
      const cargo = section(wrapper, 'Cargo totals')
      expect(control(cargo, 'Volume (m³)').element.value).toBe('')
      expect(control(cargo, 'Gross weight (kg)').element.value).toBe('')
      expect(control(cargo, 'Pallets').element.value).toBe('')
    })
  })

  describe('nothing saved', () => {
    it('shows an empty form after leaving the screen and coming back (S-7 AC 5)', async () => {
      const { wrapper, router } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)
      await promptReady(wrapper)

      await router.push('/')
      await flushPromises()
      await router.push('/estimate')
      await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('Estimate'))

      expect(control<HTMLSelectElement>(wrapper, 'Transport method').element.value).toBe('')
      expect(control<HTMLSelectElement>(section(wrapper, 'Origin'), 'Country').element.value).toBe('')
      expect(control(section(wrapper, 'Origin'), 'City').element.value).toBe('')
      expect(
        control<HTMLSelectElement>(section(wrapper, 'Destination'), 'Country').element.value,
      ).toBe('')
      expect(control(wrapper, 'Planned shipping date').element.value).toBe('')
      expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('')
      expect(hasPromptBox(wrapper)).toBe(false)
      expect(client.createDelivery).not.toHaveBeenCalled()
      expect(client.updateDelivery).not.toHaveBeenCalled()
    })
  })

  describe('comparables', () => {
    it('lists the comparables in rank order, each linking to its record in a new tab (S-7 AC 15)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)

      await vi.waitFor(() => expect(recordIds(wrapper)).toEqual([42, 57, 19]))
      for (const a of comparablesList(wrapper).findAll('a')) {
        expect(a.attributes('target')).toBe('_blank')
      }
    })

    it('shows the chargeable weight and price of each comparable (S-7 AC 15)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await vi.waitFor(() => expect(recordIds(wrapper)).toHaveLength(3))

      const rows = comparablesList(wrapper).findAll('tbody tr')
      expect(rows[0].text()).toContain('4,800')
      expect(rows[0].text()).toContain('€4,100.00')
      expect(rows[1].text()).toContain('5,600')
      expect(rows[1].text()).toContain('€4,650.00')
    })

    it('shows a comparable\'s real price, else its offer price marked "(offer)" (S-9 AC 24)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({
          comparables: [
            estimateComparable({ id: 42, cost_eur: 4100, offer_price_eur: 3900 }),
            estimateComparable({ id: 73, cost_eur: null, offer_price_eur: 4300 }),
          ],
        }),
      )

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await vi.waitFor(() => expect(recordIds(wrapper)).toEqual([42, 73]))

      const [realPriced, offerPriced] = comparablesList(wrapper).findAll('tbody tr').map((row) => row.text())
      expect(realPriced).toContain('€4,100.00')
      expect(realPriced).not.toContain('€3,900.00')
      expect(realPriced).not.toContain('(offer)')
      expect(offerPriced).toMatch(/€4,300\.00\s*\(offer\)/)
      expect(offerPriced).not.toMatch(/—|null|undefined|NaN/)
    })

    it('heads the comparables\' price column "Price", not "Cost paid" (S-9 AC 24)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await vi.waitFor(() => expect(recordIds(wrapper)).toHaveLength(3))

      const headings = comparablesList(wrapper).findAll('thead th').map((th) => th.text())
      expect(headings).toContain('Price')
      expect(comparablesList(wrapper).text()).not.toContain('Cost paid')
    })

    it('shows the chargeable weight the server computed for the potential delivery (S-7 AC 6)', async () => {
      getEstimateComparables.mockResolvedValue(estimateComparables({ chargeable_kg: 5000 }))

      const { wrapper } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)
      await vi.waitFor(() => expect(recordIds(wrapper)).toHaveLength(3))

      expect(wrapper.text()).toMatch(/Chargeable weight[^.]*5,000 kg/)
    })

    it('shows the comparables for the newer form when an older request answers late (S-7 AC 15)', async () => {
      const toGermany = deferred<EstimateComparables>()
      getEstimateComparables
        .mockReturnValueOnce(toGermany.promise)
        .mockResolvedValueOnce(
          estimateComparables({
            comparables: [estimateComparable({ id: 88, destination_country: 'FR' })],
          }),
        )

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await vi.waitFor(() => expect(getEstimateComparables).toHaveBeenCalledTimes(1))
      await control(section(wrapper, 'Destination'), 'Country').setValue('FR')
      await vi.waitFor(() => expect(recordIds(wrapper)).toEqual([88]))

      toGermany.resolve(estimateComparables())
      await flushPromises()

      expect(recordIds(wrapper)).toEqual([88])
    })

    it('shows that comparables are being found while the request runs (S-7 AC 15)', async () => {
      getEstimateComparables.mockReturnValue(new Promise<EstimateComparables>(() => {}))

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)

      await vi.waitFor(() =>
        expect(comparablesList(wrapper).text()).toContain('Finding comparable deliveries'),
      )
      expect(hasPromptBox(wrapper)).toBe(false)
      expect(canCopy(wrapper)).toBe(false)
    })

    it('shows the failure, keeps the form and finds the comparables on retry (S-7 AC 15)', async () => {
      getEstimateComparables.mockRejectedValue(new ApiError(500, 'internal', { error: 'internal' }))

      const { wrapper } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)

      await vi.waitFor(() =>
        expect(wrapper.find('[role="alert"]').text()).toContain(
          'The server could not handle the request (HTTP 500).',
        ),
      )
      expect(hasPromptBox(wrapper)).toBe(false)
      expect(canCopy(wrapper)).toBe(false)
      expect(control<HTMLSelectElement>(wrapper, 'Transport method').element.value).toBe('SEA')
      expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('5')

      getEstimateComparables.mockResolvedValue(estimateComparables())
      await button(wrapper.find('[role="alert"]'), 'Try again').trigger('click')

      await vi.waitFor(() => expect(recordIds(wrapper)).toEqual([42, 57, 19]))
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(canCopy(wrapper)).toBe(true)
    })
  })

  describe('fallback', () => {
    it('mentions no ignored country when the route itself matched (S-7 AC 13)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await promptReady(wrapper)

      expect(wrapper.text()).not.toMatch(/ignored/i)
      expect(wrapper.text()).not.toContain('other routes with the same method')
      expect(promptText(wrapper)).not.toMatch(/ignored/i)
      expect(promptText(wrapper)).not.toContain('other routes with the same method')
    })

    it('says on screen and in the prompt that the destination country was ignored (S-7 AC 12, AC 13)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({ fallback: 'DESTINATION_IGNORED' }),
      )

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, { method: 'SEA', origin: 'CN', destination: 'AT' })
      await promptReady(wrapper)

      expect(comparablesList(wrapper).text()).toContain('the destination country is ignored')
      expect(promptText(wrapper)).toContain('the destination country was ignored')
      expect(wrapper.text()).not.toContain('other routes with the same method')
    })

    it('warns on screen and in the prompt when only the method matched (S-7 AC 12, AC 13)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({ fallback: 'ORIGIN_AND_DESTINATION_IGNORED' }),
      )

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, { method: 'AIR', origin: 'VN', destination: 'CH' })
      await promptReady(wrapper)

      const warning =
        'Fewer than 3 deliveries on this route; other routes with the same method are included.'
      expect(comparablesList(wrapper).text()).toContain(warning)
      expect(promptText(wrapper)).toContain(warning)
    })

    it('shows only "Not enough comparable deliveries", with no prompt and no copy (S-7 AC 14)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({
          fallback: 'ORIGIN_AND_DESTINATION_IGNORED',
          removed_company_names: 0,
          comparables: [],
        }),
      )

      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, { method: 'RAIL', origin: 'CN', destination: 'PL' })

      await vi.waitFor(() =>
        expect(comparablesList(wrapper).text()).toContain('Not enough comparable deliveries'),
      )
      expect(recordIds(wrapper)).toEqual([])
      expect(wrapper.text()).not.toContain('other routes with the same method')
      expect(wrapper.text()).not.toMatch(/ignored/i)
      expect(hasPromptBox(wrapper)).toBe(false)
      expect(canCopy(wrapper)).toBe(false)
    })
  })

  describe('prompt', () => {
    async function promptFor(response: EstimateComparables = estimateComparables()) {
      getEstimateComparables.mockResolvedValue(response)
      const rendered = await renderApp('/estimate')
      await fillPotentialDelivery(rendered.wrapper)
      await promptReady(rendered.wrapper)
      return { ...rendered, prompt: promptText(rendered.wrapper) }
    }

    it('follows the template order: role, date, delivery, warning, table, answer format (S-7 AC 16)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({ fallback: 'ORIGIN_AND_DESTINATION_IGNORED' }),
      )

      const positions = [
        /freight cost analyst/i,
        /2026-10-26/,
        /2026-11-20/,
        /Fewer than 3 deliveries on this route; other routes with the same method are included\./,
        /^\s*\|\s*Ref no/im,
        /low \/ likely \/ high/i,
        /Keep apart what comes from the data above and what comes from the web/i,
      ].map((pattern) => {
        const index = prompt.search(pattern)
        expect(index, String(pattern)).toBeGreaterThanOrEqual(0)
        return index
      })
      expect(positions).toEqual([...positions].sort((a, b) => a - b))
    })

    it('opens with the role line and today\'s date (S-7 AC 16.1, AC 16.2)', async () => {
      const { prompt } = await promptFor()

      const [roleLine] = prompt.split('\n').filter((line) => line.trim() !== '')
      expect(roleLine).toMatch(/freight cost analyst/i)
      expect(roleLine).toMatch(/broker/i)
      expect(prompt).toContain('2026-10-26')
    })

    it('describes the potential delivery with route, method, date, totals and chargeable kg (S-7 AC 16.3)', async () => {
      const { prompt } = await promptFor(estimateComparables({ chargeable_kg: 5000 }))

      const delivery = prompt.slice(prompt.indexOf('2026-10-26') + 10, prompt.search(/^\s*\|/m))
      expect(delivery).toMatch(/Ningbo, China/)
      expect(delivery).toMatch(/Munich, Germany/)
      expect(delivery).toMatch(/\bSea\b/)
      expect(delivery).toContain('2026-11-20')
      expect(delivery).toMatch(/Pallets\D*6\b/i)
      expect(delivery).toMatch(/Volume\D*5\b/i)
      expect(delivery).toMatch(/Gross weight\D*900\b/i)
      expect(delivery).toMatch(/Chargeable[^\n\d]*5000 kg/i)
      expect(delivery).not.toMatch(/cargo size unknown/i)
    })

    it('says "cargo size unknown" when the potential delivery has no size (S-7 AC 16.3)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({
          chargeable_kg: null,
          comparables: [estimateComparable({ chargeable_kg: null })],
        }),
      )
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await promptReady(wrapper)

      const prompt = promptText(wrapper)
      expect(prompt).toMatch(/cargo size unknown/i)
      expect(prompt).not.toMatch(/null|undefined|NaN/)
    })

    it('names the route by country without cities when none are given (S-7 AC 16.3)', async () => {
      getEstimateComparables.mockResolvedValue(
        estimateComparables({
          chargeable_kg: null,
          comparables: [estimateComparable({ chargeable_kg: null })],
        }),
      )
      const { wrapper } = await renderApp('/estimate')
      await setRoute(wrapper, seaChinaGermany)
      await promptReady(wrapper)

      const prompt = promptText(wrapper)
      const delivery = prompt.slice(0, prompt.search(/^\s*\|/m))
      expect(delivery).toContain('China')
      expect(delivery).toContain('Germany')
      expect(delivery).not.toMatch(/Ningbo|Munich/)
      expect(delivery).toMatch(/not given/i)
      expect(prompt).not.toMatch(/null|undefined|NaN/)
    })

    it('tabulates each comparable: ref, route, method, dates, age, m³, kg, chargeable, cost, notes (S-7 AC 16.5)', async () => {
      const { prompt } = await promptFor()

      const [header] = tableLines(prompt)
      expect(header).toHaveLength(11)
      const columns = [
        /ref no/i,
        /route/i,
        /method/i,
        /ship/i,
        /arriv/i,
        /age.*month/i,
        /m³/,
        /gross kg/i,
        /chargeable kg/i,
        /cost.*EUR/i,
        /notes/i,
      ]
      columns.forEach((pattern, i) => expect(header[i], String(pattern)).toMatch(pattern))

      const row = rowFor(prompt, 42)
      expect(row).toHaveLength(11)
      expect(row[1]).toMatch(/Ningbo, China.*Munich, Germany/)
      expect(row[2]).toBe('Sea')
      expect(row[3]).toBe('2026-03-10')
      expect(row[4]).toBe('2026-04-21')
      expect(row[5]).toBe('7')
      expect(row[6]).toBe('4.8')
      expect(row[7]).toBe('900')
      expect(row[8]).toBe('4800')
      expect(row[9]).toBe('4100')
      expect(row[10]).toBe('[customer] wanted it fast, rush premium')

      expect(tableLines(prompt).slice(1).map((cells) => cells[0])).toEqual(['42', '57', '19'])
    })

    it('marks missing comparable values with "-" and explains it under the table (S-7 AC 16.8)', async () => {
      const { prompt } = await promptFor()

      const row = rowFor(prompt, 19)
      expect(row).toHaveLength(11)
      expect(row[1]).toMatch(/Shenzhen, China.*Hamburg, Germany/)
      expect(row[3]).toBe('2025-05-06')
      expect(row[4]).toBe('-')
      expect(row[6]).toBe('-')
      expect(row[7]).toBe('3900')
      expect(row[10]).toBe('-')
      expect(prompt).not.toMatch(/null|undefined|NaN/)

      const lines = prompt.split('\n')
      const afterTable = lines.length - [...lines].reverse().findIndex((line) => line.trim().startsWith('|'))
      const legend = lines.slice(afterTable).find((line) => line.trim() !== '')
      expect(legend).toMatch(/-.*not given/i)
    })

    it('writes numbers plain: no separators, at most 2 decimals, no trailing zeros (S-7 AC 16.7)', async () => {
      vi.mocked(client.parseExcel).mockResolvedValue(parsedPackingList())
      getEstimateComparables.mockResolvedValue(
        estimateComparables({
          chargeable_kg: 7420,
          comparables: [
            estimateComparable({
              volume_m3: 7.42,
              gross_weight_kg: 1580.5,
              chargeable_kg: 7420.456,
              cost_eur: 12500.1,
            }),
          ],
        }),
      )
      const { wrapper } = await renderApp('/estimate')
      await pickFiles(control(wrapper, 'Packing list Excel file'), [packingList])
      await vi.waitFor(() =>
        expect(control(section(wrapper, 'Cargo totals'), 'Volume (m³)').element.value).toBe('7.42'),
      )
      await setRoute(wrapper, seaChinaGermany)
      await promptReady(wrapper)

      const prompt = promptText(wrapper)
      const delivery = prompt.slice(0, prompt.search(/^\s*\|/m))
      expect(delivery).toMatch(/Volume\D*7\.42\b/i)
      expect(delivery).toMatch(/Gross weight\D*1580\b/i)
      expect(delivery).toMatch(/Chargeable[^\n\d]*7420 kg/i)
      const row = rowFor(prompt, 42)
      expect(row[6]).toBe('7.42')
      expect(row[7]).toBe('1580.5')
      expect(row[8]).toBe('7420.46')
      expect(row[9]).toBe('12500.1')
      expect(prompt).not.toMatch(/\d,\d{3}/)
    })

    it('keeps a note on one table row: line breaks become " / ", "|" becomes "/" (S-7 AC 16.9)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [
            estimateComparable({ notes: 'Rush premium\nPort storage 2 days | paid by [customer]' }),
          ],
        }),
      )

      const row = rowFor(prompt, 42)
      expect(row).toHaveLength(11)
      expect(row[10]).toBe('Rush premium / Port storage 2 days / paid by [customer]')
    })

    it('shows "-" as the shipping date of a comparable without one, at age 0 (S-9 AC 23)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [
            estimateComparable({
              id: 73,
              shipping_date: null,
              expected_arrival_date: null,
              arrival_date: null,
              age_months: 0,
              cost_eur: null,
              offer_price_eur: 4300,
            }),
          ],
        }),
      )

      const row = rowFor(prompt, 73)
      expect(row).toHaveLength(11)
      expect(row[3]).toBe('-')
      expect(row[4]).toBe('-')
      expect(row[5]).toBe('0')
      expect(prompt).not.toMatch(/null|undefined|NaN/)
    })

    it('prints the real arrival of a comparable, not the expected one, when both are set (S-10 AC 18)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [
            estimateComparable({ id: 42, expected_arrival_date: '2026-04-18', arrival_date: '2026-04-21' }),
          ],
        }),
      )

      const row = rowFor(prompt, 42)
      expect(row).toHaveLength(11)
      expect(row[4]).toBe('2026-04-21')
      expect(prompt).not.toContain('2026-04-18')
      expect(prompt).not.toContain('(expected)')
    })

    it('prints the expected arrival marked "(expected)" for a comparable without a real arrival (S-10 AC 18)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [
            estimateComparable({ id: 42, expected_arrival_date: '2026-04-18', arrival_date: '2026-04-21' }),
            estimateComparable({ id: 73, expected_arrival_date: '2026-11-09', arrival_date: null }),
          ],
        }),
      )

      expect(rowFor(prompt, 42)[4]).toBe('2026-04-21')
      const row = rowFor(prompt, 73)
      expect(row).toHaveLength(11)
      expect(row[4]).toContain('2026-11-09')
      expect(row[4]).toContain('(expected)')
    })

    it('prints "-" for a comparable with neither arrival (S-10 AC 18)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [estimateComparable({ id: 73, expected_arrival_date: null, arrival_date: null })],
        }),
      )

      expect(rowFor(prompt, 73)[4]).toBe('-')
      expect(prompt).not.toContain('(expected)')
      expect(prompt).not.toMatch(/null|undefined|NaN/)
    })

    it('prices a comparable by its real price, else by its offer price marked as an offer (S-9 AC 24)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          comparables: [
            estimateComparable({ id: 42, cost_eur: 4100, offer_price_eur: 3900 }),
            estimateComparable({ id: 57, cost_eur: 4650, offer_price_eur: null }),
            estimateComparable({ id: 73, cost_eur: null, offer_price_eur: 4300 }),
          ],
        }),
      )

      expect(rowFor(prompt, 42)[9]).toBe('4100')
      expect(rowFor(prompt, 57)[9]).toBe('4650')

      const offerRow = rowFor(prompt, 73)
      expect(offerRow).toHaveLength(11)
      expect(offerRow[9]).toMatch(/\b4300\b/)
      expect(offerRow[9]).toMatch(/offer/i)
      expect(prompt).not.toMatch(/\b3900\b/)
      expect(prompt).not.toMatch(/null|undefined|NaN/)

      const lines = prompt.split('\n')
      const afterTable = lines.length - [...lines].reverse().findIndex((line) => line.trim().startsWith('|'))
      const answerFormat = lines.findIndex((line, i) => i >= afterTable && /answer/i.test(line))
      const underTable = lines.slice(afterTable, answerFormat).join('\n')
      expect(underTable).toMatch(/offer/i)
      expect(underTable).toMatch(/not (a )?final cost/i)
    })

    it('asks for the six answer items in order, with the market check as item 3 (S-7 AC 16.6)', async () => {
      const { prompt } = await promptFor()

      const answer = prompt.slice(prompt.search(/answer/i))
      const starts = [1, 2, 3, 4, 5, 6].map((n) => {
        const index = answer.search(new RegExp(`^\\s*\\(?${n}[.)]`, 'm'))
        expect(index, `item ${n}`).toBeGreaterThanOrEqual(0)
        return index
      })
      expect(starts).toEqual([...starts].sort((a, b) => a - b))
      expect(answer.search(/^\s*\(?7[.)]/m)).toBe(-1)
      const item = (n: number) => answer.slice(starts[n - 1], starts[n] ?? undefined)

      expect(item(1)).toMatch(/what the cost seems to include/i)
      expect(item(1)).toMatch(/anomal/i)
      expect(item(2)).toMatch(/refs to ignore/i)
      expect(item(2)).toMatch(/why/i)
      for (const pattern of [
        /market check/i,
        /search the web/i,
        /current rates/i,
        /surcharges/i,
        /route/i,
        /method/i,
        /source/i,
        /link/i,
        /date/i,
        /comparables/i,
      ]) {
        expect(item(3), String(pattern)).toMatch(pattern)
      }
      expect(item(4)).toMatch(/expected buy cost/i)
      expect(item(4)).toMatch(/EUR/)
      expect(item(4)).toMatch(/low \/ likely \/ high/i)
      expect(item(4)).toMatch(/comparables adjusted by (item 3|the market check)/i)
      expect(item(5)).toMatch(/refs that weighed most/i)
      expect(item(6)).toMatch(/warnings/i)
    })

    it('ends by asking to keep the data and the web apart, no longer "use only the data given" (S-7 AC 16.6)', async () => {
      const { prompt } = await promptFor()

      expect(prompt.trim()).toMatch(
        /Keep apart what comes from the data above and what comes from the web\.$/,
      )
      expect(prompt).not.toMatch(/use only the data given/i)
    })

    it('carries the notes with company names already replaced by [customer] (S-7 AC 17, AC 18)', async () => {
      const { prompt } = await promptFor(
        estimateComparables({
          removed_company_names: 2,
          comparables: [
            estimateComparable({ notes: '[customer] split invoice with [customer]' }),
          ],
        }),
      )

      expect(rowFor(prompt, 42)[10]).toBe('[customer] split invoice with [customer]')
    })

    it('counts the company names removed from notes above the prompt (S-7 AC 18)', async () => {
      const { wrapper } = await promptFor(estimateComparables({ removed_company_names: 3 }))

      expect(section(wrapper, 'Prompt for Claude').text()).toContain(
        '3 company names removed from notes',
      )
    })

    it('counts a single removed company name in the singular (S-7 AC 18)', async () => {
      const { wrapper } = await promptFor(estimateComparables({ removed_company_names: 1 }))

      expect(section(wrapper, 'Prompt for Claude').text()).toContain(
        '1 company name removed from notes',
      )
    })

    it('follows form changes live while Alice has not edited it (S-7 AC 19)', async () => {
      const { wrapper } = await promptFor()

      await control(wrapper, 'Planned shipping date').setValue('2026-12-01')
      await control(section(wrapper, 'Destination'), 'City').setValue('Augsburg')
      await control(section(wrapper, 'Cargo totals'), 'Pallets').setValue('14')

      await vi.waitFor(() => expect(promptText(wrapper)).toContain('2026-12-01'))
      const prompt = promptText(wrapper)
      expect(prompt).toContain('Augsburg, Germany')
      expect(prompt).toMatch(/Pallets\D*14\b/i)
      expect(prompt).not.toContain('2026-11-20')
    })

    it('keeps Alice\'s edits when the form changes and warns the prompt is out of date (S-7 AC 19)', async () => {
      const { wrapper } = await promptFor()
      const edited = `${promptText(wrapper)}\nThe customer needs it before 15 Dec.`
      await promptBox(wrapper).setValue(edited)
      getEstimateComparables.mockResolvedValue(
        estimateComparables({ comparables: [estimateComparable({ id: 63 })] }),
      )

      await control(section(wrapper, 'Cargo totals'), 'Volume (m³)').setValue('9')
      await control(wrapper, 'Planned shipping date').setValue('2026-12-01')

      await vi.waitFor(() => expect(recordIds(wrapper)).toEqual([63]))
      expect(promptText(wrapper)).toBe(edited)
      expect(button(section(wrapper, 'Prompt for Claude'), 'Discard my edits and rebuild prompt').exists()).toBe(true)
    })

    it('rebuilds the prompt from the current form when Alice discards her edits (S-7 AC 19)', async () => {
      const { wrapper } = await promptFor()
      await promptBox(wrapper).setValue('My own prompt.')
      await control(wrapper, 'Planned shipping date').setValue('2026-12-01')
      await flushPromises()

      await button(section(wrapper, 'Prompt for Claude'), 'Discard my edits and rebuild prompt').trigger('click')

      await vi.waitFor(() => expect(promptText(wrapper)).toContain('2026-12-01'))
      expect(promptText(wrapper)).not.toContain('My own prompt.')
      await control(section(wrapper, 'Destination'), 'City').setValue('Augsburg')
      await vi.waitFor(() => expect(promptText(wrapper)).toContain('Augsburg'))
    })

    it('shows Alice\'s edited prompt again when comparables return after none were found (S-7 AC 14, AC 19)', async () => {
      const { wrapper } = await promptFor()
      await promptBox(wrapper).setValue('My own prompt.')
      getEstimateComparables.mockResolvedValue(estimateComparables({ comparables: [] }))

      await control(section(wrapper, 'Destination'), 'Country').setValue('PL')
      await vi.waitFor(() =>
        expect(comparablesList(wrapper).text()).toContain('Not enough comparable deliveries'),
      )
      expect(hasPromptBox(wrapper)).toBe(false)

      getEstimateComparables.mockResolvedValue(estimateComparables())
      await control(section(wrapper, 'Destination'), 'Country').setValue('DE')

      await promptReady(wrapper)
      expect(promptText(wrapper)).toBe('My own prompt.')
    })
  })

  describe('copy', () => {
    it('copies the prompt to the clipboard (S-7 AC 20)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)
      await promptReady(wrapper)
      expect(canCopy(wrapper)).toBe(true)

      await button(wrapper, 'Copy prompt').trigger('click')

      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(promptText(wrapper)))
    })

    it('copies the text as Alice edited it (S-7 AC 20)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await fillPotentialDelivery(wrapper)
      await promptReady(wrapper)
      await promptBox(wrapper).setValue('Estimate this for me, please.')

      await button(wrapper, 'Copy prompt').trigger('click')

      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith('Estimate this for me, please.'))
    })

    it('cannot copy before the required fields are set (S-7 AC 20)', async () => {
      const { wrapper } = await renderApp('/estimate')
      await control(wrapper, 'Transport method').setValue('SEA')
      await control(section(wrapper, 'Cargo totals'), 'Volume (m³)').setValue('5')
      await flushPromises()

      expect(canCopy(wrapper)).toBe(false)
      expect(writeText).not.toHaveBeenCalled()
    })

    describe('copy confirmation', () => {
      const copied = 'Prompt copied. Paste it into Claude.'

      async function copyPrompt(wrapper: VueWrapper) {
        await button(wrapper, 'Copy prompt').trigger('click')
        await vi.waitFor(() => expect(wrapper.text()).toContain(copied))
      }

      it('clears "Prompt copied" when a form change rebuilds the prompt (S-7 AC 19, AC 20)', async () => {
        const { wrapper } = await renderApp('/estimate')
        await fillPotentialDelivery(wrapper)
        await promptReady(wrapper)
        await copyPrompt(wrapper)

        await control(wrapper, 'Planned shipping date').setValue('2026-12-01')

        await vi.waitFor(() => expect(promptText(wrapper)).toContain('2026-12-01'))
        expect(wrapper.text()).not.toContain(copied)
      })

      it('clears "Prompt copied" while the comparables are looked up again (S-7 AC 15, AC 20)', async () => {
        const { wrapper } = await renderApp('/estimate')
        await fillPotentialDelivery(wrapper)
        await promptReady(wrapper)
        await copyPrompt(wrapper)
        getEstimateComparables.mockReturnValue(new Promise<EstimateComparables>(() => {}))

        await control(section(wrapper, 'Destination'), 'Country').setValue('FR')

        await vi.waitFor(() =>
          expect(comparablesList(wrapper).text()).toContain('Finding comparable deliveries'),
        )
        expect(wrapper.text()).not.toContain(copied)
      })

      it('clears "Prompt copied" when Alice discards her edits and rebuilds (S-7 AC 19, AC 20)', async () => {
        const { wrapper } = await renderApp('/estimate')
        await fillPotentialDelivery(wrapper)
        await promptReady(wrapper)
        await promptBox(wrapper).setValue('My own prompt.')
        await control(wrapper, 'Planned shipping date').setValue('2026-12-01')
        await copyPrompt(wrapper)

        await button(
          section(wrapper, 'Prompt for Claude'),
          'Discard my edits and rebuild prompt',
        ).trigger('click')

        await vi.waitFor(() => expect(promptText(wrapper)).toContain('2026-12-01'))
        expect(wrapper.text()).not.toContain(copied)
      })
    })
  })

  describe('lookup while typing cargo totals', () => {
    async function renderWithComparables() {
      const rendered = await renderApp('/estimate')
      await setRoute(rendered.wrapper, seaChinaGermany)
      await promptReady(rendered.wrapper)
      vi.useRealTimers()
      vi.useFakeTimers({
        toFake: ['Date', 'setTimeout', 'clearTimeout'],
        now: new Date(2026, 9, 26, 10, 0),
      })
      return { ...rendered, lookups: getEstimateComparables.mock.calls.length }
    }

    async function type(wrapper: VueWrapper, label: string, keystrokes: string) {
      const input = control(section(wrapper, 'Cargo totals'), label)
      for (let length = 1; length <= keystrokes.length; length++) {
        await input.setValue(keystrokes.slice(0, length))
        await vi.advanceTimersByTimeAsync(100)
      }
    }

    it('looks up once, 300 ms after typing in volume pauses (S-7 AC 19)', async () => {
      const { wrapper, lookups } = await renderWithComparables()

      await type(wrapper, 'Volume (m³)', '7.42')
      await vi.advanceTimersByTimeAsync(199)
      await flushPromises()

      expect(getEstimateComparables).toHaveBeenCalledTimes(lookups)
      expect(recordIds(wrapper)).toEqual([42, 57, 19])
      expect(comparablesList(wrapper).text()).not.toContain('Finding comparable deliveries')
      expect(hasPromptBox(wrapper)).toBe(true)

      await vi.advanceTimersByTimeAsync(1)
      await flushPromises()

      expect(getEstimateComparables).toHaveBeenCalledTimes(lookups + 1)
      expect(asNumbers(lastQuery())).toMatchObject({ volume_m3: 7.42 })
    })

    it('looks up once with both values when volume and weight are typed without a pause (S-7 AC 19)', async () => {
      const { wrapper, lookups } = await renderWithComparables()

      await type(wrapper, 'Volume (m³)', '4.8')
      await type(wrapper, 'Gross weight (kg)', '900')
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(getEstimateComparables).toHaveBeenCalledTimes(lookups + 1)
      expect(asNumbers(lastQuery())).toMatchObject({ volume_m3: 4.8, gross_weight_kg: 900 })
    })

    it('looks up again after each pause in typing (S-7 AC 19)', async () => {
      const { wrapper, lookups } = await renderWithComparables()

      await type(wrapper, 'Gross weight (kg)', '90')
      await vi.advanceTimersByTimeAsync(300)
      await type(wrapper, 'Gross weight (kg)', '900')
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(getEstimateComparables).toHaveBeenCalledTimes(lookups + 2)
      expect(asNumbers(lastQuery())).toMatchObject({ gross_weight_kg: 900 })
    })

    it('looks nothing up when Alice leaves the screen before typing pauses (S-7 AC 5)', async () => {
      const { wrapper, router, lookups } = await renderWithComparables()

      await type(wrapper, 'Volume (m³)', '7.42')
      await router.push('/')
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(getEstimateComparables).toHaveBeenCalledTimes(lookups)
    })
  })
})

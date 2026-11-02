import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { DeliveryDetail } from '../../src/api/types'
import { formatDateTime } from '../../src/format'
import { DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { button, link, renderApp, section, unmountAll } from '../support/app'
import { deliveryDetail, deliveryItem, deliveryPdf } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  listDeliveries: vi.fn(),
  getDelivery: vi.fn(),
  deleteDelivery: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const getDelivery = vi.mocked(client.getDelivery)

const fullRecord = deliveryDetail({
  totals_edited: { volume_m3: true, gross_weight_kg: false, pallet_count: false },
  item_sums: { volume_m3: 7.1, gross_weight_kg: 1580, pallet_count: 6 },
  items: [
    deliveryItem(),
    deliveryItem({
      source_row: 6,
      description: null,
      article_no: 'SH-1200-ASH',
      length_mm: null,
      volume_m3: 3.4,
      gross_weight_kg: null,
      net_weight_kg: 0,
      volume_mismatch: true,
    }),
  ],
  pdfs: [
    deliveryPdf(),
    deliveryPdf({
      id: 8,
      original_filename: 'Bill of lading.pdf',
      size_bytes: 3_250_000,
      sort_order: 1,
      uploaded_at: '2026-01-17T14:30:00Z',
    }),
  ],
})

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(client.listDeliveries).mockResolvedValue([])
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
})

afterEach(unmountAll)

function deliveryValue(wrapper: VueWrapper, term: string): string {
  const dt = section(wrapper, 'Delivery')
    .findAll('dt')
    .find((t) => t.text() === term)
  if (!dt) throw new Error(`No "${term}" in the Delivery panel`)
  const dd = dt.element.nextElementSibling
  if (!dd || dd.tagName !== 'DD') throw new Error(`No value for "${term}" in the Delivery panel`)
  return new DOMWrapper(dd).text()
}

describe('DeliveryDetailPage', () => {
  it('shows a loading message until the record arrives (S-1 AC 6)', async () => {
    getDelivery.mockReturnValue(new Promise<DeliveryDetail>(() => {}))

    const { wrapper } = await renderApp('/deliveries/1')

    expect(wrapper.text()).toContain('Loading delivery…')
  })

  it('renders the route, transport, dates and cost of the record (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1')

    expect(getDelivery).toHaveBeenCalledWith(1)
    expect(wrapper.find('h1').text()).toBe('Example Interiors')
    expect(wrapper.text()).toMatch(/Ningbo, China\s*→\s*Munich, Germany\s*·\s*Sea/)

    const delivery = section(wrapper, 'Delivery').text()
    expect(delivery).toContain('Customer company')
    expect(delivery).toContain('Transport method')
    expect(delivery).toContain('Sea')
    expect(delivery).toContain('15 Jan 2026')
    expect(delivery).toContain('02 Mar 2026')
    expect(delivery).toContain('€4,210.00')

    const route = section(wrapper, 'Route').text()
    expect(route).toContain('88 Harbour Road')
    expect(route).toContain('315000 Ningbo')
    expect(route).toContain('China')
    expect(route).toContain('Marienplatz 1')
    expect(route).toContain('80331 Munich')
    expect(route).toContain('Germany')
    expect(route).not.toMatch(/\bCN\b|\bDE\b/)
  })

  it('renders the notes in full (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1')

    expect(section(wrapper, 'Notes').text()).toContain(
      'Price included a tail-lift truck for the last mile.\nCustomer paid the port storage.',
    )
  })

  it('renders the cargo totals, marking an edited total with the line-item sum (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1')

    const totals = section(wrapper, 'Cargo totals')
    const text = totals.text()
    expect(text).toMatch(/Volume\s*7\.42\s*m³\s*edited\s*line items sum to 7\.1 m³/)
    expect(text).toMatch(/Gross weight\s*1,580\s*kg/)
    expect(text).toMatch(/Pallets\s*6/)
    expect(text.match(/edited/g)).toHaveLength(1)

    const excel = link(totals, 'Download original Excel (QX20260115 packing list.xlsx)')
    expect(excel.attributes('href')).toBe('/api/deliveries/1/excel')
  })

  it('renders every item row, flagging volume mismatches (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1')

    const items = section(wrapper, 'Item rows (2)')
    const rows = items.findAll('tbody tr')
    expect(rows).toHaveLength(2)

    const cells = (index: number) =>
      rows[index].findAll('td').map((td) => td.text().replace(/\s+/g, ' '))
    expect(cells(0)).toEqual([
      '5',
      'Shelving unit, oak',
      'SH-1200-OAK',
      '1,200',
      '800',
      '1,050',
      '2',
      '2.02',
      '480',
      '395.5',
    ])
    expect(cells(1)).toEqual(['6', '—', 'SH-1200-ASH', '—', '800', '1,050', '2', '3.4 ⚠', '—', '0'])
    expect(items.text()).toContain('1 row where the stated volume disagrees')
  })

  it('lists every PDF with its size and shows the first one in the viewer (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1')

    const documents = section(wrapper, 'Delivery documents (2)')
    const text = documents.text()
    expect(text).toContain('QX20260115 delivery note.pdf')
    expect(text).toContain(`277 kB · ${formatDateTime('2026-01-16T09:12:00Z')}`)
    expect(text).toContain('Bill of lading.pdf')
    expect(text).toContain(`3.1 MB · ${formatDateTime('2026-01-17T14:30:00Z')}`)

    const downloads = documents.findAll('a').filter((a) => a.text() === 'Download')
    expect(downloads.map((a) => a.attributes('href'))).toEqual([
      '/api/deliveries/1/pdfs/7/content',
      '/api/deliveries/1/pdfs/8/content',
    ])
    expect(documents.find('iframe').attributes('src')).toBe('/api/deliveries/1/pdfs/7/content')

    await documents.findAll('[role="tab"]')[1].trigger('click')
    expect(documents.find('iframe').attributes('src')).toBe('/api/deliveries/1/pdfs/8/content')
  })

  it('shows missing optional values as such, not as blanks or zeros (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(
      deliveryDetail({
        arrival_date: null,
        origin_postal_code: null,
        origin_street: null,
        volume_m3: null,
        gross_weight_kg: null,
        pallet_count: null,
        notes: null,
        excel_filename: null,
        item_sums: { volume_m3: null, gross_weight_kg: null, pallet_count: null },
        items: [],
        pdfs: [],
        pdf_count: 0,
      }),
    )

    const { wrapper } = await renderApp('/deliveries/1')

    expect(deliveryValue(wrapper, 'Arrival')).toBe('—')
    expect(section(wrapper, 'Route').text()).toMatch(/Origin\s*Ningbo\s*China/)
    expect(section(wrapper, 'Cargo totals').text()).toMatch(
      /Volume\s*—\s*m³\s*Gross weight\s*—\s*kg\s*Pallets\s*—/,
    )
    expect(wrapper.text()).toContain('No Excel attached')
    expect(wrapper.text()).toContain('No item rows')
    expect(wrapper.text()).toContain('No PDFs attached to this delivery.')
    expect(wrapper.findAll('h3').map((h) => h.text())).not.toContain('Notes')
  })

  describe('status and prices', () => {
    it('shows the status of the delivery (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/deliveries/1')

      expect(section(wrapper, 'Delivery').text()).toMatch(/Status\s*Offer accepted/)
    })

    it('labels the cost "Real price" and shows the offer price beside it (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: 3900, cost_eur: 4250 }))

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Offer price\s*€3,900\.00/)
      expect(delivery).toMatch(/Real price\s*€4,250\.00/)
      expect(wrapper.text()).not.toMatch(/\bCost\b/i)
    })

    it('shows how far the real price moved above the offer, in EUR and percent (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: 3900, cost_eur: 4250 }))

      const { wrapper } = await renderApp('/deliveries/1')

      expect(section(wrapper, 'Delivery').text()).toMatch(/Difference\s*\+€350\.00 · \+9\.0%/)
    })

    it('shows a real price below the offer with a minus sign (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: 2480, cost_eur: 2300 }))

      const { wrapper } = await renderApp('/deliveries/1')

      expect(section(wrapper, 'Delivery').text()).toMatch(/Difference\s*[−-]€180\.00 · [−-]7\.3%/)
    })

    it('shows a real price equal to the offer as an unsigned zero difference (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: 4250, cost_eur: 4250 }))

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Difference\s*€0\.00 · 0\.0%/)
      expect(delivery).not.toMatch(/[+−-]€0\.00|[+−-]0\.0%/)
    })

    it('shows the difference to an offer price of 0 in EUR only, with no percentage (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: 0, cost_eur: 350 }))

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Offer price\s*€0\.00/)
      expect(delivery).toMatch(/Difference\s*\+€350\.00/)
      expect(delivery).not.toMatch(/%|Infinity|∞|NaN/)
    })

    it('shows no difference while only the offer price is set (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({ status: 'OFFER_ACCEPTED', offer_price_eur: 5100, cost_eur: null }),
      )

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Offer price\s*€5,100\.00/)
      expect(delivery).toMatch(/Real price\s*—/)
      expect(delivery).not.toContain('Difference')
      expect(delivery).not.toMatch(/NaN|null|undefined/)
    })

    it('shows no difference while only the real price is set (S-9 AC 19)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ offer_price_eur: null, cost_eur: 4210 }))

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Offer price\s*—/)
      expect(delivery).not.toContain('Difference')
    })

    it('shows missing dates of a running delivery as "—" (S-9 AC 4, S-10 AC 8)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({
          status: 'TO_DO',
          shipping_date: null,
          expected_arrival_date: null,
          arrival_date: null,
          cost_eur: null,
        }),
      )

      const { wrapper } = await renderApp('/deliveries/1')

      const delivery = section(wrapper, 'Delivery').text()
      expect(delivery).toMatch(/Shipping date\s*—/)
      expect(deliveryValue(wrapper, 'Expected arrival')).toBe('—')
      expect(deliveryValue(wrapper, 'Arrival')).toBe('—')
      expect(delivery).not.toMatch(/NaN|null|undefined|1970/)
    })
  })

  describe('arrival dates', () => {
    it('shows the expected and the real arrival (S-10 AC 1, AC 8)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({ expected_arrival_date: '2026-10-24', arrival_date: '2026-10-29' }),
      )

      const { wrapper } = await renderApp('/deliveries/1')

      expect(deliveryValue(wrapper, 'Expected arrival')).toContain('24 Oct 2026')
      expect(deliveryValue(wrapper, 'Arrival')).toContain('29 Oct 2026')
    })

    for (const [expected, arrival, difference] of [
      ['2026-10-24', '2026-10-29', '5 days late'],
      ['2026-10-28', '2026-10-29', '1 day late'],
      ['2026-10-29', '2026-10-27', '2 days early'],
      ['2026-10-31', '2026-10-30', '1 day early'],
      ['2026-10-28', '2026-10-28', 'on time'],
      ['2026-12-28', '2027-01-04', '7 days late'],
      ['2026-03-27', '2026-03-30', '3 days late'],
    ] as const) {
      it(`shows the real minus the expected arrival as "${difference}" for ${expected} and ${arrival} (S-10 AC 8)`, async () => {
        getDelivery.mockResolvedValue(
          deliveryDetail({ expected_arrival_date: expected, arrival_date: arrival }),
        )

        const { wrapper } = await renderApp('/deliveries/1')

        const shown = deliveryValue(wrapper, 'Arrival')
        expect(shown).toMatch(new RegExp(`\\b${difference}$`))
        expect(shown).not.toMatch(/NaN|null|undefined|-\d/)
      })
    }

    it('shows no difference without an expected arrival (S-10 AC 8)', async () => {
      getDelivery.mockResolvedValue(deliveryDetail({ expected_arrival_date: null, arrival_date: '2026-03-02' }))

      const { wrapper } = await renderApp('/deliveries/1')

      expect(deliveryValue(wrapper, 'Arrival')).toBe('02 Mar 2026')
      expect(section(wrapper, 'Delivery').text()).not.toMatch(/late|early|on time/)
    })

    it('shows no difference and no overdue mark while only the expected arrival is set (S-10 AC 8)', async () => {
      getDelivery.mockResolvedValue(
        deliveryDetail({ status: 'ON_THE_WAY', expected_arrival_date: '2026-10-23', arrival_date: null }),
      )

      const { wrapper } = await renderApp('/deliveries/1')

      expect(deliveryValue(wrapper, 'Expected arrival')).toBe('23 Oct 2026')
      expect(deliveryValue(wrapper, 'Arrival')).toBe('—')
      expect(section(wrapper, 'Delivery').text()).not.toMatch(/late|early|on time/)
    })
  })

  it('leads back to the list at /deliveries (S-9 AC 20)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper, router } = await renderApp('/deliveries/1')
    await link(wrapper, 'Back to list').trigger('click')

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
  })

  it('shows "Not found" for an unknown id (S-1 AC 6)', async () => {
    getDelivery.mockRejectedValue(new ApiError(404, 'not_found', { error: 'not_found' }))

    const { wrapper } = await renderApp('/deliveries/999')

    expect(getDelivery).toHaveBeenCalledWith(999)
    expect(wrapper.find('[role="alert"]').text()).toContain('Not found.')
    expect(wrapper.find('h1').exists()).toBe(false)
  })

  it('navigates to the edit form (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper, router } = await renderApp('/deliveries/1')
    await link(wrapper, 'Edit').trigger('click')

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/1/edit'))
    await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('Edit delivery'))
  })

  it('deletes the record after confirmation and returns to the list (S-1 AC 6, S-9 AC 20)', async () => {
    getDelivery.mockResolvedValue(fullRecord)
    vi.mocked(client.deleteDelivery).mockResolvedValue(undefined)

    const { wrapper, router } = await renderApp('/deliveries/1')
    await link(wrapper, 'Edit').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('Edit delivery'))

    await button(wrapper, 'Delete delivery').trigger('click')
    expect(wrapper.text()).toContain('This cannot be undone.')
    expect(client.deleteDelivery).not.toHaveBeenCalled()

    await button(wrapper, 'Yes, delete').trigger('click')

    expect(client.deleteDelivery).toHaveBeenCalledWith(1)
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
  })

  it('keeps the record when Alice backs out of the delete (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)

    const { wrapper } = await renderApp('/deliveries/1/edit')
    await button(wrapper, 'Delete delivery').trigger('click')
    await button(wrapper, 'Keep it').trigger('click')

    expect(client.deleteDelivery).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('This cannot be undone.')
  })

  it('shows the error when the delete fails and stays on the record (S-1 AC 6)', async () => {
    getDelivery.mockResolvedValue(fullRecord)
    vi.mocked(client.deleteDelivery).mockRejectedValue(
      new ApiError(500, 'internal', { error: 'internal', detail: 'Files could not be removed' }),
    )

    const { wrapper, router } = await renderApp('/deliveries/1/edit')
    await button(wrapper, 'Delete delivery').trigger('click')
    await button(wrapper, 'Yes, delete').trigger('click')

    await vi.waitFor(() =>
      expect(wrapper.find('[role="alert"]').text()).toContain('Files could not be removed'),
    )
    expect(router.currentRoute.value.fullPath).toBe('/deliveries/1/edit')
  })
})

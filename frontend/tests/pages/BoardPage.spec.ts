import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, type VueWrapper } from '@vue/test-utils'
import * as client from '../../src/api/client'
import { ApiError } from '../../src/api/client'
import type { BoardCard } from '../../src/api/types'
import { button, control, fieldOf, link, renderApp, section, unmountAll } from '../support/app'
import { deferred } from '../support/deferred'
import { boardCard, deliveryDetail } from '../support/fixtures'

vi.mock('../../src/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/api/client')>()),
  getBoard: vi.fn(),
  moveDelivery: vi.fn(),
  listDeliveries: vi.fn(),
  getDelivery: vi.fn(),
  suggestCompanies: vi.fn(),
  suggestCountries: vi.fn(),
  suggestCities: vi.fn(),
}))

const getBoard = vi.mocked(client.getBoard)
const moveDelivery = vi.mocked(client.moveDelivery)

// jsdom has no modal dialogs; this gives <dialog> the open/close behaviour of a browser.
if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.show = HTMLDialogElement.prototype.showModal
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) return
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}

const STATUS_COLUMNS = ['To do', 'Offers received', 'Offer accepted', 'On the way', 'Delivered']

const sunGarden = boardCard({
  id: 61,
  status: 'TO_DO',
  customer_company: 'Sun Garden Furniture',
  origin_country: 'CN',
  origin_city: 'Foshan',
  destination_country: 'NL',
  destination_city: 'Rotterdam',
  transport_method: 'SEA',
  shipping_date: null,
  expected_arrival_date: null,
  arrival_date: null,
  offer_price_eur: null,
})

const kesslerToDo = boardCard({
  id: 62,
  status: 'TO_DO',
  customer_company: 'Kessler Bikes GmbH',
  origin_country: 'TW',
  origin_city: 'Taichung',
  destination_country: 'DE',
  destination_city: 'Munich',
  transport_method: 'AIR',
  shipping_date: '2026-11-18',
  expected_arrival_date: null,
  arrival_date: null,
  offer_price_eur: null,
})

const nordlicht = boardCard({
  id: 63,
  status: 'OFFERS_RECEIVED',
  customer_company: 'Nordlicht Lampen AG',
  origin_country: 'CN',
  origin_city: 'Ningbo',
  destination_country: 'DE',
  destination_city: 'Hamburg',
  transport_method: 'SEA',
  shipping_date: null,
  expected_arrival_date: null,
  arrival_date: null,
  offer_price_eur: null,
})

const interiorsAccepted = boardCard({
  id: 64,
  status: 'OFFER_ACCEPTED',
  customer_company: 'Example Interiors',
  origin_country: 'CN',
  origin_city: 'Shenzhen',
  destination_country: 'FR',
  destination_city: 'Lyon',
  transport_method: 'RAIL',
  shipping_date: '2026-11-12',
  expected_arrival_date: null,
  arrival_date: null,
  offer_price_eur: 2480,
})

const brightwell = boardCard({
  id: 65,
  status: 'OFFER_ACCEPTED',
  customer_company: 'Brightwell Toys Ltd',
  origin_country: 'CN',
  origin_city: 'Xiamen',
  destination_country: 'GB',
  destination_city: 'Felixstowe',
  transport_method: 'SEA',
  shipping_date: null,
  expected_arrival_date: null,
  arrival_date: null,
  offer_price_eur: null,
})

const interiorsOnTheWay = boardCard()

const valmora = boardCard({
  id: 66,
  status: 'ON_THE_WAY',
  customer_company: 'Valmora Ceramics',
  origin_country: 'CN',
  origin_city: 'Chaozhou',
  destination_country: 'IT',
  destination_city: 'Milan',
  transport_method: 'ROAD',
  shipping_date: '2026-10-27',
  expected_arrival_date: '2026-11-09',
  arrival_date: null,
  offer_price_eur: null,
})

const kesslerDelivered = boardCard({
  id: 67,
  status: 'DELIVERED',
  customer_company: 'Kessler Bikes GmbH',
  origin_country: 'TW',
  origin_city: 'Taichung',
  destination_country: 'DE',
  destination_city: 'Munich',
  transport_method: 'AIR',
  shipping_date: '2026-10-28',
  expected_arrival_date: '2026-10-31',
  arrival_date: '2026-11-02',
  offer_price_eur: 6150,
})

// API order: last updated first, across all columns. Neither alphabetical nor by id, so a
// board that sorts the cards itself shows them in another order.
const board = [
  valmora,
  sunGarden,
  interiorsAccepted,
  kesslerDelivered,
  nordlicht,
  kesslerToDo,
  interiorsOnTheWay,
  brightwell,
]

function afterMove(card: BoardCard, changes: Partial<BoardCard>): BoardCard[] {
  return [{ ...card, ...changes }, ...board.filter((c) => c.id !== card.id)]
}

beforeEach(() => {
  vi.resetAllMocks()
  getBoard.mockResolvedValue(board)
  vi.mocked(client.listDeliveries).mockResolvedValue([])
  vi.mocked(client.suggestCompanies).mockResolvedValue([])
  vi.mocked(client.suggestCountries).mockResolvedValue([])
  vi.mocked(client.suggestCities).mockResolvedValue([])
})

afterEach(unmountAll)

function columnNames(wrapper: VueWrapper): string[] {
  return wrapper
    .findAll('section[aria-labelledby]')
    .map((column) => column.find(`[id="${column.attributes('aria-labelledby')}"]`).text())
}

function cardsIn(wrapper: VueWrapper, columnName: string): DOMWrapper<Element>[] {
  return section(wrapper, columnName).findAll('article')
}

function companiesIn(wrapper: VueWrapper, columnName: string): string[] {
  return cardsIn(wrapper, columnName).map((card) => card.find('a').text())
}

function card(wrapper: VueWrapper, columnName: string, company: string): DOMWrapper<Element> {
  const match = cardsIn(wrapper, columnName).find((c) => c.find('a').text() === company)
  if (!match) throw new Error(`No card "${company}" in column "${columnName}"`)
  return match
}

function menuEntries(target: DOMWrapper<Element>): string[] {
  return target.findAll('[role="menuitem"]').map((entry) => entry.text().replace(/…$/, ''))
}

async function openMoveMenu(target: DOMWrapper<Element>): Promise<void> {
  const toggle = target.findAll('button').find((b) => b.text().startsWith('Move to'))
  if (!toggle) throw new Error('No "Move to" button on the card')
  await toggle.trigger('click')
}

async function moveWithMenu(target: DOMWrapper<Element>, status: string): Promise<void> {
  await openMoveMenu(target)
  const entry = target
    .findAll('[role="menuitem"]')
    .find((e) => e.text().replace(/…$/, '') === status)
  if (!entry) throw new Error(`No "Move to" entry "${status}"`)
  await entry.trigger('click')
  await flushPromises()
}

function fakeDataTransfer() {
  const data = new Map<string, string>()
  return {
    dropEffect: 'none',
    effectAllowed: 'all',
    get types() {
      return [...data.keys()]
    },
    setData(format: string, value: string) {
      data.set(format, value)
    },
    getData(format: string) {
      return data.get(format) ?? ''
    },
    clearData() {
      data.clear()
    },
    setDragImage() {},
  }
}

async function dragOnto(
  wrapper: VueWrapper,
  dragged: DOMWrapper<Element>,
  columnName: string,
): Promise<void> {
  const dataTransfer = fakeDataTransfer()
  const target = section(wrapper, columnName)
  await dragged.trigger('dragstart', { dataTransfer })
  await target.trigger('dragenter', { dataTransfer })
  await target.trigger('dragover', { dataTransfer })
  await target.trigger('drop', { dataTransfer })
  await dragged.trigger('dragend', { dataTransfer })
  await flushPromises()
}

function openDialog(): DOMWrapper<Element> | null {
  const element = document.body.querySelector('dialog[open], [role="dialog"]')
  return element ? new DOMWrapper(element) : null
}

function dateDialog(): DOMWrapper<Element> {
  const dialog = openDialog()
  if (!dialog) throw new Error('No date dialog is open')
  return dialog
}

function focused(): DOMWrapper<Element> {
  if (!document.activeElement) throw new Error('Nothing has the focus')
  return new DOMWrapper(document.activeElement)
}

// Presses a key on the focused element. For Escape, it also does what a browser does with a
// modal dialog open and the key not handled: fire a cancelable "cancel" on the dialog, then close it.
async function press(key: string): Promise<void> {
  const keydown = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
  focused().element.dispatchEvent(keydown)
  const modal = document.body.querySelector<HTMLDialogElement>('dialog[open]')
  if (key === 'Escape' && modal && !keydown.defaultPrevented) {
    const cancel = new Event('cancel', { cancelable: true })
    modal.dispatchEvent(cancel)
    if (!cancel.defaultPrevented) modal.close()
  }
  await flushPromises()
}

// What Chrome does on a second Escape while the page prevents "cancel": it closes the modal
// dialog anyway and fires only "close".
async function closeDialogNatively(): Promise<void> {
  const modal = document.body.querySelector<HTMLDialogElement>('dialog[open]')
  if (!modal) throw new Error('No date dialog is open')
  modal.close()
  await flushPromises()
}

function dialogInPage(): boolean {
  return document.body.querySelector('dialog') !== null
}

function boardAlerts(wrapper: VueWrapper): string[] {
  return wrapper
    .findAll('[role="alert"]')
    .filter((alert) => !alert.element.closest('dialog'))
    .map((alert) => alert.text())
}

function expectFocusOnMoveButton(target: DOMWrapper<Element>): void {
  expect(focused().text()).toMatch(/^Move to/)
  expect(target.element.contains(document.activeElement)).toBe(true)
}

function hasControl(scope: DOMWrapper<Element>, label: string): boolean {
  return scope.findAll('label').some((l) => l.text() === label)
}

function lastMove(): {
  id: number
  status: string
  shipping_date?: string | null
  expected_arrival_date?: string | null
  arrival_date?: string | null
} {
  const call = moveDelivery.mock.lastCall
  if (!call) throw new Error('No move was sent')
  const [id, body] = call
  return { id, ...body }
}

describe('BoardPage', () => {
  describe('start page and navigation', () => {
    it('opens the board at / as the start page (S-9 AC 7)', async () => {
      const { wrapper, router } = await renderApp('/')

      expect(router.currentRoute.value.fullPath).toBe('/')
      expect(wrapper.find('h1').text()).toBe('Board')
      expect(getBoard).toHaveBeenCalled()
      expect(client.listDeliveries).not.toHaveBeenCalled()
    })

    it('reads Board, Deliveries, Estimate in the top navigation (S-9 AC 7)', async () => {
      const { wrapper } = await renderApp('/')

      const nav = wrapper.find('nav[aria-label="Main"]')
      expect(nav.findAll('a').map((a) => a.text())).toEqual(['Board', 'Deliveries', 'Estimate'])
    })

    it('opens the deliveries list at /deliveries from the navigation (S-9 AC 7, AC 20)', async () => {
      const { wrapper, router } = await renderApp('/')

      await link(wrapper, 'Deliveries').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries'))
      await vi.waitFor(() => expect(client.listDeliveries).toHaveBeenCalled())
    })

    it('comes back to the board from the navigation (S-9 AC 7)', async () => {
      const { wrapper, router } = await renderApp('/deliveries')

      await link(wrapper, 'Board').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/'))
      await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('Board'))
    })

    it('opens the board for an unknown path (S-9 AC 20)', async () => {
      const { wrapper, router } = await renderApp('/no/such/page')

      expect(router.currentRoute.value.fullPath).toBe('/')
      expect(wrapper.find('h1').text()).toBe('Board')
    })
  })

  describe('columns', () => {
    it('shows one column per status, in status order (S-9 AC 1, AC 8)', async () => {
      const { wrapper } = await renderApp('/')

      expect(columnNames(wrapper)).toEqual(STATUS_COLUMNS)
    })

    it('puts each card in the column of its status (S-9 AC 8, AC 9)', async () => {
      const { wrapper } = await renderApp('/')

      expect(companiesIn(wrapper, 'To do').sort()).toEqual(['Kessler Bikes GmbH', 'Sun Garden Furniture'])
      expect(companiesIn(wrapper, 'Offers received')).toEqual(['Nordlicht Lampen AG'])
      expect(companiesIn(wrapper, 'Offer accepted').sort()).toEqual(['Brightwell Toys Ltd', 'Example Interiors'])
      expect(companiesIn(wrapper, 'On the way').sort()).toEqual(['Example Interiors', 'Valmora Ceramics'])
      expect(companiesIn(wrapper, 'Delivered')).toEqual(['Kessler Bikes GmbH'])
    })

    it('keeps the order the API returns within each column, last updated first (S-9 AC 10)', async () => {
      const { wrapper } = await renderApp('/')

      expect(companiesIn(wrapper, 'To do')).toEqual(['Sun Garden Furniture', 'Kessler Bikes GmbH'])
      expect(companiesIn(wrapper, 'Offer accepted')).toEqual(['Example Interiors', 'Brightwell Toys Ltd'])
      expect(companiesIn(wrapper, 'On the way')).toEqual(['Valmora Ceramics', 'Example Interiors'])
    })
  })

  describe('card', () => {
    it('shows the customer company, the route and the transport method (S-9 AC 11)', async () => {
      const { wrapper } = await renderApp('/')

      const onTheWay = card(wrapper, 'On the way', 'Example Interiors').text()
      expect(onTheWay).toMatch(/Ningbo, China\s*→\s*Munich, Germany/)
      expect(onTheWay).toContain('Sea')
      expect(onTheWay).not.toMatch(/\bCN\b|\bDE\b|SEA/)

      const toDo = card(wrapper, 'To do', 'Kessler Bikes GmbH').text()
      expect(toDo).toMatch(/Taichung, Taiwan\s*→\s*Munich, Germany/)
      expect(toDo).toContain('Air')
    })

    it('links the customer company to the delivery detail (S-9 AC 11)', async () => {
      vi.mocked(client.getDelivery).mockResolvedValue(deliveryDetail({ id: 51 }))

      const { wrapper, router } = await renderApp('/')
      await link(card(wrapper, 'On the way', 'Example Interiors'), 'Example Interiors').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/51'))
      expect(client.getDelivery).toHaveBeenCalledWith(51)
    })

    it('shows "Expected <date>" on an On the way card with an expected arrival (S-10 AC 9)', async () => {
      const { wrapper } = await renderApp('/')

      const onTheWay = card(wrapper, 'On the way', 'Example Interiors').text()
      expect(onTheWay).toMatch(/Expected\s+28 Nov 2026/)
      expect(onTheWay).not.toContain('not set')
      expect(onTheWay).not.toMatch(/Arriv/)
    })

    it('shows "Expected: not set" on an On the way card without an expected arrival (S-10 AC 9)', async () => {
      getBoard.mockResolvedValue([
        { ...valmora, expected_arrival_date: null },
        interiorsOnTheWay,
        sunGarden,
        { ...kesslerDelivered, expected_arrival_date: null },
      ])

      const { wrapper } = await renderApp('/')

      const withoutExpected = card(wrapper, 'On the way', 'Valmora Ceramics').text()
      expect(withoutExpected).toContain('Expected: not set')
      expect(withoutExpected).not.toMatch(/Arriv/)
      expect(card(wrapper, 'On the way', 'Example Interiors').text()).not.toContain('not set')
      expect(card(wrapper, 'To do', 'Sun Garden Furniture').text()).not.toContain('not set')
      expect(card(wrapper, 'Delivered', 'Kessler Bikes GmbH').text()).not.toContain('not set')
    })

    it('shows "Arrived <date>" on a Delivered card with the real arrival, not the expected one (S-10 AC 10)', async () => {
      getBoard.mockResolvedValue([{ ...kesslerDelivered, expected_arrival_date: null }])

      const { wrapper } = await renderApp('/')

      const delivered = card(wrapper, 'Delivered', 'Kessler Bikes GmbH').text()
      expect(delivered).toMatch(/Arrived\s+02 Nov 2026/)
      expect(delivered).not.toMatch(/Expected/)
      expect(delivered).not.toMatch(/late|early|on time/)
    })

    for (const [expected, arrival, difference] of [
      ['2026-10-24', '2026-10-29', '5 days late'],
      ['2026-10-28', '2026-10-29', '1 day late'],
      ['2026-10-28', '2026-10-28', 'on time'],
      ['2026-10-29', '2026-10-27', '2 days early'],
    ] as const) {
      it(`follows "Arrived <date>" with "${difference}" on a Delivered card with both arrivals (S-10 AC 10)`, async () => {
        getBoard.mockResolvedValue([
          { ...kesslerDelivered, expected_arrival_date: expected, arrival_date: arrival },
        ])

        const { wrapper } = await renderApp('/')

        const delivered = card(wrapper, 'Delivered', 'Kessler Bikes GmbH').text()
        expect(delivered).toMatch(new RegExp(`Arrived\\s+${arrival.slice(8)} Oct 2026\\W+${difference}`))
        expect(delivered).not.toMatch(/Expected/)
      })
    }

    it('shows "Expected <date>" at To do, Offers received and Offer accepted when it is set (S-10 AC 11)', async () => {
      getBoard.mockResolvedValue([
        { ...sunGarden, expected_arrival_date: '2026-12-14' },
        { ...nordlicht, expected_arrival_date: '2026-12-18' },
        { ...interiorsAccepted, expected_arrival_date: '2026-11-20' },
      ])

      const { wrapper } = await renderApp('/')

      expect(card(wrapper, 'To do', 'Sun Garden Furniture').text()).toMatch(/Expected\s+14 Dec 2026/)
      expect(card(wrapper, 'Offers received', 'Nordlicht Lampen AG').text()).toMatch(/Expected\s+18 Dec 2026/)
      expect(card(wrapper, 'Offer accepted', 'Example Interiors').text()).toMatch(/Expected\s+20 Nov 2026/)
    })

    it('leaves out the dates that are not set, with no "Expected: not set" before On the way (S-9 AC 11, S-10 AC 11)', async () => {
      const { wrapper } = await renderApp('/')

      const toDo = card(wrapper, 'To do', 'Sun Garden Furniture').text()
      expect(toDo).not.toMatch(/\d{2} \w{3} \d{4}/)
      expect(toDo).not.toMatch(/shipp|arriv|expected|—|null|undefined/i)

      const offers = card(wrapper, 'Offers received', 'Nordlicht Lampen AG').text()
      expect(offers).not.toMatch(/shipp|arriv|expected|—|null|undefined/i)

      const accepted = card(wrapper, 'Offer accepted', 'Example Interiors').text()
      expect(accepted).not.toMatch(/arriv|expected|—|null|undefined/i)
    })

    it('shows the shipping date whenever it is set, at every status (S-10 AC 12)', async () => {
      const { wrapper } = await renderApp('/')

      expect(card(wrapper, 'To do', 'Kessler Bikes GmbH').text()).toContain('18 Nov 2026')
      expect(card(wrapper, 'Offer accepted', 'Example Interiors').text()).toContain('12 Nov 2026')
      expect(card(wrapper, 'On the way', 'Example Interiors').text()).toContain('20 Oct 2026')
      expect(card(wrapper, 'On the way', 'Valmora Ceramics').text()).toContain('27 Oct 2026')
      expect(card(wrapper, 'Delivered', 'Kessler Bikes GmbH').text()).toContain('28 Oct 2026')
    })

    it('shows the offer price when it is set (S-9 AC 11)', async () => {
      const { wrapper } = await renderApp('/')

      expect(card(wrapper, 'On the way', 'Example Interiors').text()).toContain('€3,900.00')
      expect(card(wrapper, 'Offer accepted', 'Example Interiors').text()).toContain('€2,480.00')
      expect(card(wrapper, 'Delivered', 'Kessler Bikes GmbH').text()).toContain('€6,150.00')
      expect(card(wrapper, 'To do', 'Sun Garden Furniture').text()).not.toContain('€')
    })

    it('hints at a missing offer price at Offer accepted, On the way and Delivered only (S-9 AC 12)', async () => {
      getBoard.mockResolvedValue([
        ...board,
        boardCard({ id: 68, status: 'DELIVERED', customer_company: 'Valmora Ceramics', offer_price_eur: null }),
      ])

      const { wrapper } = await renderApp('/')

      expect(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd').text()).toContain('No offer price')
      expect(card(wrapper, 'On the way', 'Valmora Ceramics').text()).toContain('No offer price')
      expect(card(wrapper, 'Delivered', 'Valmora Ceramics').text()).toContain('No offer price')

      expect(card(wrapper, 'To do', 'Sun Garden Furniture').text()).not.toContain('No offer price')
      expect(card(wrapper, 'Offers received', 'Nordlicht Lampen AG').text()).not.toContain('No offer price')
      expect(card(wrapper, 'Offer accepted', 'Example Interiors').text()).not.toContain('No offer price')
    })
  })

  describe('overdue warning', () => {
    function onTheWay(id: number, company: string, expected: string | null, daysLate: number | null): BoardCard {
      return boardCard({ id, customer_company: company, expected_arrival_date: expected, days_late: daysLate })
    }

    // "Today" on the server is 31 Oct 2026; days_late is what it computed for each card.
    const notDue = interiorsOnTheWay
    const dueToday = onTheWay(71, 'Oak & Iron Ltd', '2026-10-31', null)
    const noExpected = onTheWay(72, 'Vela Interiors Srl', null, null)
    const late1 = onTheWay(73, 'Tessera Design BV', '2026-10-30', 1)
    const late3 = onTheWay(74, 'Hofmann Leuchten AG', '2026-10-28', 3)
    const late4 = onTheWay(75, 'Polar Home Oy', '2026-10-27', 4)
    const late7 = onTheWay(76, 'Casa Nova SL', '2026-10-24', 7)
    const late8 = onTheWay(77, 'Meridian Tools GmbH', '2026-10-23', 8)
    const late15 = { ...onTheWay(78, 'Baltic Living UAB', '2026-10-16', 15), offer_price_eur: null }
    const deliveredLate = { ...kesslerDelivered, expected_arrival_date: '2026-10-24', arrival_date: '2026-10-29' }
    const toDoPast = { ...sunGarden, expected_arrival_date: '2026-10-01' }
    const acceptedPast = { ...interiorsAccepted, expected_arrival_date: '2026-10-10' }

    const overdueBoard = [
      notDue,
      dueToday,
      noExpected,
      late1,
      late3,
      late4,
      late7,
      late8,
      late15,
      deliveredLate,
      toDoPast,
      acceptedPast,
    ]

    // jsdom applies no component styles, so a card's colour is read from what styles it: its
    // classes and inline style. A level is what an overdue card carries beyond a card not due yet.
    function looks(target: DOMWrapper<Element>): Set<string> {
      const element = target.element as HTMLElement
      const style = Array.from({ length: element.style.length }, (_, i) => element.style[i])
      return new Set([
        ...element.classList,
        ...style.map((property) => `${property}:${element.style.getPropertyValue(property)}`),
      ])
    }

    function levelOf(target: DOMWrapper<Element>, baseline: DOMWrapper<Element>): string[] {
      const plain = looks(baseline)
      return [...looks(target)].filter((trait) => !plain.has(trait)).sort()
    }

    function onTheWayCard(wrapper: VueWrapper, company: string): DOMWrapper<Element> {
      return card(wrapper, 'On the way', company)
    }

    beforeEach(() => {
      getBoard.mockResolvedValue(overdueBoard)
    })

    it('shows "<n> days late" on an overdue On the way card, "1 day late" for one (S-10 AC 13, AC 15)', async () => {
      const { wrapper } = await renderApp('/')

      for (const [company, text] of [
        ['Tessera Design BV', '1 day late'],
        ['Hofmann Leuchten AG', '3 days late'],
        ['Polar Home Oy', '4 days late'],
        ['Casa Nova SL', '7 days late'],
        ['Meridian Tools GmbH', '8 days late'],
        ['Baltic Living UAB', '15 days late'],
      ]) {
        expect(onTheWayCard(wrapper, company).text(), company).toContain(text)
      }
      expect(onTheWayCard(wrapper, 'Tessera Design BV').text()).not.toContain('1 days late')
    })

    it('puts the days late in text a screen reader reads, not only in colour (S-10 AC 15)', async () => {
      const { wrapper } = await renderApp('/')

      const late = onTheWayCard(wrapper, 'Polar Home Oy')
      const hidden = late.findAll('[aria-hidden="true"]').map((element) => element.text())
      expect(late.text()).toContain('4 days late')
      expect(hidden.join(' ')).not.toContain('late')
      expect(late.attributes('aria-hidden')).toBeUndefined()
    })

    it('colours an overdue card by level: 1 to 3, 4 to 7, 8 and more days late (S-10 AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      const plain = onTheWayCard(wrapper, 'Example Interiors')
      const level = (company: string) => levelOf(onTheWayCard(wrapper, company), plain)

      const yellow = level('Tessera Design BV')
      const orange = level('Polar Home Oy')
      const red = level('Meridian Tools GmbH')
      expect(yellow, '1 day late').not.toEqual([])
      expect(orange, '4 days late').not.toEqual([])
      expect(red, '8 days late').not.toEqual([])
      expect(level('Hofmann Leuchten AG'), '3 days late').toEqual(yellow)
      expect(level('Casa Nova SL'), '7 days late').toEqual(orange)
      expect(level('Baltic Living UAB'), '15 days late').toEqual(red)
      expect(orange).not.toEqual(yellow)
      expect(red).not.toEqual(yellow)
      expect(red).not.toEqual(orange)
    })

    it('does not mark a card on its arrival day, before it or without an expected arrival (S-10 AC 16)', async () => {
      const { wrapper } = await renderApp('/')
      const plain = onTheWayCard(wrapper, 'Example Interiors')
      const levelTraits = new Set(
        ['Tessera Design BV', 'Polar Home Oy', 'Meridian Tools GmbH'].flatMap((company) =>
          levelOf(onTheWayCard(wrapper, company), plain),
        ),
      )
      expect([...levelTraits]).not.toEqual([])

      for (const company of ['Example Interiors', 'Oak & Iron Ltd', 'Vela Interiors Srl']) {
        const unmarked = onTheWayCard(wrapper, company)
        expect(unmarked.text(), company).not.toMatch(/late/)
        expect([...looks(unmarked)].filter((trait) => levelTraits.has(trait)), company).toEqual([])
      }
      expect(onTheWayCard(wrapper, 'Oak & Iron Ltd').text()).toMatch(/Expected\s+31 Oct 2026/)
    })

    it('does not mark a card at any other status, even past its expected arrival (S-10 AC 16)', async () => {
      const { wrapper } = await renderApp('/')
      const plain = onTheWayCard(wrapper, 'Example Interiors')
      const levelTraits = new Set(
        ['Tessera Design BV', 'Polar Home Oy', 'Meridian Tools GmbH'].flatMap((company) =>
          levelOf(onTheWayCard(wrapper, company), plain),
        ),
      )
      expect([...levelTraits]).not.toEqual([])

      const others = [
        card(wrapper, 'To do', 'Sun Garden Furniture'),
        card(wrapper, 'Offer accepted', 'Example Interiors'),
        card(wrapper, 'Delivered', 'Kessler Bikes GmbH'),
      ]
      for (const other of others) {
        expect([...looks(other)].filter((trait) => levelTraits.has(trait))).toEqual([])
      }
      expect(others[0].text()).not.toMatch(/late/)
      expect(others[1].text()).not.toMatch(/late/)
    })

    it('takes the days late from the board, not from the browser\'s clock (S-10 AC 13)', async () => {
      vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 10, 20, 10, 0) })
      try {
        const { wrapper } = await renderApp('/')

        expect(onTheWayCard(wrapper, 'Hofmann Leuchten AG').text()).toContain('3 days late')
        expect(onTheWayCard(wrapper, 'Hofmann Leuchten AG').text()).not.toContain('23 days late')
        expect(onTheWayCard(wrapper, 'Oak & Iron Ltd').text()).not.toMatch(/late/)
      } finally {
        vi.useRealTimers()
      }
    })
  })

  describe('loading, error and empty', () => {
    it('shows a loading message until the board arrives (S-9 AC 8)', async () => {
      getBoard.mockReturnValue(new Promise<BoardCard[]>(() => {}))

      const { wrapper } = await renderApp('/')

      expect(wrapper.text()).toContain('Loading the board…')
      expect(wrapper.findAll('article')).toHaveLength(0)
    })

    it('shows the API error instead of empty columns (S-9 AC 8)', async () => {
      getBoard.mockRejectedValue(
        new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }),
      )

      const { wrapper } = await renderApp('/')

      const alert = wrapper.find('[role="alert"]')
      expect(alert.text()).toContain('The server could not handle the request (HTTP 500).')
      expect(alert.text()).toContain('Database unavailable')
      expect(wrapper.findAll('article')).toHaveLength(0)
      expect(wrapper.text()).not.toContain('No running deliveries')
    })

    it('loads the board again when Alice retries after an error (S-9 AC 8)', async () => {
      getBoard
        .mockRejectedValueOnce(new ApiError(503, 'unavailable', { error: 'unavailable' }))
        .mockResolvedValueOnce(board)

      const { wrapper } = await renderApp('/')
      await button(wrapper, 'Try again').trigger('click')

      await vi.waitFor(() => expect(wrapper.findAll('article')).toHaveLength(board.length))
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    })

    it('keeps every column when nothing is running, each saying it is empty (S-9 AC 8)', async () => {
      getBoard.mockResolvedValue([])

      const { wrapper } = await renderApp('/')

      expect(columnNames(wrapper)).toEqual(STATUS_COLUMNS)
      for (const name of STATUS_COLUMNS) {
        expect(section(wrapper, name).text(), name).toContain('No deliveries')
      }
      expect(wrapper.text()).toContain('No running deliveries')
    })
  })

  describe('moving a card', () => {
    it('offers every other status in the "Move to" menu (S-9 AC 13)', async () => {
      const { wrapper } = await renderApp('/')

      const target = card(wrapper, 'Offers received', 'Nordlicht Lampen AG')
      await openMoveMenu(target)

      expect(menuEntries(target)).toEqual(['To do', 'Offer accepted', 'On the way', 'Delivered'])
    })

    it('moves a card to the status picked in its "Move to" menu (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      expect(moveDelivery).toHaveBeenCalledTimes(1)
      expect(lastMove()).toMatchObject({ id: 63, status: 'OFFER_ACCEPTED' })
      expect(openDialog()).toBeNull()
      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Nordlicht Lampen AG'),
      )
      expect(companiesIn(wrapper, 'Offers received')).toEqual([])
    })

    it('moves a card by dragging it onto a column, as the menu does (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/')
      await dragOnto(wrapper, card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      expect(moveDelivery).toHaveBeenCalledTimes(1)
      expect(lastMove()).toMatchObject({ id: 63, status: 'OFFER_ACCEPTED' })
      expect(openDialog()).toBeNull()
      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Nordlicht Lampen AG'),
      )
      expect(companiesIn(wrapper, 'Offers received')).toEqual([])
    })

    it('moves a card backwards (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...interiorsAccepted, status: 'TO_DO' })
      getBoard.mockResolvedValueOnce(board).mockResolvedValue(afterMove(interiorsAccepted, { status: 'TO_DO' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Example Interiors'), 'To do')

      expect(lastMove()).toMatchObject({ id: 64, status: 'TO_DO' })
      await vi.waitFor(() => expect(companiesIn(wrapper, 'To do')[0]).toBe('Example Interiors'))
      expect(companiesIn(wrapper, 'Offer accepted')).toEqual(['Brightwell Toys Ltd'])
    })

    it('moves a card past the next column (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...sunGarden, status: 'OFFER_ACCEPTED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(sunGarden, { status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/')
      await dragOnto(wrapper, card(wrapper, 'To do', 'Sun Garden Furniture'), 'Offer accepted')

      expect(lastMove()).toMatchObject({ id: 61, status: 'OFFER_ACCEPTED' })
      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Sun Garden Furniture'),
      )
    })

    it('moves to On the way at once when the shipping date is set, without an expected arrival (S-10 AC 4)', async () => {
      getBoard.mockResolvedValue([interiorsAccepted])
      moveDelivery.mockResolvedValue({ ...interiorsAccepted, status: 'ON_THE_WAY' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Example Interiors'), 'On the way')

      expect(openDialog()).toBeNull()
      expect(moveDelivery).toHaveBeenCalledTimes(1)
      expect(lastMove()).toMatchObject({ id: 64, status: 'ON_THE_WAY' })
    })

    it('moves to On the way at once when the shipping date and the expected arrival are set (S-10 AC 4)', async () => {
      const withExpected = { ...interiorsAccepted, expected_arrival_date: '2026-12-15' }
      getBoard.mockResolvedValue([withExpected])
      moveDelivery.mockResolvedValue({ ...withExpected, status: 'ON_THE_WAY' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Example Interiors'), 'On the way')

      expect(openDialog()).toBeNull()
      expect(lastMove()).toMatchObject({ id: 64, status: 'ON_THE_WAY' })
    })

    it('moves to On the way at once by dragging, too, when the shipping date is set (S-10 AC 4, AC 6)', async () => {
      getBoard.mockResolvedValue([interiorsAccepted])
      moveDelivery.mockResolvedValue({ ...interiorsAccepted, status: 'ON_THE_WAY' })

      const { wrapper } = await renderApp('/')
      await dragOnto(wrapper, card(wrapper, 'Offer accepted', 'Example Interiors'), 'On the way')

      expect(openDialog()).toBeNull()
      expect(lastMove()).toMatchObject({ id: 64, status: 'ON_THE_WAY' })
    })

    it('marks with "…" only the "Move to" entries that open the date dialog (S-10 AC 6)', async () => {
      const withoutShipping = { ...brightwell, expected_arrival_date: '2026-11-20' }
      getBoard.mockResolvedValue([withoutShipping, interiorsAccepted])

      const { wrapper } = await renderApp('/')
      const rawEntries = (target: DOMWrapper<Element>) =>
        target.findAll('[role="menuitem"]').map((entry) => entry.text())

      const noShipping = card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd')
      await openMoveMenu(noShipping)
      expect(rawEntries(noShipping)).toEqual(['To do', 'Offers received', 'On the way…', 'Delivered…'])
      await press('Escape')

      const shipped = card(wrapper, 'Offer accepted', 'Example Interiors')
      await openMoveMenu(shipped)
      expect(rawEntries(shipped)).toEqual(['To do', 'Offers received', 'On the way', 'Delivered…'])
    })

    it('never clears a date that is set when a card moves (S-9 AC 16, S-10 AC 2)', async () => {
      const allDates = { ...interiorsOnTheWay, arrival_date: '2026-11-30' }
      getBoard.mockResolvedValue([allDates])
      moveDelivery.mockResolvedValue({ ...allDates, status: 'OFFERS_RECEIVED' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Offers received')

      const move = lastMove()
      expect(move).toMatchObject({ id: 51, status: 'OFFERS_RECEIVED' })
      expect([undefined, null, '2026-10-20']).toContain(move.shipping_date)
      expect([undefined, null, '2026-11-28']).toContain(move.expected_arrival_date)
      expect([undefined, null, '2026-11-30']).toContain(move.arrival_date)
    })

    it('shows the card as moving until the move is saved (S-9 AC 13)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      expect(wrapper.text()).toContain('Moving…')

      getBoard.mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))
      move.resolve({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      await flushPromises()

      await vi.waitFor(() => expect(wrapper.text()).not.toContain('Moving…'))
    })

    it('does not move a card again while it is moving, by dragging it onto another column (S-9 AC 13)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')
      expect(card(wrapper, 'Offers received', 'Nordlicht Lampen AG').text()).toContain('Moving…')

      await dragOnto(wrapper, card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'To do')
      await dragOnto(wrapper, card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Delivered')

      expect(moveDelivery).toHaveBeenCalledTimes(1)
      expect(lastMove()).toMatchObject({ id: 63, status: 'OFFER_ACCEPTED' })
      expect(openDialog()).toBeNull()
      expect(companiesIn(wrapper, 'To do')).not.toContain('Nordlicht Lampen AG')

      getBoard.mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))
      move.resolve({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      await flushPromises()

      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Nordlicht Lampen AG'),
      )
      expect(moveDelivery).toHaveBeenCalledTimes(1)
    })

    it('says which card could not be moved and leaves it in its column (S-9 AC 13)', async () => {
      moveDelivery.mockRejectedValue(new ApiError(500, 'internal', { error: 'internal' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))
      const alert = wrapper.find('[role="alert"]').text()
      expect(alert).toContain('Nordlicht Lampen AG could not be moved')
      expect(companiesIn(wrapper, 'Offers received')).toEqual(['Nordlicht Lampen AG'])
      expect(companiesIn(wrapper, 'Offer accepted')).not.toContain('Nordlicht Lampen AG')
    })
  })

  describe('moving a card with the keyboard', () => {
    it('moves the focus through the "Move to" menu with the arrow keys and moves to the focused status (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/')
      const target = card(wrapper, 'Offers received', 'Nordlicht Lampen AG')
      await openMoveMenu(target)
      expect(focused().text()).toBe('To do')

      await press('ArrowDown')
      expect(focused().text()).toBe('Offer accepted')
      await press('ArrowDown')
      expect(focused().text().replace(/…$/, '')).toBe('On the way')
      await press('ArrowUp')
      expect(focused().text()).toBe('Offer accepted')
      expect(moveDelivery).not.toHaveBeenCalled()

      await focused().trigger('click')
      await flushPromises()

      expect(lastMove()).toMatchObject({ id: 63, status: 'OFFER_ACCEPTED' })
      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Nordlicht Lampen AG'),
      )
    })

    it('closes the "Move to" menu on Escape, back on its button, and moves nothing (S-9 AC 13)', async () => {
      const { wrapper } = await renderApp('/')
      const target = card(wrapper, 'Offers received', 'Nordlicht Lampen AG')
      await openMoveMenu(target)
      await press('ArrowDown')

      await press('Escape')

      expect(menuEntries(target)).toEqual([])
      expect(focused().text()).toMatch(/^Move to/)
      expect(focused().attributes('aria-expanded')).toBe('false')
      expect(openDialog()).toBeNull()
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'Offers received')).toEqual(['Nordlicht Lampen AG'])
    })

    it('cancels the date dialog for On the way on Escape, leaving card and delivery unchanged (S-9 AC 13, AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      control(dateDialog(), 'Shipping date').element.focus()

      await press('Escape')

      expect(openDialog()).toBeNull()
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'Offer accepted')).toContain('Brightwell Toys Ltd')
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Brightwell Toys Ltd')
      expect(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd').text()).not.toContain('10 Nov 2026')
    })

    it('cancels the date dialog for Delivered on Escape and changes nothing (S-9 AC 13, AC 15)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      control(dateDialog(), 'Arrival').element.focus()

      await press('Escape')

      expect(openDialog()).toBeNull()
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'On the way')).toContain('Example Interiors')
      expect(companiesIn(wrapper, 'Delivered')).not.toContain('Example Interiors')
    })

    it('opens the date dialog again after an Escape, empty, for the next move (S-9 AC 13, AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      control(dateDialog(), 'Shipping date').element.focus()
      await press('Escape')

      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      expect(control(dateDialog(), 'Shipping date').element.value).toBe('')
      expect(moveDelivery).not.toHaveBeenCalled()
    })

    it('keeps the date dialog open on Escape while the move is saving, and closes it once moved (S-9 AC 13, AC 15)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()
      control(dateDialog(), 'Arrival').element.focus()

      await press('Escape')

      expect(dateDialog().text()).toContain('Move to Delivered')
      expect(moveDelivery).toHaveBeenCalledTimes(1)

      getBoard.mockResolvedValue(afterMove(interiorsOnTheWay, { status: 'DELIVERED' }))
      move.resolve({ ...interiorsOnTheWay, status: 'DELIVERED' })
      await flushPromises()

      await vi.waitFor(() => expect(openDialog()).toBeNull())
      await vi.waitFor(() => expect(companiesIn(wrapper, 'Delivered')[0]).toBe('Example Interiors'))
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Example Interiors')
    })

    it('shows the error of a move still saving when Escape was pressed, and leaves the card (S-9 AC 13, AC 14)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      await control(dateDialog(), 'Expected arrival').setValue('2026-12-22')
      await button(dateDialog(), 'Move to On the way').trigger('click')
      await flushPromises()
      control(dateDialog(), 'Shipping date').element.focus()

      await press('Escape')

      move.reject(new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }))
      await flushPromises()

      // In the dialog, kept open, or on the board, as a move from the menu reports it.
      const alerts = () => [...document.body.querySelectorAll('[role="alert"]')].map((a) => a.textContent)
      await vi.waitFor(() => expect(alerts().join(' ')).toContain('Database unavailable'))
      expect(companiesIn(wrapper, 'Offer accepted')).toContain('Brightwell Toys Ltd')
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Brightwell Toys Ltd')
    })

    it('puts the focus back on the card\'s "Move to" button after a move from the menu (S-9 AC 13)', async () => {
      moveDelivery.mockResolvedValue({ ...nordlicht, status: 'OFFER_ACCEPTED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(nordlicht, { status: 'OFFER_ACCEPTED' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      await vi.waitFor(() =>
        expect(companiesIn(wrapper, 'Offer accepted')[0]).toBe('Nordlicht Lampen AG'),
      )
      await vi.waitFor(() =>
        expectFocusOnMoveButton(card(wrapper, 'Offer accepted', 'Nordlicht Lampen AG')),
      )
    })

    it('puts the focus back on the card\'s "Move to" button after a move from the menu fails (S-9 AC 13)', async () => {
      moveDelivery.mockRejectedValue(new ApiError(500, 'internal', { error: 'internal' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offers received', 'Nordlicht Lampen AG'), 'Offer accepted')

      await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))
      await vi.waitFor(() =>
        expectFocusOnMoveButton(card(wrapper, 'Offers received', 'Nordlicht Lampen AG')),
      )
    })

    it('puts the focus back on the card\'s "Move to" button when the date dialog is cancelled (S-9 AC 13, AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      await button(dateDialog(), 'Cancel').trigger('click')
      await flushPromises()

      expect(openDialog()).toBeNull()
      await vi.waitFor(() =>
        expectFocusOnMoveButton(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd')),
      )
    })

    it('puts the focus back on the card\'s "Move to" button when the date dialog is closed with Escape (S-9 AC 13, AC 15)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      control(dateDialog(), 'Arrival').element.focus()

      await press('Escape')

      expect(openDialog()).toBeNull()
      await vi.waitFor(() => expectFocusOnMoveButton(card(wrapper, 'On the way', 'Example Interiors')))
    })

    it('puts the focus on the moved card\'s "Move to" button after the date dialog saves the move (S-9 AC 13, AC 15)', async () => {
      moveDelivery.mockResolvedValue({ ...interiorsOnTheWay, status: 'DELIVERED' })
      getBoard
        .mockResolvedValueOnce(board)
        .mockResolvedValue(afterMove(interiorsOnTheWay, { status: 'DELIVERED' }))

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()

      await vi.waitFor(() => expect(openDialog()).toBeNull())
      await vi.waitFor(() => expectFocusOnMoveButton(card(wrapper, 'Delivered', 'Example Interiors')))
    })
  })

  describe('date dialog', () => {
    it('asks for the shipping date and offers the expected arrival when a card without a shipping date moves to On the way (S-10 AC 4)', async () => {
      moveDelivery.mockResolvedValue({
        ...brightwell,
        status: 'ON_THE_WAY',
        shipping_date: '2026-11-10',
        expected_arrival_date: '2026-12-22',
      })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to On the way')
      expect(control(dialog, 'Shipping date').element.value).toBe('')
      expect(control(dialog, 'Expected arrival').element.value).toBe('')
      expect(hasControl(dialog, 'Arrival')).toBe(false)
      expect(moveDelivery).not.toHaveBeenCalled()

      await control(dialog, 'Shipping date').setValue('2026-11-10')
      await control(dialog, 'Expected arrival').setValue('2026-12-22')
      await button(dialog, 'Move to On the way').trigger('click')
      await flushPromises()

      expect(moveDelivery).toHaveBeenCalledTimes(1)
      const move = lastMove()
      expect(move).toMatchObject({
        id: 65,
        status: 'ON_THE_WAY',
        shipping_date: '2026-11-10',
        expected_arrival_date: '2026-12-22',
      })
      expect([undefined, null]).toContain(move.arrival_date)
      await vi.waitFor(() => expect(openDialog()).toBeNull())
    })

    it('moves to On the way with the shipping date only, the expected arrival left empty (S-10 AC 4)', async () => {
      moveDelivery.mockResolvedValue({ ...brightwell, status: 'ON_THE_WAY', shipping_date: '2026-11-10' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      await button(dateDialog(), 'Move to On the way').trigger('click')
      await flushPromises()

      expect(moveDelivery).toHaveBeenCalledTimes(1)
      const move = lastMove()
      expect(move).toMatchObject({ id: 65, status: 'ON_THE_WAY', shipping_date: '2026-11-10' })
      expect([undefined, null]).toContain(move.expected_arrival_date)
      expect([undefined, null]).toContain(move.arrival_date)
      await vi.waitFor(() => expect(openDialog()).toBeNull())
    })

    it('fills in the expected arrival in the On the way dialog when it is set (S-10 AC 4)', async () => {
      const expectedOnly = { ...brightwell, expected_arrival_date: '2026-12-22' }
      getBoard.mockResolvedValue([expectedOnly])
      moveDelivery.mockResolvedValue({ ...expectedOnly, status: 'ON_THE_WAY', shipping_date: '2026-11-10' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      const dialog = dateDialog()
      expect(control(dialog, 'Shipping date').element.value).toBe('')
      expect(control(dialog, 'Expected arrival').element.value).toBe('2026-12-22')

      await control(dialog, 'Shipping date').setValue('2026-11-10')
      await button(dialog, 'Move to On the way').trigger('click')
      await flushPromises()

      const move = lastMove()
      expect(move).toMatchObject({ id: 65, status: 'ON_THE_WAY', shipping_date: '2026-11-10' })
      expect([undefined, null, '2026-12-22']).toContain(move.expected_arrival_date)
    })

    it('sends an expected arrival Alice corrects in the On the way dialog (S-10 AC 4)', async () => {
      const expectedOnly = { ...brightwell, expected_arrival_date: '2026-12-22' }
      getBoard.mockResolvedValue([expectedOnly])
      moveDelivery.mockResolvedValue({ ...expectedOnly, status: 'ON_THE_WAY', shipping_date: '2026-11-10' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      await control(dateDialog(), 'Expected arrival').setValue('2027-01-05')
      await button(dateDialog(), 'Move to On the way').trigger('click')
      await flushPromises()

      expect(lastMove()).toMatchObject({
        id: 65,
        status: 'ON_THE_WAY',
        shipping_date: '2026-11-10',
        expected_arrival_date: '2027-01-05',
      })
    })

    it('opens the same dialog when the card is dragged onto On the way (S-9 AC 13, S-10 AC 4, AC 6)', async () => {
      const { wrapper } = await renderApp('/')
      await dragOnto(wrapper, card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to On the way')
      expect(hasControl(dialog, 'Shipping date')).toBe(true)
      expect(hasControl(dialog, 'Expected arrival')).toBe(true)
      expect(moveDelivery).not.toHaveBeenCalled()
    })

    it('leaves the card and the delivery unchanged when Alice cancels (S-9 AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')

      await button(dateDialog(), 'Cancel').trigger('click')
      await flushPromises()

      expect(openDialog()).toBeNull()
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'Offer accepted')).toContain('Brightwell Toys Ltd')
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Brightwell Toys Ltd')
      expect(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd').text()).not.toContain('10 Nov 2026')
    })

    it('names the missing shipping date on confirm, never the expected arrival, and moves nothing (S-10 AC 2, AC 4)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      const dialog = dateDialog()
      await control(dialog, 'Expected arrival').setValue('2026-12-22')

      await button(dialog, 'Move to On the way').trigger('click')
      await flushPromises()

      expect(moveDelivery).not.toHaveBeenCalled()
      expect(fieldOf(dateDialog(), 'Shipping date').text()).toContain('Shipping date is required at On the way.')
      expect(fieldOf(dateDialog(), 'Expected arrival').text()).not.toMatch(/required/i)
    })

    it('asks for the real arrival on a move to Delivered, filled in with the expected arrival (S-10 AC 5)', async () => {
      moveDelivery.mockResolvedValue({ ...interiorsOnTheWay, status: 'DELIVERED', arrival_date: '2026-11-30' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to Delivered')
      expect(hasControl(dialog, 'Shipping date')).toBe(false)
      expect(hasControl(dialog, 'Expected arrival')).toBe(false)
      expect(control(dialog, 'Arrival').element.value).toBe('2026-11-28')
      expect(fieldOf(dialog, 'Arrival').text()).toContain('Filled in with the expected arrival.')
      expect(moveDelivery).not.toHaveBeenCalled()

      await control(dialog, 'Arrival').setValue('2026-11-30')
      await button(dialog, 'Move to Delivered').trigger('click')
      await flushPromises()

      const move = lastMove()
      expect(move).toMatchObject({ id: 51, status: 'DELIVERED', arrival_date: '2026-11-30' })
      expect([undefined, null, '2026-10-20']).toContain(move.shipping_date)
      expect([undefined, null, '2026-11-28']).toContain(move.expected_arrival_date)
    })

    it('sends the expected arrival as the real one when Alice confirms it unchanged (S-10 AC 5)', async () => {
      moveDelivery.mockResolvedValue({ ...interiorsOnTheWay, status: 'DELIVERED', arrival_date: '2026-11-28' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()

      const move = lastMove()
      expect(move).toMatchObject({ id: 51, status: 'DELIVERED', arrival_date: '2026-11-28' })
      expect([undefined, null, '2026-11-28']).toContain(move.expected_arrival_date)
    })

    it('fills in the real arrival when it is set, before the expected one, on a move to Delivered (S-10 AC 5)', async () => {
      const arrivedBefore = { ...interiorsOnTheWay, expected_arrival_date: '2026-10-24', arrival_date: '2026-10-29' }
      getBoard.mockResolvedValue([arrivedBefore])
      moveDelivery.mockResolvedValue({ ...arrivedBefore, status: 'DELIVERED' })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')

      const dialog = dateDialog()
      expect(control(dialog, 'Arrival').element.value).toBe('2026-10-29')
      expect(fieldOf(dialog, 'Arrival').text()).toContain('Filled in with the arrival already recorded.')

      await button(dialog, 'Move to Delivered').trigger('click')
      await flushPromises()

      const move = lastMove()
      expect(move).toMatchObject({ id: 51, status: 'DELIVERED' })
      expect([undefined, null, '2026-10-29']).toContain(move.arrival_date)
      expect([undefined, null, '2026-10-24']).toContain(move.expected_arrival_date)
    })

    it('opens the Delivered dialog when the card is dragged onto Delivered (S-9 AC 13, S-10 AC 5, AC 6)', async () => {
      const { wrapper } = await renderApp('/')
      await dragOnto(wrapper, card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to Delivered')
      expect(control(dialog, 'Arrival').element.value).toBe('2026-11-28')
      expect(moveDelivery).not.toHaveBeenCalled()
    })

    it('asks for the shipping date too when it is missing on a move to Delivered (S-10 AC 5)', async () => {
      const expectedOnly = { ...sunGarden, expected_arrival_date: '2026-11-03' }
      getBoard.mockResolvedValue([expectedOnly])
      moveDelivery.mockResolvedValue({
        ...expectedOnly,
        status: 'DELIVERED',
        shipping_date: '2026-10-29',
        arrival_date: '2026-11-03',
      })

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'To do', 'Sun Garden Furniture'), 'Delivered')

      const dialog = dateDialog()
      expect(control(dialog, 'Shipping date').element.value).toBe('')
      expect(control(dialog, 'Arrival').element.value).toBe('2026-11-03')

      await control(dialog, 'Shipping date').setValue('2026-10-29')
      await button(dialog, 'Move to Delivered').trigger('click')
      await flushPromises()

      const move = lastMove()
      expect(move).toMatchObject({
        id: 61,
        status: 'DELIVERED',
        shipping_date: '2026-10-29',
        arrival_date: '2026-11-03',
      })
      expect([undefined, null, '2026-11-03']).toContain(move.expected_arrival_date)
    })

    it('asks for both dates, empty, when neither arrival nor shipping date is set on a move to Delivered (S-10 AC 5)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'To do', 'Sun Garden Furniture'), 'Delivered')

      const dialog = dateDialog()
      expect(control(dialog, 'Shipping date').element.value).toBe('')
      expect(control(dialog, 'Arrival').element.value).toBe('')
    })

    it('names both missing dates on confirm of an empty Delivered dialog and moves nothing (S-10 AC 2, AC 5)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'To do', 'Sun Garden Furniture'), 'Delivered')

      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()

      expect(moveDelivery).not.toHaveBeenCalled()
      expect(fieldOf(dateDialog(), 'Shipping date').text()).toContain('Shipping date is required at Delivered.')
      expect(fieldOf(dateDialog(), 'Arrival').text()).toContain('Arrival is required at Delivered.')
    })

    it('changes nothing when Alice cancels a move to Delivered (S-9 AC 15)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')

      await button(dateDialog(), 'Cancel').trigger('click')
      await flushPromises()

      expect(openDialog()).toBeNull()
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'On the way')).toContain('Example Interiors')
      expect(companiesIn(wrapper, 'Delivered')).not.toContain('Example Interiors')
    })

    it('keeps the dialog open with the error when the move fails (S-9 AC 15)', async () => {
      moveDelivery.mockRejectedValue(
        new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }),
      )

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()

      const dialog = dateDialog()
      expect(dialog.find('[role="alert"]').text()).toContain('Database unavailable')
      expect(control(dialog, 'Arrival').element.value).toBe('2026-11-28')
      expect(companiesIn(wrapper, 'On the way')).toContain('Example Interiors')
    })
  })

  describe('date dialog closed by the browser', () => {
    it('counts a native close as cancel and changes nothing (S-9 AC 13, AC 14)', async () => {
      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      await closeDialogNatively()

      expect(dialogInPage()).toBe(false)
      expect(moveDelivery).not.toHaveBeenCalled()
      expect(companiesIn(wrapper, 'Offer accepted')).toContain('Brightwell Toys Ltd')
      await vi.waitFor(() =>
        expectFocusOnMoveButton(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd')),
      )
    })

    it('takes the dialog off the page when the browser closes it while the move saves (S-9 AC 13, AC 15)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()

      await closeDialogNatively()

      expect(dialogInPage()).toBe(false)
      expect(moveDelivery).toHaveBeenCalledTimes(1)
    })

    it('moves the card when a save still running after a native close goes through (S-9 AC 13, AC 15)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()
      await closeDialogNatively()

      getBoard.mockResolvedValue(afterMove(interiorsOnTheWay, { status: 'DELIVERED' }))
      move.resolve({ ...interiorsOnTheWay, status: 'DELIVERED' })
      await flushPromises()

      await vi.waitFor(() => expect(companiesIn(wrapper, 'Delivered')[0]).toBe('Example Interiors'))
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Example Interiors')
      expect(dialogInPage()).toBe(false)
      expect(boardAlerts(wrapper)).toEqual([])
      await vi.waitFor(() => expectFocusOnMoveButton(card(wrapper, 'Delivered', 'Example Interiors')))
    })

    it('reports a save failing after a native close on the board, as a menu move does, and leaves the card (S-9 AC 13, AC 14)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValue(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      await control(dateDialog(), 'Expected arrival').setValue('2026-12-22')
      await button(dateDialog(), 'Move to On the way').trigger('click')
      await flushPromises()
      await closeDialogNatively()

      move.reject(new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }))
      await flushPromises()

      await vi.waitFor(() => expect(boardAlerts(wrapper).join(' ')).toContain('Database unavailable'))
      expect(boardAlerts(wrapper).join(' ')).toContain('Brightwell Toys Ltd could not be moved')
      expect(dialogInPage()).toBe(false)
      expect(companiesIn(wrapper, 'Offer accepted')).toContain('Brightwell Toys Ltd')
      expect(companiesIn(wrapper, 'On the way')).not.toContain('Brightwell Toys Ltd')
      await vi.waitFor(() =>
        expectFocusOnMoveButton(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd')),
      )
    })

    it('opens the date dialog for the next move after a failed save was closed natively (S-9 AC 13, AC 14)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValueOnce(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')
      await control(dateDialog(), 'Shipping date').setValue('2026-11-10')
      await control(dateDialog(), 'Expected arrival').setValue('2026-12-22')
      await button(dateDialog(), 'Move to On the way').trigger('click')
      await flushPromises()
      await closeDialogNatively()
      move.reject(new ApiError(500, 'internal', { error: 'internal', detail: 'Database unavailable' }))
      await flushPromises()
      await vi.waitFor(() => expect(boardAlerts(wrapper).join(' ')).toContain('Database unavailable'))

      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to Delivered')
      expect(control(dialog, 'Arrival').element.value).toBe('2026-11-28')
      expect(moveDelivery).toHaveBeenCalledTimes(1)
    })

    it('opens the date dialog for the next move after a successful save was closed natively (S-9 AC 13, AC 14)', async () => {
      const move = deferred<BoardCard>()
      moveDelivery.mockReturnValueOnce(move.promise)

      const { wrapper } = await renderApp('/')
      await moveWithMenu(card(wrapper, 'On the way', 'Example Interiors'), 'Delivered')
      await button(dateDialog(), 'Move to Delivered').trigger('click')
      await flushPromises()
      await closeDialogNatively()
      getBoard.mockResolvedValue(afterMove(interiorsOnTheWay, { status: 'DELIVERED' }))
      move.resolve({ ...interiorsOnTheWay, status: 'DELIVERED' })
      await flushPromises()
      await vi.waitFor(() => expect(companiesIn(wrapper, 'Delivered')[0]).toBe('Example Interiors'))

      await moveWithMenu(card(wrapper, 'Offer accepted', 'Brightwell Toys Ltd'), 'On the way')

      const dialog = dateDialog()
      expect(dialog.text()).toContain('Move to On the way')
      expect(control(dialog, 'Shipping date').element.value).toBe('')
      expect(moveDelivery).toHaveBeenCalledTimes(1)
    })
  })

  describe('New delivery', () => {
    it('opens the delivery form at To do from the board (S-9 AC 17)', async () => {
      const { wrapper, router } = await renderApp('/')

      await link(wrapper, 'New delivery').trigger('click')

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/deliveries/new'))
      await vi.waitFor(() => expect(wrapper.find('h1').text()).toBe('New delivery'))
      expect(control<HTMLSelectElement>(wrapper, 'Status').element.value).toBe('TO_DO')
    })
  })
})

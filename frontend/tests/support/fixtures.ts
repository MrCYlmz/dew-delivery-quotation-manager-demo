import type {
  BoardCard,
  DeliveryDetail,
  DeliveryItem,
  DeliveryListRow,
  DeliveryPdf,
  EstimateComparable,
  EstimateComparables,
  ParsedPackingList,
} from '../../src/api/types'

export function listRow(overrides: Partial<DeliveryListRow> = {}): DeliveryListRow {
  return {
    id: 1,
    customer_company: 'Example Interiors',
    origin_country: 'CN',
    origin_city: 'Ningbo',
    destination_country: 'DE',
    destination_city: 'Munich',
    transport_method: 'SEA',
    status: 'DELIVERED',
    shipping_date: '2026-01-15',
    arrival_date: '2026-03-02',
    cost_eur: 4210.0,
    volume_m3: 7.42,
    gross_weight_kg: 1580,
    pallet_count: 6,
    has_notes: true,
    pdf_count: 3,
    ...overrides,
  }
}

export function boardCard(overrides: Partial<BoardCard> = {}): BoardCard {
  return {
    id: 51,
    status: 'ON_THE_WAY',
    customer_company: 'Example Interiors',
    origin_country: 'CN',
    origin_city: 'Ningbo',
    destination_country: 'DE',
    destination_city: 'Munich',
    transport_method: 'SEA',
    shipping_date: '2026-10-20',
    expected_arrival_date: '2026-11-28',
    arrival_date: null,
    days_late: null,
    offer_price_eur: 3900.0,
    ...overrides,
  }
}

export function deliveryItem(overrides: Partial<DeliveryItem> = {}): DeliveryItem {
  return {
    source_row: 5,
    description: 'Shelving unit, oak',
    article_no: 'SH-1200-OAK',
    length_mm: 1200,
    width_mm: 800,
    height_mm: 1050,
    pallet_count: 2,
    volume_m3: 2.02,
    gross_weight_kg: 480,
    net_weight_kg: 395.5,
    volume_mismatch: false,
    ...overrides,
  }
}

export function deliveryPdf(overrides: Partial<DeliveryPdf> = {}): DeliveryPdf {
  return {
    id: 7,
    original_filename: 'QX20260115 delivery note.pdf',
    size_bytes: 284119,
    sort_order: 0,
    uploaded_at: '2026-01-16T09:12:00Z',
    ...overrides,
  }
}

export function deliveryDetail(overrides: Partial<DeliveryDetail> = {}): DeliveryDetail {
  return {
    ...listRow({ pdf_count: 1 }),
    origin_postal_code: '315000',
    origin_street: '88 Harbour Road',
    destination_postal_code: '80331',
    destination_street: 'Marienplatz 1',
    notes: 'Price included a tail-lift truck for the last mile.\nCustomer paid the port storage.',
    excel_filename: 'QX20260115 packing list.xlsx',
    offer_price_eur: null,
    expected_arrival_date: null,
    moved_to_delivered_on: null,
    totals_edited: { volume_m3: false, gross_weight_kg: false, pallet_count: false },
    item_sums: { volume_m3: 7.42, gross_weight_kg: 1580, pallet_count: 6 },
    items: [deliveryItem()],
    pdfs: [deliveryPdf()],
    ...overrides,
  }
}

export function parsedPackingList(overrides: Partial<ParsedPackingList> = {}): ParsedPackingList {
  return {
    total_volume_m3: 7.42,
    total_gross_weight_kg: 1580,
    total_pallet_count: 6,
    volume_mismatch_count: 0,
    sheet_name: 'PACKING LIST ',
    items: [
      deliveryItem(),
      deliveryItem({ source_row: 6, description: 'Shelving unit, ash', article_no: 'SH-1200-ASH' }),
    ],
    ...overrides,
  }
}

export function estimateComparable(overrides: Partial<EstimateComparable> = {}): EstimateComparable {
  return {
    id: 42,
    origin_country: 'CN',
    origin_city: 'Ningbo',
    destination_country: 'DE',
    destination_city: 'Munich',
    transport_method: 'SEA',
    shipping_date: '2026-03-10',
    expected_arrival_date: '2026-04-18',
    arrival_date: '2026-04-21',
    age_months: 7,
    volume_m3: 4.8,
    gross_weight_kg: 900,
    pallet_count: 6,
    chargeable_kg: 4800.0,
    cost_eur: 4100.0,
    offer_price_eur: null,
    notes: '[customer] wanted it fast, rush premium',
    ...overrides,
  }
}

export function estimateComparables(overrides: Partial<EstimateComparables> = {}): EstimateComparables {
  return {
    chargeable_kg: 5000.0,
    fallback: 'NONE',
    removed_company_names: 3,
    comparables: [
      estimateComparable(),
      estimateComparable({
        id: 57,
        origin_city: 'Shanghai',
        shipping_date: '2026-06-02',
        expected_arrival_date: '2026-07-12',
        arrival_date: '2026-07-15',
        age_months: 4,
        volume_m3: 5.6,
        gross_weight_kg: 1100,
        pallet_count: 7,
        chargeable_kg: 5600.0,
        cost_eur: 4650.0,
        notes: 'Incl. customs clearance and last-mile truck',
      }),
      estimateComparable({
        id: 19,
        origin_city: 'Shenzhen',
        destination_city: 'Hamburg',
        shipping_date: '2025-05-06',
        expected_arrival_date: null,
        arrival_date: null,
        age_months: 17,
        volume_m3: null,
        gross_weight_kg: 3900,
        pallet_count: null,
        chargeable_kg: 3900.0,
        cost_eur: 3900.0,
        notes: null,
      }),
    ],
    ...overrides,
  }
}

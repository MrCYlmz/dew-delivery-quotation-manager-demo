<script setup lang="ts">
import DeliveryStatusBadge from '../molecules/DeliveryStatusBadge.vue'
import { EMPTY, formatCost, formatDate, formatNumber } from '../../format'
import { countryName } from '../../countries'
import { TRANSPORT_METHOD_LABELS, type DeliveryListRow, type SortKey, type SortOrder } from '../../api/types'

const props = defineProps<{
  rows: DeliveryListRow[]
  sort: SortKey
  order: SortOrder
}>()

const emit = defineEmits<{
  sort: [key: SortKey]
  open: [id: number]
}>()

const columns: { key: SortKey | null; label: string; num?: boolean }[] = [
  { key: null, label: 'Company' },
  { key: null, label: 'Status' },
  { key: null, label: 'Origin' },
  { key: null, label: 'Destination' },
  { key: null, label: 'Method' },
  { key: 'shipping_date', label: 'Shipped' },
  { key: 'arrival_date', label: 'Arrived' },
  { key: 'cost_eur', label: 'Real price', num: true },
  { key: 'volume_m3', label: 'Volume m³', num: true },
  { key: 'gross_weight_kg', label: 'Gross kg', num: true },
  { key: null, label: 'Pallets', num: true },
  { key: null, label: 'Docs' },
]

function ariaSort(key: SortKey | null): 'ascending' | 'descending' | 'none' | undefined {
  if (!key) return undefined
  if (props.sort !== key) return 'none'
  return props.order === 'asc' ? 'ascending' : 'descending'
}

function indicator(key: SortKey | null): string {
  if (!key || props.sort !== key) return ''
  return props.order === 'asc' ? ' ▲' : ' ▼'
}

function place(country: string, city: string): string {
  return [city, countryName(country)].filter(Boolean).join(', ') || EMPTY
}
</script>

<template>
  <div class="table-scroll">
    <table>
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.label"
            :class="{ num: col.num, sortable: !!col.key }"
            :aria-sort="ariaSort(col.key)"
            scope="col"
          >
            <button
              v-if="col.key"
              type="button"
              class="sort-btn"
              @click="emit('sort', col.key)"
            >
              {{ col.label }}{{ indicator(col.key) }}
            </button>
            <template v-else>{{ col.label }}</template>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.id"
          class="row"
          tabindex="0"
          role="link"
          :aria-label="`Open delivery for ${row.customer_company}`"
          @click="emit('open', row.id)"
          @keydown.enter="emit('open', row.id)"
          @keydown.space.prevent="emit('open', row.id)"
        >
          <td>
            <span class="company">{{ row.customer_company }}</span>
            <span
              v-if="row.has_notes"
              class="notes-marker"
              title="This delivery has notes"
            >
              notes
            </span>
          </td>
          <td><DeliveryStatusBadge :status="row.status" /></td>
          <td class="nowrap">{{ place(row.origin_country, row.origin_city) }}</td>
          <td class="nowrap">{{ place(row.destination_country, row.destination_city) }}</td>
          <td class="nowrap">{{ TRANSPORT_METHOD_LABELS[row.transport_method] ?? row.transport_method }}</td>
          <td class="nowrap">{{ formatDate(row.shipping_date) }}</td>
          <td class="nowrap">{{ formatDate(row.arrival_date) }}</td>
          <td class="num nowrap">{{ formatCost(row.cost_eur) }}</td>
          <td class="num">{{ formatNumber(row.volume_m3) }}</td>
          <td class="num">{{ formatNumber(row.gross_weight_kg) }}</td>
          <td class="num">{{ formatNumber(row.pallet_count) }}</td>
          <td class="num">{{ row.pdf_count > 0 ? row.pdf_count : EMPTY }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.sort-btn {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: inherit;
  text-transform: inherit;
  letter-spacing: inherit;
  cursor: pointer;
}

.sort-btn:hover {
  color: var(--accent);
}

th.sortable {
  cursor: pointer;
}

.row {
  cursor: pointer;
}

.row:hover,
.row:focus-visible {
  background: var(--accent-soft);
}

.company {
  font-weight: 500;
}

.notes-marker {
  display: inline-block;
  margin-left: 0.4rem;
  padding: 0.02rem 0.35rem;
  border-radius: 999px;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  background: var(--warn-soft);
  color: var(--warn);
  border: 1px solid var(--warn);
  vertical-align: 1px;
}
</style>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { excelDownloadUrl } from '../../api/client'
import { formatNumber } from '../../format'
import type { DeliveryDetail } from '../../api/types'

const props = defineProps<{ detail: DeliveryDetail }>()

const headingId = useId()

const totals = computed(() => {
  const d = props.detail
  return [
    {
      key: 'volume_m3' as const,
      label: 'Volume',
      unit: 'm³',
      saved: d.volume_m3,
      edited: d.totals_edited?.volume_m3 ?? false,
      itemSum: d.item_sums?.volume_m3 ?? null,
    },
    {
      key: 'gross_weight_kg' as const,
      label: 'Gross weight',
      unit: 'kg',
      saved: d.gross_weight_kg,
      edited: d.totals_edited?.gross_weight_kg ?? false,
      itemSum: d.item_sums?.gross_weight_kg ?? null,
    },
    {
      key: 'pallet_count' as const,
      label: 'Pallets',
      unit: '',
      saved: d.pallet_count,
      edited: d.totals_edited?.pallet_count ?? false,
      itemSum: d.item_sums?.pallet_count ?? null,
    },
  ]
})
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <div class="row-between">
      <h3 :id="headingId">Cargo totals</h3>
      <a
        v-if="detail.excel_filename"
        :href="excelDownloadUrl(detail.id)"
        class="small"
      >
        Download original Excel ({{ detail.excel_filename }})
      </a>
      <span
        v-else
        class="muted small"
      >No Excel attached</span>
    </div>
    <dl class="totals">
      <div
        v-for="total in totals"
        :key="total.key"
        class="total"
      >
        <dt>{{ total.label }}</dt>
        <dd>
          <span class="value">
            {{ formatNumber(total.saved) }}<span
              v-if="total.unit"
              class="unit"
            >
              {{ total.unit }}</span>
          </span>
          <template v-if="total.edited">
            <span class="edited-badge">edited</span>
            <span class="sum small">
              line items sum to {{ formatNumber(total.itemSum) }}{{ total.unit ? ' ' + total.unit : '' }}
            </span>
          </template>
        </dd>
      </div>
    </dl>
    <p
      v-if="totals.some((t) => t.edited)"
      class="field-hint"
    >
      A saved total that differs from the sum of the stored item rows was overridden by hand. The
      saved figure is the authoritative one.
    </p>
  </section>
</template>

<style scoped>
dl.totals {
  display: flex;
  gap: 2.2rem;
  flex-wrap: wrap;
  margin: 0.6rem 0 0;
}

.total dt {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.total dd {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.total .value {
  font-size: 1.2rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.total .unit {
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--text-muted);
  margin-left: 0.2rem;
}

.edited-badge {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  color: var(--accent);
}

.sum {
  color: var(--text-muted);
}
</style>

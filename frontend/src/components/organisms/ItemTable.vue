<script setup lang="ts">
import { computed, useId } from 'vue'
import { EMPTY, formatNumber } from '../../format'
import type { DeliveryItem } from '../../api/types'

const props = defineProps<{ items: DeliveryItem[] }>()

const mismatches = computed(() => props.items.filter((i) => i.volume_mismatch).length)
const headingId = useId()
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <div class="row-between head">
      <h3 :id="headingId">Item rows ({{ items.length }})</h3>
      <p
        v-if="mismatches > 0"
        class="legend"
      >
        <span
          class="swatch"
          aria-hidden="true"
        />
        {{ mismatches }} {{ mismatches === 1 ? 'row' : 'rows' }} where the stated volume disagrees
        with L × W × H × pallets by more than 0.01 m³
      </p>
    </div>

    <p
      v-if="items.length === 0"
      class="muted small no-items"
    >
      No item rows — no packing list has been uploaded for this delivery.
    </p>

    <div
      v-else
      class="table-scroll"
    >
      <table>
        <thead>
          <tr>
            <th
              scope="col"
              class="num"
            >Row</th>
            <th scope="col">Description</th>
            <th scope="col">Article no.</th>
            <th
              scope="col"
              class="num"
            >L mm</th>
            <th
              scope="col"
              class="num"
            >W mm</th>
            <th
              scope="col"
              class="num"
            >H mm</th>
            <th
              scope="col"
              class="num"
            >Pallets</th>
            <th
              scope="col"
              class="num"
            >Volume m³</th>
            <th
              scope="col"
              class="num"
            >Gross kg</th>
            <th
              scope="col"
              class="num"
            >Net kg</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.source_row"
            :class="{ mismatch: item.volume_mismatch }"
          >
            <td class="num muted">{{ item.source_row }}</td>
            <td>{{ item.description || EMPTY }}</td>
            <td>{{ item.article_no || EMPTY }}</td>
            <td class="num">{{ formatNumber(item.length_mm, 0) }}</td>
            <td class="num">{{ formatNumber(item.width_mm, 0) }}</td>
            <td class="num">{{ formatNumber(item.height_mm, 0) }}</td>
            <td class="num">{{ formatNumber(item.pallet_count) }}</td>
            <td class="num">
              {{ formatNumber(item.volume_m3) }}
              <span
                v-if="item.volume_mismatch"
                class="flag"
                title="Volume cross-check failed"
              >
                ⚠
              </span>
            </td>
            <td class="num">{{ formatNumber(item.gross_weight_kg) }}</td>
            <td class="num">{{ formatNumber(item.net_weight_kg) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.head {
  align-items: flex-start;
}

.legend {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.82rem;
  color: var(--warn);
  max-width: 34rem;
}

.swatch {
  display: inline-block;
  width: 0.85rem;
  height: 0.85rem;
  flex: 0 0 auto;
  border: 1px solid var(--warn);
  background: var(--warn-soft);
  border-radius: 2px;
}

tr.mismatch {
  background: var(--warn-soft);
}

.flag {
  color: var(--warn);
  margin-left: 0.15rem;
}

.no-items {
  margin: 0.4rem 0 0;
}
</style>

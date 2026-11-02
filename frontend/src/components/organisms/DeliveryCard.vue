<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import MenuButton from '../molecules/MenuButton.vue'
import { countryName } from '../../countries'
import { formatArrivalDifference, formatCost, formatDate, formatDaysLate } from '../../format'
import { MISSING_OFFER_PRICE, lateLevel, moveAsksForDates, offerPriceExpected } from '../../status'
import {
  DELIVERY_STATUSES,
  DELIVERY_STATUS_LABELS,
  TRANSPORT_METHOD_LABELS,
  type BoardCard,
  type DeliveryStatus,
} from '../../api/types'

const props = defineProps<{ card: BoardCard; moving: boolean }>()

const emit = defineEmits<{
  move: [status: DeliveryStatus]
  'drag-start': []
  'drag-end': []
}>()

const shippingLabel = computed(() =>
  props.card.status === 'ON_THE_WAY' || props.card.status === 'DELIVERED' ? 'Shipped' : 'Shipping',
)

const delivered = computed(() => props.card.status === 'DELIVERED')

const expectedArrivalMissing = computed(
  () => props.card.status === 'ON_THE_WAY' && props.card.expected_arrival_date === null,
)

const arrivalDifference = computed(() => {
  const { expected_arrival_date: expected, arrival_date: real } = props.card
  return expected === null || real === null ? null : formatArrivalDifference(expected, real)
})

// The board sends days_late only for an overdue card at On the way.
const late = computed(() => {
  const daysLate = props.card.days_late
  return daysLate === null ? null : { level: lateLevel(daysLate), text: formatDaysLate(daysLate) }
})

// An entry that opens the date dialog ends in "…", as a menu entry that asks for more does.
const moveTargets = computed(() =>
  DELIVERY_STATUSES.filter((status) => status !== props.card.status).map((status) => ({
    key: status,
    label: `${DELIVERY_STATUS_LABELS[status]}${moveAsksForDates(props.card, status) ? '…' : ''}`,
  })),
)

const offerMissing = computed(
  () => offerPriceExpected(props.card.status) && props.card.offer_price_eur === null,
)

const moveMenu = useTemplateRef('moveMenu')

function onDragStart(event: DragEvent) {
  // A card already moving would send a second move.
  if (props.moving) {
    event.preventDefault()
    return
  }
  event.dataTransfer?.setData('text/plain', String(props.card.id))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('drag-start')
}

function focusMoveMenu() {
  moveMenu.value?.focus()
}

defineExpose({ focusMoveMenu })
</script>

<template>
  <article
    class="card"
    :class="[{ moving }, late ? ['late', `late-${late.level}`] : []]"
    :draggable="!moving"
    :aria-label="card.customer_company"
    :aria-busy="moving"
    @dragstart="onDragStart"
    @dragend="emit('drag-end')"
  >
    <RouterLink
      :to="{ name: 'delivery-detail', params: { id: card.id } }"
      class="card-title"
    >{{ card.customer_company }}</RouterLink>
    <span
      v-if="late"
      class="late-flag"
    >{{ late.text }}</span>
    <span
      v-else-if="expectedArrivalMissing"
      class="expected-missing"
    >Expected: not set</span>
    <p class="card-route">
      {{ card.origin_city }}, {{ countryName(card.origin_country) }} →
      {{ card.destination_city }}, {{ countryName(card.destination_country) }}
    </p>
    <p class="card-meta">
      <span>{{ TRANSPORT_METHOD_LABELS[card.transport_method] }}</span>
      <span v-if="card.shipping_date">
        {{ shippingLabel }} <strong>{{ formatDate(card.shipping_date) }}</strong>
      </span>
      <span v-if="delivered && card.arrival_date">
        Arrived <strong>{{ formatDate(card.arrival_date) }}</strong>
        <template v-if="arrivalDifference">· {{ arrivalDifference }}</template>
      </span>
      <span v-else-if="!delivered && card.expected_arrival_date">
        Expected <strong>{{ formatDate(card.expected_arrival_date) }}</strong>
      </span>
    </p>
    <div class="card-foot">
      <span
        v-if="card.offer_price_eur !== null"
        class="card-offer"
      >Offer {{ formatCost(card.offer_price_eur) }}</span>
      <span
        v-else-if="offerMissing"
        class="offer-hint"
      >{{ MISSING_OFFER_PRICE }}</span>
      <span v-else />
      <span
        v-if="moving"
        class="small muted"
      >Moving…</span>
      <MenuButton
        v-else
        ref="moveMenu"
        label="Move to"
        :menu-label="`Move ${card.customer_company} to`"
        :caption="`Now: ${DELIVERY_STATUS_LABELS[card.status]}`"
        :entries="moveTargets"
        @select="emit('move', $event)"
      />
    </div>
  </article>
</template>

<style scoped>
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 0.6rem 0.7rem;
  cursor: grab;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.card:hover {
  border-color: var(--border-strong);
}

.card.moving {
  opacity: 0.7;
}

/* The colour fills the card; a 4px left edge keeps it visible at a glance. */
.card.late {
  border-left-width: 4px;
  padding-left: calc(0.7rem - 3px);
}

.card.late-1,
.card.late-1:hover {
  background: var(--late-1-bg);
  border-color: var(--late-1-border);
}

.card.late-2,
.card.late-2:hover {
  background: var(--late-2-bg);
  border-color: var(--late-2-border);
}

.card.late-3,
.card.late-3:hover {
  background: var(--late-3-bg);
  border-color: var(--late-3-border);
}

.late-flag,
.expected-missing {
  align-self: flex-start;
  padding: 0.04rem 0.5rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  background: var(--surface);
  border: 1px solid;
}

.late-1 .late-flag {
  color: var(--late-1-text);
  border-color: var(--late-1-border);
}

.late-2 .late-flag {
  color: var(--late-2-text);
  border-color: var(--late-2-border);
}

.late-3 .late-flag {
  color: var(--late-3-text);
  border-color: var(--late-3-border);
}

/* The accent colour, so it never reads as an overdue level. */
.expected-missing {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.card-title {
  font-weight: 600;
  text-decoration: none;
  color: var(--text);
}

.card-title:hover {
  color: var(--accent);
  text-decoration: underline;
}

.card-route {
  font-size: 0.85rem;
  margin: 0;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.15rem 0.6rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0;
}

.card-meta strong {
  font-weight: 600;
  color: var(--text);
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
  margin-top: 0.15rem;
}

.card-offer {
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
}

.offer-hint {
  padding: 0.05rem 0.45rem;
  border-radius: var(--radius);
  font-size: 0.78rem;
  background: var(--warn-soft);
  color: var(--warn);
  border: 1px solid var(--warn);
}
</style>

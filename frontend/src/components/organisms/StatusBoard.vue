<script setup lang="ts">
import { computed, ref, useId, useTemplateRef, watch } from 'vue'
import DeliveryCard from './DeliveryCard.vue'
import {
  DELIVERY_STATUSES,
  DELIVERY_STATUS_LABELS,
  type BoardCard,
  type DeliveryStatus,
} from '../../api/types'

const props = defineProps<{ cards: BoardCard[]; movingIds: number[]; dialogOpen: boolean }>()

const emit = defineEmits<{ move: [card: BoardCard, status: DeliveryStatus] }>()

const headingId = useId()
const dragged = ref<BoardCard | null>(null)
const dropTarget = ref<DeliveryStatus | null>(null)
const cardViews = useTemplateRef('cardViews')
const menuMovedId = ref<number | null>(null)

// The API sends the cards last updated first; each column keeps that order.
const columns = computed(() =>
  DELIVERY_STATUSES.map((status) => ({
    status,
    cards: props.cards.filter((card) => card.status === status),
  })),
)

function onDragOver(event: DragEvent, status: DeliveryStatus) {
  if (!dragged.value || dragged.value.status === status) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTarget.value = status
}

function onDragLeave(event: DragEvent, status: DeliveryStatus) {
  const column = event.currentTarget
  if (column instanceof Node && event.relatedTarget instanceof Node && column.contains(event.relatedTarget)) {
    return
  }
  if (dropTarget.value === status) dropTarget.value = null
}

function onDrop(status: DeliveryStatus) {
  const card = dragged.value
  endDrag()
  if (card && card.status !== status) emit('move', card, status)
}

function endDrag() {
  dragged.value = null
  dropTarget.value = null
}

function moveFromMenu(card: BoardCard, status: DeliveryStatus) {
  menuMovedId.value = card.id
  emit('move', card, status)
}

// A moving card shows no menu and the date dialog holds the focus, so without this the focus
// would end on the page. It goes back to the card in whichever column the card now is.
watch(
  () =>
    menuMovedId.value !== null && !props.dialogOpen && !props.movingIds.includes(menuMovedId.value),
  (settled) => {
    if (!settled) return
    cardViews.value?.find((view) => view?.$props.card.id === menuMovedId.value)?.focusMoveMenu()
    menuMovedId.value = null
  },
  { flush: 'post' },
)
</script>

<template>
  <div class="board">
    <section
      v-for="column in columns"
      :key="column.status"
      class="column"
      :class="{ 'drop-target': dropTarget === column.status }"
      :aria-labelledby="`${headingId}-${column.status}`"
      @dragenter="onDragOver($event, column.status)"
      @dragover="onDragOver($event, column.status)"
      @dragleave="onDragLeave($event, column.status)"
      @drop.prevent="onDrop(column.status)"
    >
      <div class="column-head">
        <h2 :id="`${headingId}-${column.status}`">{{ DELIVERY_STATUS_LABELS[column.status] }}</h2>
        <span class="column-count">{{ column.cards.length }}</span>
      </div>
      <p
        v-if="column.status === 'DELIVERED'"
        class="column-note"
      >Shown for a week after delivery</p>
      <DeliveryCard
        v-for="card in column.cards"
        :key="card.id"
        ref="cardViews"
        :card="card"
        :moving="movingIds.includes(card.id)"
        @move="moveFromMenu(card, $event)"
        @drag-start="dragged = card"
        @drag-end="endDrag"
      />
      <p
        v-if="column.cards.length === 0"
        class="column-empty"
      >No deliveries</p>
    </section>
  </div>
</template>

<style scoped>
.board {
  display: grid;
  grid-template-columns: repeat(5, minmax(240px, 1fr));
  gap: 0.8rem;
  overflow-x: auto;
  align-items: start;
  padding-bottom: 0.4rem;
}

.column {
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0.6rem;
  min-height: 22rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.column.drop-target {
  border: 2px dashed var(--accent);
  background: var(--accent-soft);
  padding: calc(0.6rem - 1px);
}

.column-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.4rem;
  padding: 0 0.15rem;
}

.column-head h2 {
  font-size: 0.82rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  margin: 0;
}

.column-count {
  font-size: 0.78rem;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.column-note {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin: -0.3rem 0.15rem 0;
}

.column-empty {
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  padding: 0.9rem 0.6rem;
  margin: 0;
  text-align: center;
  font-size: 0.82rem;
  color: var(--text-muted);
}
</style>

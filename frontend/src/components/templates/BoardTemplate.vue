<script setup lang="ts">
import ErrorBanner from '../molecules/ErrorBanner.vue'
import MoveDatesDialog from '../organisms/MoveDatesDialog.vue'
import StatusBoard from '../organisms/StatusBoard.vue'
import type { BoardCard, DeliveryStatus } from '../../api/types'

defineProps<{
  cards: BoardCard[]
  loading: boolean
  error: string | null
  movingIds: number[]
  moveError: string | null
  pendingMove: {
    card: BoardCard
    status: DeliveryStatus
    askShipping: boolean
    fieldErrors: Record<string, string>
    error: string | null
    saving: boolean
  } | null
}>()

const emit = defineEmits<{
  retry: []
  move: [card: BoardCard, status: DeliveryStatus]
  'confirm-move': [dates: { shippingDate: string; expectedArrivalDate: string; arrivalDate: string }]
  'cancel-move': []
  'close-move': []
  'dismiss-move-error': []
  'dismiss-dialog-error': []
}>()
</script>

<template>
  <div class="row-between page-head">
    <h1>Board</h1>
    <RouterLink
      to="/deliveries/new"
      class="btn primary"
    >New delivery</RouterLink>
  </div>

  <ErrorBanner
    v-if="error"
    :message="error"
    @retry="emit('retry')"
  />
  <ErrorBanner
    v-if="moveError"
    :message="moveError"
    retry-label="Dismiss"
    @retry="emit('dismiss-move-error')"
  />

  <p
    v-if="loading"
    class="spinner-text"
  >Loading the board…</p>
  <p
    v-else-if="error"
    class="muted"
  >
    The board could not be loaded, so no cards are shown. This does not mean nothing is running.
  </p>
  <template v-else>
    <p
      v-if="cards.length === 0"
      class="muted small empty-note"
    >
      No running deliveries. Past deliveries are in the
      <RouterLink to="/deliveries">Deliveries</RouterLink> list.
    </p>
    <StatusBoard
      :cards="cards"
      :moving-ids="movingIds"
      :dialog-open="pendingMove !== null"
      @move="(card, status) => emit('move', card, status)"
    />
  </template>

  <MoveDatesDialog
    v-if="pendingMove"
    :company="pendingMove.card.customer_company"
    :status="pendingMove.status"
    :ask-shipping="pendingMove.askShipping"
    :current-expected-arrival-date="pendingMove.card.expected_arrival_date"
    :current-arrival-date="pendingMove.card.arrival_date"
    :field-errors="pendingMove.fieldErrors"
    :error="pendingMove.error"
    :saving="pendingMove.saving"
    @confirm="emit('confirm-move', $event)"
    @cancel="emit('cancel-move')"
    @close="emit('close-move')"
    @dismiss-error="emit('dismiss-dialog-error')"
  />
</template>

<style scoped>
.page-head {
  margin-bottom: 0.9rem;
}

.empty-note {
  margin-top: 0;
}
</style>

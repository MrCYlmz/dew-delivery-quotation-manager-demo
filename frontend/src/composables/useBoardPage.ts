import { ref } from 'vue'
import { ApiError, errorMessage, getBoard, moveDelivery } from '../api/client'
import {
  DELIVERY_STATUS_LABELS,
  type BoardCard,
  type DeliveryMoveBody,
  type DeliveryStatus,
} from '../api/types'
import { arrivalRequired, moveAsksForDates } from '../status'

interface PendingMove {
  card: BoardCard
  status: DeliveryStatus
  askShipping: boolean
  fieldErrors: Record<string, string>
  error: string | null
  saving: boolean
}

export function useBoardPage() {
  const cards = ref<BoardCard[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const movingIds = ref<number[]>([])
  const moveError = ref<string | null>(null)
  const pendingMove = ref<PendingMove | null>(null)

  let latestLoad = 0

  async function fetchBoard(showLoading: boolean) {
    const request = ++latestLoad
    if (showLoading) loading.value = true
    error.value = null
    try {
      const result = await getBoard()
      if (request !== latestLoad) return
      cards.value = result
    } catch (err) {
      if (request !== latestLoad) return
      cards.value = []
      error.value = errorMessage(err)
    } finally {
      if (request === latestLoad) loading.value = false
    }
  }

  function load() {
    return fetchBoard(true)
  }

  // The saved card was updated last, so it leads its column as the API would order it.
  function placeMoved(moved: BoardCard) {
    cards.value = [moved, ...cards.value.filter((card) => card.id !== moved.id)]
  }

  function requestMove(card: BoardCard, status: DeliveryStatus) {
    moveError.value = null
    if (moveAsksForDates(card, status)) {
      pendingMove.value = {
        card,
        status,
        askShipping: !card.shipping_date,
        fieldErrors: {},
        error: null,
        saving: false,
      }
      return
    }
    void moveNow(card, status)
  }

  function reportFailedMove(card: BoardCard, status: DeliveryStatus, err: unknown) {
    moveError.value = `${card.customer_company} could not be moved to ${
      DELIVERY_STATUS_LABELS[status]
    }. It stays at ${DELIVERY_STATUS_LABELS[card.status]}. ${errorMessage(err)}`
    // The delivery may have changed or gone meanwhile, so show what is saved now.
    void fetchBoard(false)
  }

  async function moveNow(card: BoardCard, status: DeliveryStatus) {
    movingIds.value = [...movingIds.value, card.id]
    try {
      placeMoved(await moveDelivery(card.id, { status }))
    } catch (err) {
      reportFailedMove(card, status, err)
    } finally {
      movingIds.value = movingIds.value.filter((id) => id !== card.id)
    }
  }

  async function confirmMove(dates: {
    shippingDate: string
    expectedArrivalDate: string
    arrivalDate: string
  }) {
    const move = pendingMove.value
    if (!move) return
    const statusLabel = DELIVERY_STATUS_LABELS[move.status]
    const fieldErrors: Record<string, string> = {}
    const body: DeliveryMoveBody = { status: move.status }
    if (move.askShipping) {
      if (dates.shippingDate) body.shipping_date = dates.shippingDate
      else fieldErrors.shipping_date = `Shipping date is required at ${statusLabel}.`
    }
    // The dialog asks for the real arrival at Delivered and offers the expected one at On the way.
    if (arrivalRequired(move.status)) {
      if (dates.arrivalDate) body.arrival_date = dates.arrivalDate
      else fieldErrors.arrival_date = `Arrival is required at ${statusLabel}.`
    } else if (dates.expectedArrivalDate) {
      body.expected_arrival_date = dates.expectedArrivalDate
    }
    move.fieldErrors = fieldErrors
    move.error = null
    if (Object.keys(fieldErrors).length > 0) return

    move.saving = true
    try {
      placeMoved(await moveDelivery(move.card.id, body))
      if (pendingMove.value === move) pendingMove.value = null
    } catch (err) {
      if (pendingMove.value !== move) {
        reportFailedMove(move.card, move.status, err)
        return
      }
      if (err instanceof ApiError && err.fields) move.fieldErrors = err.fields
      move.error = errorMessage(err)
    } finally {
      move.saving = false
      movingIds.value = movingIds.value.filter((id) => id !== move.card.id)
    }
  }

  function cancelMove() {
    pendingMove.value = null
  }

  // The browser closed the dialog on its own. A running save goes on as a move on the board.
  function closeMove() {
    const move = pendingMove.value
    if (!move) return
    if (move.saving) movingIds.value = [...movingIds.value, move.card.id]
    pendingMove.value = null
  }

  function dismissMoveError() {
    moveError.value = null
  }

  function dismissDialogError() {
    if (pendingMove.value) pendingMove.value.error = null
  }

  void load()

  return {
    cards,
    loading,
    error,
    movingIds,
    moveError,
    pendingMove,
    load,
    requestMove,
    confirmMove,
    cancelMove,
    closeMove,
    dismissMoveError,
    dismissDialogError,
  }
}

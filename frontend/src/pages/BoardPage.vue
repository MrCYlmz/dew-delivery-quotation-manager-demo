<script setup lang="ts">
import BoardTemplate from '../components/templates/BoardTemplate.vue'
import { useBoardPage } from '../composables/useBoardPage'

const {
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
} = useBoardPage()
</script>

<template>
  <BoardTemplate
    :cards="cards"
    :loading="loading"
    :error="error"
    :moving-ids="movingIds"
    :move-error="moveError"
    :pending-move="pendingMove"
    @retry="load"
    @move="requestMove"
    @confirm-move="confirmMove"
    @cancel-move="cancelMove"
    @close-move="closeMove"
    @dismiss-move-error="dismissMoveError"
    @dismiss-dialog-error="dismissDialogError"
  />
</template>

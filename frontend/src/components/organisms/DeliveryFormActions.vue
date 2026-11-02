<script setup lang="ts">
defineProps<{ isEdit: boolean; saving: boolean }>()

const confirmingDelete = defineModel<boolean>('confirmingDelete', { required: true })

const emit = defineEmits<{ cancel: []; delete: [] }>()
</script>

<template>
  <div class="actions panel">
    <button
      type="submit"
      class="primary"
      :disabled="saving"
    >
      {{ saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create delivery' }}
    </button>
    <button
      type="button"
      :disabled="saving"
      @click="emit('cancel')"
    >Cancel</button>

    <template v-if="isEdit">
      <span class="spacer" />
      <template v-if="!confirmingDelete">
        <button
          type="button"
          class="danger"
          :disabled="saving"
          @click="confirmingDelete = true"
        >
          Delete delivery
        </button>
      </template>
      <template v-else>
        <span class="confirm-text">
          Delete this delivery, its item rows and all attached files? This cannot be undone.
        </span>
        <button
          type="button"
          class="danger"
          :disabled="saving"
          @click="emit('delete')"
        >
          Yes, delete
        </button>
        <button
          type="button"
          :disabled="saving"
          @click="confirmingDelete = false"
        >Keep it</button>
      </template>
    </template>
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  position: sticky;
  bottom: 0.6rem;
}

.spacer {
  flex: 1 1 1rem;
}

.confirm-text {
  color: var(--danger);
  font-size: 0.85rem;
}
</style>

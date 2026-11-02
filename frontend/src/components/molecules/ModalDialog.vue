<script setup lang="ts">
import { onMounted, useId, useTemplateRef } from 'vue'

defineProps<{ title: string }>()

const emit = defineEmits<{ cancel: []; close: [] }>()

const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const titleId = useId()

// The parent mounts this only while it is open, so Escape must ask the parent to close it.
onMounted(() => dialog.value?.showModal())

function onCancel(event: Event) {
  event.preventDefault()
  emit('cancel')
}

// The browser may close the dialog even when cancel was prevented, such as on a second Escape.
function onClose() {
  emit('close')
}
</script>

<template>
  <dialog
    ref="dialog"
    class="modal"
    :aria-labelledby="titleId"
    @cancel="onCancel"
    @close="onClose"
  >
    <h2 :id="titleId">{{ title }}</h2>
    <slot />
  </dialog>
</template>

<style scoped>
.modal {
  width: 26rem;
  max-width: calc(100% - 2rem);
  background: var(--surface);
  color: var(--text);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  padding: 1.1rem 1.2rem;
  box-shadow: var(--shadow);
}

.modal::backdrop {
  background: var(--backdrop);
}

h2 {
  margin-bottom: 0.3rem;
}
</style>

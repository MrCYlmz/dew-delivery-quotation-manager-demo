<script setup lang="ts">
import { computed, useId, useTemplateRef } from 'vue'
import FieldMessage from '../atoms/FieldMessage.vue'
import FileInput from '../atoms/FileInput.vue'
import { formatBytes, formatDateTime } from '../../format'
import type { DeliveryPdf } from '../../api/types'

const props = defineProps<{
  attached: DeliveryPdf[]
  removingId: number | null
  error: string | null
  disabled?: boolean
}>()

const queued = defineModel<File[]>('queued', { required: true })

const emit = defineEmits<{ remove: [pdf: DeliveryPdf] }>()

const headingId = useId()
const hintId = useId()
const errorId = useId()
const picker = useTemplateRef<InstanceType<typeof FileInput>>('picker')

const total = computed(() => props.attached.length + queued.value.length)
const describedBy = computed(() => (props.error ? `${hintId} ${errorId}` : hintId))

function key(file: File): string {
  return `${file.name}:${file.size}`
}

function onPick(picked: File[]) {
  const seen = new Set(queued.value.map(key))
  const added = picked.filter((file) => {
    if (seen.has(key(file))) return false
    seen.add(key(file))
    return true
  })
  queued.value = [...queued.value, ...added]
  picker.value?.clear()
}

function removeQueued(file: File) {
  queued.value = queued.value.filter((f) => f !== file)
}
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <div class="row-between">
      <h3 :id="headingId">Delivery documents (PDF)</h3>
      <span
        v-if="total"
        class="badge"
      >{{ total }} {{ total === 1 ? 'file' : 'files' }}</span>
    </div>

    <div class="pick">
      <FileInput
        ref="picker"
        label="Delivery document PDFs"
        accept="application/pdf,.pdf"
        multiple
        :disabled="disabled"
        :described-by="describedBy"
        @pick="onPick"
      />
    </div>
    <FieldMessage
      :id="hintId"
      tone="hint"
    >
      Select several at once — a delivery usually travels with more than one: the delivery note, a
      bill of lading, a customs form. Stored as-is and never read. Optional.
    </FieldMessage>

    <FieldMessage
      v-if="error"
      :id="errorId"
      tone="error"
      role="alert"
    >{{ error }}</FieldMessage>

    <ul
      v-if="total"
      class="files"
    >
      <li
        v-for="pdf in attached"
        :key="`a-${pdf.id}`"
      >
        <span class="name">{{ pdf.original_filename }}</span>
        <span class="meta muted small">
          {{ formatBytes(pdf.size_bytes) }} · added {{ formatDateTime(pdf.uploaded_at) }}
        </span>
        <button
          type="button"
          class="link remove"
          :disabled="disabled || removingId === pdf.id"
          @click="emit('remove', pdf)"
        >
          {{ removingId === pdf.id ? 'Removing…' : 'Remove' }}
        </button>
      </li>

      <li
        v-for="file in queued"
        :key="`q-${key(file)}`"
        class="queued"
      >
        <span class="name">{{ file.name }}</span>
        <span class="meta muted small">{{ formatBytes(file.size) }} · uploads on save</span>
        <button
          type="button"
          class="link remove"
          :disabled="disabled"
          @click="removeQueued(file)"
        >
          Remove
        </button>
      </li>
    </ul>
    <p
      v-else
      class="muted small no-files"
    >No documents attached.</p>
  </section>
</template>

<style scoped>
.files {
  list-style: none;
  margin: 0.7rem 0 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.files li {
  display: flex;
  align-items: baseline;
  gap: 0.7rem;
  flex-wrap: wrap;
  padding: 0.45rem 0.7rem;
  border-bottom: 1px solid var(--border);
}

.files li:last-child {
  border-bottom: none;
}

.files li.queued {
  background: var(--accent-soft);
}

.name {
  font-weight: 500;
  word-break: break-word;
}

.meta {
  flex: 1 1 auto;
}

.remove {
  flex: 0 0 auto;
}

.no-files {
  margin: 0.7rem 0 0;
}
</style>

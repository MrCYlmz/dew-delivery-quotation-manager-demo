<script setup lang="ts">
import { useTemplateRef } from 'vue'

defineProps<{
  label: string
  accept: string
  multiple?: boolean
  disabled?: boolean
  describedBy?: string
}>()

const emit = defineEmits<{ pick: [files: File[]] }>()

const input = useTemplateRef<HTMLInputElement>('input')

function onChange() {
  emit('pick', Array.from(input.value?.files ?? []))
}

function clear() {
  if (input.value) input.value.value = ''
}

defineExpose({ clear })
</script>

<template>
  <input
    ref="input"
    type="file"
    :accept="accept"
    :multiple="multiple"
    :disabled="disabled"
    :aria-label="label"
    :aria-describedby="describedBy"
    @change="onChange"
  >
</template>

<style scoped>
input[type='file'] {
  border: 1px dashed var(--border-strong);
  padding: 0.45rem;
  background: var(--surface-alt);
}
</style>

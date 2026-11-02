<script setup lang="ts">
import { computed } from 'vue'
import FieldMessage from '../atoms/FieldMessage.vue'

const props = defineProps<{
  id: string
  label: string
  required?: boolean
  hideLabel?: boolean
  error?: string | null
  warning?: string | null
  hint?: string | null
}>()

defineSlots<{ default(props: { describedBy: string | undefined }): unknown }>()

const errorId = computed(() => `${props.id}-error`)
const warningId = computed(() => `${props.id}-warning`)
const hintId = computed(() => `${props.id}-hint`)

const describedBy = computed(() => {
  if (props.error) return errorId.value
  if (props.warning) return warningId.value
  if (props.hint) return hintId.value
  return undefined
})
</script>

<template>
  <div class="field">
    <label
      :for="id"
      :class="{ required, 'sr-label': hideLabel }"
    >{{ label }}</label>
    <slot :described-by="describedBy" />
    <FieldMessage
      v-if="error"
      :id="errorId"
      tone="error"
    >{{ error }}</FieldMessage>
    <FieldMessage
      v-else-if="warning"
      :id="warningId"
      tone="warn"
    >{{ warning }}</FieldMessage>
    <FieldMessage
      v-else-if="hint"
      :id="hintId"
      tone="hint"
    >{{ hint }}</FieldMessage>
  </div>
</template>

<style scoped>
.sr-label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
</style>

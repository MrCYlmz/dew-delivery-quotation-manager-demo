<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue'
import FieldMessage from '../atoms/FieldMessage.vue'

const props = defineProps<{
  options: string[]
  id: string
  placeholder?: string
  invalid?: boolean
  autocompleteHint?: string
  describedBy?: string
}>()

const modelValue = defineModel<string>({ required: true })

const open = ref(false)
const highlighted = ref(-1)
const root = useTemplateRef<HTMLElement>('root')
const messageId = computed(() => `${props.id}-suggestion`)

const matches = computed(() => {
  const needle = modelValue.value.trim().toLowerCase()
  const list = needle
    ? props.options.filter((o) => o.toLowerCase().includes(needle))
    : props.options
  return list.slice(0, 50)
})

const nearDuplicate = computed(() => {
  const typed = modelValue.value.trim()
  if (!typed) return null
  const exact = props.options.some((o) => o === typed)
  if (exact) return null
  return props.options.find((o) => o.trim().toLowerCase() === typed.toLowerCase()) ?? null
})

const isKnown = computed(() => props.options.some((o) => o === modelValue.value.trim()))

const hasMessage = computed(() => !!nearDuplicate.value || isKnown.value || !!props.autocompleteHint)
const ariaDescribedBy = computed(
  () =>
    [hasMessage.value ? messageId.value : '', props.describedBy ?? ''].filter(Boolean).join(' ') ||
    undefined,
)

function choose(value: string) {
  modelValue.value = value
  open.value = false
  highlighted.value = -1
}

function onInput(event: Event) {
  modelValue.value = (event.target as HTMLInputElement).value
  open.value = true
  highlighted.value = -1
}

function onFocus() {
  open.value = true
}

function onBlur() {
  void nextTick(() => {
    if (!root.value?.contains(document.activeElement)) open.value = false
  })
}

function move(delta: number) {
  if (!open.value) {
    open.value = true
    return
  }
  const count = matches.value.length
  if (count === 0) return
  highlighted.value = (highlighted.value + delta + count) % count
}

function onEnter(event: KeyboardEvent) {
  if (open.value && highlighted.value >= 0) {
    event.preventDefault()
    const picked = matches.value[highlighted.value]
    if (picked) choose(picked)
  }
}
</script>

<template>
  <div
    ref="root"
    class="autocomplete"
    @blur.capture="onBlur"
  >
    <input
      :id="id"
      type="text"
      role="combobox"
      autocomplete="off"
      aria-autocomplete="list"
      :aria-expanded="open"
      :aria-controls="`${id}-list`"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-describedby="ariaDescribedBy"
      :value="modelValue"
      :placeholder="placeholder"
      @input="onInput"
      @focus="onFocus"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter="onEnter"
      @keydown.esc="open = false"
    >

    <ul
      v-if="open && matches.length"
      :id="`${id}-list`"
      class="options"
      role="listbox"
    >
      <li
        v-for="(option, index) in matches"
        :key="option"
        :class="{ highlighted: index === highlighted, current: option === modelValue }"
        role="option"
        :aria-selected="option === modelValue"
        @mousedown.prevent="choose(option)"
      >
        {{ option }}
      </li>
    </ul>

    <FieldMessage
      v-if="nearDuplicate"
      :id="messageId"
      tone="error"
      class="near-dup"
    >
      “{{ nearDuplicate }}” already exists and differs only in spelling. Using both splits this
      value across two entries.
      <button
        type="button"
        class="link"
        @click="choose(nearDuplicate)"
      >
        Use “{{ nearDuplicate }}”
      </button>
    </FieldMessage>
    <FieldMessage
      v-else-if="isKnown"
      :id="messageId"
      tone="hint"
    >Existing value.</FieldMessage>
    <FieldMessage
      v-else-if="autocompleteHint"
      :id="messageId"
      tone="hint"
    >{{ autocompleteHint }}</FieldMessage>
  </div>
</template>

<style scoped>
.autocomplete {
  position: relative;
}

.options {
  position: absolute;
  z-index: 10;
  left: 0;
  right: 0;
  margin: 2px 0 0;
  padding: 0;
  list-style: none;
  max-height: 14rem;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: 0 6px 16px rgb(16 24 40 / 14%);
}

.options li {
  padding: 0.32rem 0.55rem;
  cursor: pointer;
  font-size: 0.9rem;
}

.options li.highlighted,
.options li:hover {
  background: var(--accent-soft);
}

.options li.current {
  font-weight: 600;
}

.near-dup {
  line-height: 1.4;
}
</style>

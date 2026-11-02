<script setup lang="ts" generic="Key extends string">
import { nextTick, ref, useTemplateRef } from 'vue'

defineProps<{
  label: string
  menuLabel: string
  caption?: string
  entries: { key: Key; label: string }[]
}>()

const emit = defineEmits<{ select: [key: Key] }>()

const open = ref(false)
const root = useTemplateRef<HTMLDivElement>('root')
const toggleButton = useTemplateRef<HTMLButtonElement>('toggleButton')
const entryButtons = useTemplateRef<HTMLButtonElement[]>('entryButtons')

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  await nextTick()
  entryButtons.value?.[0]?.focus()
}

function focus() {
  toggleButton.value?.focus()
}

function close() {
  open.value = false
  focus()
}

function pick(key: Key) {
  close()
  emit('select', key)
}

function moveFocus(step: number) {
  const buttons = entryButtons.value ?? []
  if (buttons.length === 0) return
  const current = buttons.findIndex((button) => button === document.activeElement)
  buttons[(current + step + buttons.length) % buttons.length].focus()
}

function onFocusOut(event: FocusEvent) {
  if (event.relatedTarget instanceof Node && root.value?.contains(event.relatedTarget)) return
  open.value = false
}

defineExpose({ focus })
</script>

<template>
  <div
    ref="root"
    class="menu-button"
    @focusout="onFocusOut"
  >
    <button
      ref="toggleButton"
      type="button"
      class="toggle"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="toggle"
    >
      {{ label }} <span aria-hidden="true">▾</span>
    </button>
    <ul
      v-if="open"
      class="menu"
      role="menu"
      :aria-label="menuLabel"
      @keydown.down.prevent="moveFocus(1)"
      @keydown.up.prevent="moveFocus(-1)"
      @keydown.esc.prevent="close"
    >
      <li
        v-if="caption"
        class="caption"
        role="none"
      >{{ caption }}</li>
      <li
        v-for="entry in entries"
        :key="entry.key"
        role="none"
      >
        <button
          ref="entryButtons"
          type="button"
          role="menuitem"
          @click="pick(entry.key)"
        >{{ entry.label }}</button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.menu-button {
  position: relative;
}

.toggle {
  font-size: 0.8rem;
  padding: 0.18rem 0.5rem;
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 0.2rem);
  z-index: 5;
  min-width: 11rem;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 0.25rem;
  list-style: none;
  margin: 0;
}

.menu button {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  padding: 0.35rem 0.55rem;
  font-size: 0.85rem;
}

.menu button:hover,
.menu button:focus-visible {
  background: var(--accent-soft);
}

.caption {
  padding: 0.35rem 0.55rem;
  font-size: 0.78rem;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.2rem;
}
</style>

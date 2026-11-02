<script setup lang="ts">
defineProps<{ tone: 'neutral' | 'accent' | 'accent-strong' | 'warn' | 'ok' }>()
</script>

<template>
  <span :class="['badge-label', tone]"><slot /></span>
</template>

<style scoped>
.badge-label {
  display: inline-block;
  padding: 0.06rem 0.45rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  border: 1px solid var(--border);
  background: var(--surface-alt);
  color: var(--text-muted);
  white-space: nowrap;
}

.accent {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent-soft);
}

.accent-strong {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent);
}

.warn {
  background: var(--warn-soft);
  color: var(--warn);
  border-color: var(--warn);
}

.ok {
  color: var(--ok);
  border-color: var(--ok);
}
</style>

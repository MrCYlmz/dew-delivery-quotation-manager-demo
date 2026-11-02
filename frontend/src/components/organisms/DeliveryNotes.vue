<script setup lang="ts">
import { useId } from 'vue'

defineProps<{ notes: string }>()

const headingId = useId()
</script>

<template>
  <section
    class="panel notes"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Notes</h3>
    <p class="notes-body">{{ notes }}</p>
  </section>
</template>

<style scoped>
.notes-body {
  white-space: pre-wrap;
  margin: 0;
  padding: 0.7rem 0.85rem;
  background: var(--warn-soft);
  border-left: 3px solid var(--warn);
  border-radius: 0 var(--radius) var(--radius) 0;
  line-height: 1.6;
}
</style>

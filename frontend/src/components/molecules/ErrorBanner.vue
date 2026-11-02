<script setup lang="ts">
defineProps<{ message: string; retryLabel?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div
    class="error-banner"
    role="alert"
  >
    <div class="error-body">
      <strong>Something went wrong.</strong>
      <span>{{ message }}</span>
    </div>
    <button
      type="button"
      @click="$emit('retry')"
    >{{ retryLabel ?? 'Try again' }}</button>
  </div>
</template>

<style scoped>
.error-banner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  background: var(--danger-soft);
  border: 1px solid var(--danger);
  border-radius: var(--radius);
  padding: 0.7rem 0.9rem;
  margin-bottom: 1rem;
}

.error-body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  color: var(--danger);
}
</style>

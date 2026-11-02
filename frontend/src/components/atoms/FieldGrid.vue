<template>
  <div class="grid">
    <slot />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.7rem 0.9rem;
  align-items: start;
}
</style>

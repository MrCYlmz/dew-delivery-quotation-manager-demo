<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="app-header">
    <RouterLink
      to="/"
      class="brand"
    >
      Delivery Quotation Archive
      <small>Past deliveries, filtered to comparables</small>
    </RouterLink>
    <nav
      class="main-nav"
      aria-label="Main"
    >
      <RouterLink
        to="/"
        class="nav-link"
      >Board</RouterLink>
      <RouterLink
        to="/deliveries"
        class="nav-link"
      >Deliveries</RouterLink>
      <RouterLink
        to="/estimate"
        class="nav-link"
      >Estimate</RouterLink>
    </nav>
  </header>
  <main>
    <RouterView />
  </main>
</template>

<style scoped>
.main-nav {
  display: flex;
  align-items: center;
  gap: 1.1rem;
}

.nav-link {
  color: var(--text-muted);
  text-decoration: none;
  font-weight: 600;
  padding: 0.2rem 0;
}

.nav-link[aria-current='page'] {
  color: var(--accent);
  border-bottom: 2px solid var(--accent);
}
</style>

<script setup lang="ts">
defineProps<{ id: string; tone: 'error' | 'warn' | 'hint' }>()
</script>

<template>
  <p
    :id="id"
    :class="`field-${tone}`"
  ><slot /></p>
</template>

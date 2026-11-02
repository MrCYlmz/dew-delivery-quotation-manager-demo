<script setup lang="ts">
import DeliveryDetailTemplate from '../components/templates/DeliveryDetailTemplate.vue'
import { useDeliveryDetailPage } from '../composables/useDeliveryDetailPage'

const { detail, loading, error, load } = useDeliveryDetailPage()
</script>

<template>
  <DeliveryDetailTemplate
    :detail="detail"
    :loading="loading"
    :error="error"
    @retry="load"
  />
</template>

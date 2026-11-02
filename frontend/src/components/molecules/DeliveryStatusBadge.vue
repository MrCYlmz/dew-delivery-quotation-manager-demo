<script setup lang="ts">
import BadgeLabel from '../atoms/BadgeLabel.vue'
import { DELIVERY_STATUS_LABELS, type DeliveryStatus } from '../../api/types'

defineProps<{ status: DeliveryStatus }>()

const TONES = {
  TO_DO: 'neutral',
  OFFERS_RECEIVED: 'accent',
  OFFER_ACCEPTED: 'accent-strong',
  ON_THE_WAY: 'warn',
  DELIVERED: 'ok',
} as const satisfies Record<DeliveryStatus, string>
</script>

<template>
  <BadgeLabel :tone="TONES[status]">{{ DELIVERY_STATUS_LABELS[status] }}</BadgeLabel>
</template>

<script setup lang="ts">
import DeliveryStatusBadge from '../molecules/DeliveryStatusBadge.vue'
import ErrorBanner from '../molecules/ErrorBanner.vue'
import CargoTotals from '../organisms/CargoTotals.vue'
import DeliveryNotes from '../organisms/DeliveryNotes.vue'
import DeliveryRoute from '../organisms/DeliveryRoute.vue'
import DeliverySummary from '../organisms/DeliverySummary.vue'
import ItemTable from '../organisms/ItemTable.vue'
import PdfViewer from '../organisms/PdfViewer.vue'
import { countryName } from '../../countries'
import { TRANSPORT_METHOD_LABELS, type DeliveryDetail } from '../../api/types'

defineProps<{
  detail: DeliveryDetail | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <ErrorBanner
    v-if="error"
    :message="error"
    @retry="emit('retry')"
  />
  <p
    v-if="loading"
    class="spinner-text"
  >Loading delivery…</p>

  <template v-else-if="detail">
    <div class="row-between page-head">
      <div>
        <h1>{{ detail.customer_company }}</h1>
        <p class="muted small route-line">
          <DeliveryStatusBadge :status="detail.status" />
          {{ detail.origin_city }}, {{ countryName(detail.origin_country) }} →
          {{ detail.destination_city }}, {{ countryName(detail.destination_country) }} ·
          {{ TRANSPORT_METHOD_LABELS[detail.transport_method] ?? detail.transport_method }}
        </p>
      </div>
      <div class="head-actions">
        <RouterLink to="/deliveries">Back to list</RouterLink>
        <RouterLink
          :to="`/deliveries/${detail.id}/edit`"
          class="btn primary"
        >Edit</RouterLink>
      </div>
    </div>

    <div class="two-col">
      <DeliverySummary :detail="detail" />
      <DeliveryRoute :detail="detail" />
    </div>

    <DeliveryNotes
      v-if="detail.notes && detail.notes.trim()"
      :notes="detail.notes"
    />

    <CargoTotals :detail="detail" />

    <ItemTable :items="detail.items" />

    <PdfViewer
      :delivery-id="detail.id"
      :pdfs="detail.pdfs"
    />
  </template>

  <p
    v-else-if="!error"
    class="spinner-text"
  >Delivery not found.</p>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.9rem;
  align-items: flex-start;
}

.route-line {
  margin: 0;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0 1.1rem;
}
</style>

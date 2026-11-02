import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { errorMessage, getDelivery } from '../api/client'
import type { DeliveryDetail } from '../api/types'

export function useDeliveryDetailPage() {
  const route = useRoute()

  const detail = ref<DeliveryDetail | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const deliveryId = computed(() => {
    const raw = route.params.id
    return Number(Array.isArray(raw) ? raw[0] : raw)
  })

  let latestRequest = 0

  async function load() {
    const id = deliveryId.value
    const request = ++latestRequest
    loading.value = true
    error.value = null
    try {
      const result = await getDelivery(id)
      if (request !== latestRequest) return
      detail.value = result
    } catch (err) {
      if (request !== latestRequest) return
      detail.value = null
      error.value = errorMessage(err)
    } finally {
      if (request === latestRequest) loading.value = false
    }
  }

  watch(
    () => route.fullPath,
    () => {
      if (route.name === 'delivery-detail') void load()
    },
    { immediate: true },
  )

  return { detail, loading, error, load }
}

<script setup lang="ts">
import { computed, useId } from 'vue'
import { countryName } from '../../countries'
import type { DeliveryDetail } from '../../api/types'

const props = defineProps<{ detail: DeliveryDetail }>()

const headingId = useId()

function address(
  country: string,
  city: string,
  postal: string | null,
  street: string | null,
): string[] {
  return [street, [postal, city].filter(Boolean).join(' '), countryName(country)].filter(
    (line): line is string => !!line && line.trim() !== '',
  )
}

const origin = computed(() =>
  address(
    props.detail.origin_country,
    props.detail.origin_city,
    props.detail.origin_postal_code,
    props.detail.origin_street,
  ),
)

const destination = computed(() =>
  address(
    props.detail.destination_country,
    props.detail.destination_city,
    props.detail.destination_postal_code,
    props.detail.destination_street,
  ),
)
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Route</h3>
    <div class="route-grid">
      <div>
        <h4>Origin</h4>
        <p class="addr">
          <span
            v-for="line in origin"
            :key="line"
          >
            {{ line }}
          </span>
        </p>
      </div>
      <div>
        <h4>Destination</h4>
        <p class="addr">
          <span
            v-for="line in destination"
            :key="line"
          >
            {{ line }}
          </span>
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.route-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.9rem;
}

@media (max-width: 520px) {
  .route-grid {
    grid-template-columns: 1fr;
  }
}

h4 {
  margin: 0 0 0.25rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.addr {
  margin: 0;
  display: flex;
  flex-direction: column;
}
</style>

<script setup lang="ts">
import { computed, useId } from 'vue'
import FieldGrid from '../atoms/FieldGrid.vue'
import FormField from '../molecules/FormField.vue'
import type { AddressSide } from '../../api/types'
import { COUNTRIES } from '../../countries'

const props = defineProps<{
  side: AddressSide
  title: string
}>()

const country = defineModel<string>('country', { required: true })
const city = defineModel<string>('city', { required: true })

const headingId = useId()

const ids = computed(() => ({
  country: `${props.side}_country`,
  city: `${props.side}_city`,
}))
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">{{ title }}</h3>
    <FieldGrid>
      <FormField
        :id="ids.country"
        label="Country"
        required
      >
        <select
          :id="ids.country"
          v-model="country"
        >
          <option value="">Choose…</option>
          <option
            v-for="c in COUNTRIES"
            :key="c.code"
            :value="c.code"
          >
            {{ c.name }}
          </option>
        </select>
      </FormField>
      <FormField
        :id="ids.city"
        label="City"
      >
        <input
          :id="ids.city"
          v-model="city"
          type="text"
          autocomplete="off"
        >
      </FormField>
    </FieldGrid>
  </section>
</template>

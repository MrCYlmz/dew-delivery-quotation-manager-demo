<script setup lang="ts">
import { computed, useId } from 'vue'
import FieldGrid from '../atoms/FieldGrid.vue'
import AutocompleteInput from '../molecules/AutocompleteInput.vue'
import FormField from '../molecules/FormField.vue'
import type { AddressSide } from '../../api/types'
import { COUNTRIES, isKnownCountryCode } from '../../countries'

const props = defineProps<{
  side: AddressSide
  title: string
  fieldErrors: Record<string, string>
  cities: string[]
}>()

const country = defineModel<string>('country', { required: true })
const city = defineModel<string>('city', { required: true })
const postalCode = defineModel<string>('postalCode', { required: true })
const street = defineModel<string>('street', { required: true })

const headingId = useId()

const ids = computed(() => ({
  country: `${props.side}_country`,
  city: `${props.side}_city`,
  postalCode: `${props.side}_postal_code`,
  street: `${props.side}_street`,
}))

const countryError = computed(() => props.fieldErrors[ids.value.country])
const cityError = computed(() => props.fieldErrors[ids.value.city])

const legacyCountry = computed(() =>
  country.value && !isKnownCountryCode(country.value) ? country.value : null,
)

const countryMessage = computed(() => {
  if (countryError.value) return countryError.value
  if (legacyCountry.value) {
    return `“${legacyCountry.value}” is not an ISO country code. Pick a country to normalize this record.`
  }
  return null
})
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
        v-slot="{ describedBy }"
        label="Country"
        required
        :error="countryMessage"
        :hint="country ? `Stored as ${country}.` : null"
      >
        <select
          :id="ids.country"
          v-model="country"
          :aria-invalid="countryError ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
          <option value="">Choose…</option>
          <option
            v-if="legacyCountry"
            :value="legacyCountry"
          >
            {{ legacyCountry }} (not a known country code)
          </option>
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
        v-slot="{ describedBy }"
        label="City"
        required
        :error="cityError"
      >
        <AutocompleteInput
          :id="ids.city"
          v-model="city"
          :options="cities"
          :invalid="!!cityError"
          :described-by="describedBy"
        />
      </FormField>
      <FormField
        :id="ids.postalCode"
        label="Postal code"
      >
        <input
          :id="ids.postalCode"
          v-model="postalCode"
          type="text"
        >
      </FormField>
      <FormField
        :id="ids.street"
        label="Street"
      >
        <input
          :id="ids.street"
          v-model="street"
          type="text"
        >
      </FormField>
    </FieldGrid>
  </section>
</template>

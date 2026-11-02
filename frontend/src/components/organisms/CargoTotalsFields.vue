<script setup lang="ts">
import { useId } from 'vue'
import FieldGrid from '../atoms/FieldGrid.vue'
import FormField from '../molecules/FormField.vue'

defineProps<{
  fieldErrors: Record<string, string>
  parsedHints: { volume_m3: string | null; gross_weight_kg: string | null; pallet_count: string | null }
}>()

const volume = defineModel<string>('volume', { required: true })
const grossWeight = defineModel<string>('grossWeight', { required: true })
const pallets = defineModel<string>('pallets', { required: true })

const headingId = useId()
</script>

<template>
  <section
    class="panel cargo"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Cargo totals</h3>
    <FieldGrid>
      <FormField
        id="volume_m3"
        v-slot="{ describedBy }"
        label="Volume (m³)"
        :error="fieldErrors.volume_m3"
        :hint="parsedHints.volume_m3"
      >
        <input
          id="volume_m3"
          v-model="volume"
          type="text"
          inputmode="decimal"
          :aria-invalid="fieldErrors.volume_m3 ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
      <FormField
        id="gross_weight_kg"
        v-slot="{ describedBy }"
        label="Gross weight (kg)"
        :error="fieldErrors.gross_weight_kg"
        :hint="parsedHints.gross_weight_kg"
      >
        <input
          id="gross_weight_kg"
          v-model="grossWeight"
          type="text"
          inputmode="decimal"
          :aria-invalid="fieldErrors.gross_weight_kg ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
      <FormField
        id="pallet_count"
        v-slot="{ describedBy }"
        label="Pallets"
        :error="fieldErrors.pallet_count"
        :hint="parsedHints.pallet_count"
      >
        <input
          id="pallet_count"
          v-model="pallets"
          type="text"
          inputmode="decimal"
          :aria-invalid="fieldErrors.pallet_count ? 'true' : undefined"
          :aria-describedby="describedBy"
        >
      </FormField>
    </FieldGrid>
    <p class="field-hint">
      Gross weight is the headline figure — it is what carriers charge on. Pallets is a decimal
      on purpose: the source column is a plain sum.
    </p>
  </section>
</template>

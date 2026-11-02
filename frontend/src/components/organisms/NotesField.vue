<script setup lang="ts">
import { useId } from 'vue'
import FormField from '../molecules/FormField.vue'

const notes = defineModel<string>({ required: true })

const headingId = useId()
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Notes</h3>
    <FormField
      id="notes"
      v-slot="{ describedBy }"
      label="Notes about this delivery"
      hide-label
      hint="Shown in full on the detail view, and marked in the list so you know a comparable is worth opening."
    >
      <textarea
        id="notes"
        v-model="notes"
        rows="6"
        placeholder="What made this delivery unusual, what the price included, anything worth remembering when quoting a similar one."
        :aria-describedby="describedBy"
      />
    </FormField>
  </section>
</template>

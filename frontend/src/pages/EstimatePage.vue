<script setup lang="ts">
import EstimateTemplate from '../components/templates/EstimateTemplate.vue'
import { useEstimatePage } from '../composables/useEstimatePage'

const {
  form,
  fieldErrors,
  status,
  found,
  findError,
  excelFile,
  parsing,
  parseError,
  parsed,
  parsedHints,
  promptText,
  promptEdited,
  promptOutOfDate,
  copyStatus,
  findComparables,
  pickPackingList,
  clearPackingList,
  rebuildPrompt,
  copyPrompt,
} = useEstimatePage()
</script>

<template>
  <EstimateTemplate
    v-model:transport-method="form.transport_method"
    v-model:planned-shipping-date="form.planned_shipping_date"
    v-model:origin-country="form.origin_country"
    v-model:origin-city="form.origin_city"
    v-model:destination-country="form.destination_country"
    v-model:destination-city="form.destination_city"
    v-model:volume="form.volume_m3"
    v-model:gross-weight="form.gross_weight_kg"
    v-model:pallets="form.pallet_count"
    v-model:prompt-text="promptText"
    :field-errors="fieldErrors"
    :status="status"
    :found="found"
    :find-error="findError"
    :excel-file="excelFile"
    :parsing="parsing"
    :parse-error="parseError"
    :parsed="parsed"
    :parsed-hints="parsedHints"
    :prompt-edited="promptEdited"
    :prompt-out-of-date="promptOutOfDate"
    :copy-status="copyStatus"
    @retry="findComparables"
    @pick-packing-list="pickPackingList"
    @clear-packing-list="clearPackingList"
    @rebuild-prompt="rebuildPrompt"
    @copy-prompt="copyPrompt"
  />
</template>

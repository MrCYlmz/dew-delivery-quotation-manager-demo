<script setup lang="ts">
import { computed, useId, useTemplateRef, watch } from 'vue'
import FieldMessage from '../atoms/FieldMessage.vue'
import FileInput from '../atoms/FileInput.vue'
import { excelDownloadUrl } from '../../api/client'
import { formatNumber } from '../../format'
import type { ParsedPackingList } from '../../api/types'

const props = defineProps<{
  existingFilename: string | null
  deliveryId: number | null
  file: File | null
  parsing: boolean
  parseError: string | null
  parsed: ParsedPackingList | null
  disabled?: boolean
  previewOnly?: boolean
}>()

const emit = defineEmits<{
  pick: [file: File]
  clear: []
}>()

const headingId = useId()
const hintId = useId()
const failureId = useId()
const picker = useTemplateRef<InstanceType<typeof FileInput>>('picker')

const describedBy = computed(() => (props.parseError ? `${hintId} ${failureId}` : hintId))

watch(
  () => props.file,
  (file) => {
    if (!file) picker.value?.clear()
  },
)

function onPick(files: File[]) {
  const file = files[0]
  if (file) emit('pick', file)
}
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Packing list (Excel)</h3>

    <p
      v-if="existingFilename && !file"
      class="existing"
    >
      Attached:
      <a
        v-if="deliveryId !== null"
        :href="excelDownloadUrl(deliveryId)"
      >{{ existingFilename }}</a>
      <span v-else>{{ existingFilename }}</span>
      <span class="muted small"> — choose a file below to replace it.</span>
    </p>

    <div class="pick">
      <FileInput
        ref="picker"
        label="Packing list Excel file"
        accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
        :disabled="disabled"
        :described-by="describedBy"
        @pick="onPick"
      />
      <button
        v-if="file"
        type="button"
        class="link"
        @click="emit('clear')"
      >Remove</button>
    </div>
    <FieldMessage
      v-if="previewOnly"
      :id="hintId"
      tone="hint"
    >
      Optional. The totals only fill the fields below; the file is not kept and no delivery is
      created.
    </FieldMessage>
    <FieldMessage
      v-else
      :id="hintId"
      tone="hint"
    >
      Upload the file exactly as the factory sent it — the invoice sheet does not need deleting.
      Both .xlsx and .xls are accepted. Optional: a delivery can be saved with no Excel.
    </FieldMessage>

    <p
      v-if="parsing"
      class="spinner-text"
    >Parsing {{ file?.name }}…</p>

    <div
      v-else-if="parseError"
      :id="failureId"
      class="parse-failed"
      role="alert"
    >
      <strong>That file could not be parsed.</strong>
      <span>{{ parseError }}</span>
      <span
        v-if="previewOnly"
        class="muted small"
      >You can still type the totals below, or fix the file and try again.</span>
      <span
        v-else
        class="muted small"
      >
        Nothing has been saved. You can still save the delivery without the Excel, or fix the file
        and try again.
      </span>
    </div>

    <div
      v-else-if="parsed"
      class="parsed"
    >
      <p class="parsed-head">
        Read <strong>{{ parsed.items.length }}</strong> item rows from sheet
        <strong>{{ parsed.sheet_name }}</strong>.
      </p>
      <ul class="totals">
        <li>Volume <strong>{{ formatNumber(parsed.total_volume_m3) }} m³</strong></li>
        <li>Gross weight <strong>{{ formatNumber(parsed.total_gross_weight_kg) }} kg</strong></li>
        <li>Pallets <strong>{{ formatNumber(parsed.total_pallet_count) }}</strong></li>
      </ul>
      <p
        v-if="previewOnly"
        class="field-hint"
      >These are now in the editable cargo-total fields below.</p>
      <p
        v-else
        class="field-hint"
      >
        These are now in the editable cargo-total fields below — override any of them before
        saving; what you save is what the record keeps.
      </p>

      <template v-if="!previewOnly">
        <p
          v-if="parsed.volume_mismatch_count > 0"
          class="mismatch"
        >
          {{ parsed.volume_mismatch_count }}
          {{ parsed.volume_mismatch_count === 1 ? 'row states' : 'rows state' }} a volume that
          disagrees with L × W × H × pallets by more than 0.01 m³. Highlighted in the item table on
          the detail view. This does not stop you saving.
        </p>
        <p
          v-else
          class="ok-line"
        >Every row with dimensions passes the volume cross-check.</p>
      </template>
    </div>
  </section>
</template>

<style scoped>
.existing {
  margin: 0 0 0.6rem;
}

.pick {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.parse-failed {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  margin-top: 0.7rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius);
  color: var(--danger);
}

.parsed {
  margin-top: 0.8rem;
  padding-top: 0.7rem;
  border-top: 1px solid var(--border);
}

.parsed-head {
  margin: 0 0 0.5rem;
}

.totals {
  display: flex;
  gap: 1.4rem;
  flex-wrap: wrap;
  list-style: none;
  margin: 0 0 0.4rem;
  padding: 0;
  font-size: 0.9rem;
  color: var(--text-muted);
}

.totals strong {
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.mismatch {
  margin: 0.6rem 0 0;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--warn);
  background: var(--warn-soft);
  color: var(--warn);
  border-radius: var(--radius);
  font-size: 0.88rem;
}

.ok-line {
  margin: 0.6rem 0 0;
  color: var(--ok);
  font-size: 0.88rem;
}
</style>

<script setup lang="ts">
import { computed, useId } from 'vue'
import type { CopyStatus, EstimateStatus } from '../../estimate'

const props = defineProps<{
  status: EstimateStatus
  removedCompanyNames: number
  edited: boolean
  outOfDate: boolean
  copyStatus: CopyStatus | null
}>()

const text = defineModel<string>('text', { required: true })

const emit = defineEmits<{
  rebuild: []
  copy: []
}>()

const headingId = useId()
const boxId = useId()
const hintId = useId()

const waitingFor = computed(() => {
  switch (props.status) {
    case 'incomplete':
      return 'The prompt appears once the transport method, origin country and destination country are set.'
    case 'invalid':
      return 'The prompt appears once the cargo totals are fixed.'
    case 'loading':
      return 'The prompt is drafted once the comparables are in.'
    case 'failed':
      return 'The prompt needs the comparables, which could not be loaded.'
    default:
      return 'No prompt without at least one comparable delivery.'
  }
})
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <template v-if="status === 'found'">
      <div class="prompt-head">
        <label
          :id="headingId"
          :for="boxId"
        >Prompt for Claude</label>
        <span
          v-if="edited"
          class="badge"
        >Edited by you: no longer follows the form</span>
      </div>
      <div class="prompt-meta">
        <span class="badge">
          {{ removedCompanyNames }}
          {{ removedCompanyNames === 1 ? 'company name' : 'company names' }} removed from notes
        </span>
        <span class="muted">Customer company is never included.</span>
      </div>
      <div
        v-if="outOfDate"
        class="stale-note"
        role="alert"
      >
        <span>
          You changed the form after editing the prompt. The comparables are up to date; the prompt
          still describes the earlier form.
        </span>
        <button
          type="button"
          @click="emit('rebuild')"
        >Discard my edits and rebuild prompt</button>
      </div>
      <p
        v-else-if="edited"
        class="field-hint edited-note"
      >
        Your edits are kept.
        <button
          type="button"
          class="link"
          @click="emit('rebuild')"
        >Discard my edits and rebuild prompt</button>
      </p>
      <textarea
        :id="boxId"
        v-model="text"
        class="prompt-box"
        spellcheck="false"
        :aria-describedby="hintId"
      />
      <p
        :id="hintId"
        class="field-hint"
      >
        Edit freely before copying. Follows the form until you edit it. Paste it into Claude
        yourself; nothing is sent from the app.
      </p>
    </template>
    <template v-else>
      <h3 :id="headingId">Prompt for Claude</h3>
      <p class="muted">{{ waitingFor }}</p>
    </template>

    <div class="copy-row">
      <button
        type="button"
        class="primary"
        :disabled="status !== 'found'"
        @click="emit('copy')"
      >Copy prompt</button>
      <span
        class="copied"
        role="status"
      >
        <template v-if="copyStatus === 'copied'">Prompt copied. Paste it into Claude.</template>
        <template v-else-if="copyStatus === 'blocked'">
          Copy is blocked in this browser; select the text and copy it by hand.
        </template>
      </span>
    </div>
    <p class="field-hint">
      {{
        status === 'found'
          ? 'Copies the text in the box as it is now, including your edits.'
          : 'Copying is possible once there is a prompt.'
      }}
    </p>
  </section>
</template>

<style scoped>
.prompt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-bottom: 0.5rem;
}

.prompt-head label {
  margin: 0;
  font-size: 0.95rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.prompt-meta {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
}

.prompt-box {
  min-height: 30rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8rem;
  white-space: pre-wrap;
}

.stale-note {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0 0 0.6rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--warn);
  background: var(--warn-soft);
  color: var(--warn);
  border-radius: var(--radius);
  font-size: 0.88rem;
}

.stale-note button {
  align-self: flex-start;
}

.edited-note {
  margin: 0 0 0.4rem;
}

.copy-row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-top: 0.6rem;
}

.copied {
  color: var(--ok);
  font-size: 0.88rem;
}
</style>

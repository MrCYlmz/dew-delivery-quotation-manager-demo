<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { pdfContentUrl } from '../../api/client'
import { formatBytes, formatDateTime } from '../../format'
import type { DeliveryPdf } from '../../api/types'

const props = defineProps<{ deliveryId: number; pdfs: DeliveryPdf[] }>()

const selectedId = ref<number | null>(null)
const headingId = useId()

watch(
  () => props.pdfs,
  (list) => {
    if (list.length === 0) {
      selectedId.value = null
      return
    }
    if (selectedId.value === null || !list.some((p) => p.id === selectedId.value)) {
      selectedId.value = list[0]?.id ?? null
    }
  },
  { immediate: true, deep: true },
)

const selected = computed(() => props.pdfs.find((p) => p.id === selectedId.value) ?? null)
const viewerUrl = computed(() =>
  selected.value ? pdfContentUrl(props.deliveryId, selected.value.id) : null,
)
</script>

<template>
  <section
    class="panel"
    :aria-labelledby="headingId"
  >
    <h3 :id="headingId">Delivery documents ({{ pdfs.length }})</h3>

    <p
      v-if="pdfs.length === 0"
      class="muted small"
    >No PDFs attached to this delivery.</p>

    <div
      v-else
      class="layout"
    >
      <ul
        class="file-list"
        role="tablist"
        aria-label="Attached documents"
      >
        <li
          v-for="pdf in pdfs"
          :key="pdf.id"
        >
          <button
            type="button"
            role="tab"
            class="file-btn"
            :class="{ active: pdf.id === selectedId }"
            :aria-selected="pdf.id === selectedId"
            @click="selectedId = pdf.id"
          >
            <span class="name">{{ pdf.original_filename }}</span>
            <span class="meta">
              {{ formatBytes(pdf.size_bytes) }} · {{ formatDateTime(pdf.uploaded_at) }}
            </span>
          </button>
          <a
            :href="pdfContentUrl(deliveryId, pdf.id)"
            :download="pdf.original_filename"
            class="download"
          >
            Download
          </a>
        </li>
      </ul>

      <div class="viewer">
        <p class="viewer-label small muted">
          Showing <strong>{{ selected?.original_filename }}</strong>
        </p>
        <iframe
          v-if="viewerUrl"
          :key="viewerUrl"
          :src="viewerUrl"
          :title="`Viewer for ${selected?.original_filename}`"
        />
        <p class="small muted fallback">
          If the document does not appear, use its Download link — nothing is extracted from these
          files, they are stored for reference.
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(220px, 300px) 1fr;
  gap: 1rem;
  align-items: start;
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}

.file-list li {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0 0.55rem 0 0;
  border-bottom: 1px solid var(--border);
}

.file-list li:last-child {
  border-bottom: none;
}

.file-btn {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  border: none;
  background: none;
  text-align: left;
  padding: 0.45rem 0.55rem;
  min-width: 0;
}

.file-btn.active {
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

.file-btn .name {
  font-weight: 500;
  word-break: break-word;
}

.file-btn .meta {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.download {
  flex: 0 0 auto;
  font-size: 0.82rem;
}

.viewer-label {
  margin: 0 0 0.35rem;
}

iframe {
  width: 100%;
  height: 70vh;
  min-height: 420px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-alt);
}

.fallback {
  margin: 0.4rem 0 0;
}
</style>

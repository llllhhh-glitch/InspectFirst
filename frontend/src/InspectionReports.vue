<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
const props = defineProps({ inspectionId: String, refreshKey: Number });
const rows = ref([]),
  error = ref(''),
  busy = ref(false),
  loading = ref(false);
let timer,
  request,
  alive = true;
async function load() {
  clearTimeout(timer);
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  try {
    let page = 0,
      more = true,
      found = [];
    while (more) {
      const d = await getJson(`/api/reports?page=${page++}&size=100`, current.signal);
      found.push(...d.items.filter((r) => r.inspectionId === props.inspectionId));
      more = d.hasNext;
    }
    if (alive && request === current) {
      rows.value = found;
      if (!found.length || found.some((r) => ['QUEUED', 'PROCESSING'].includes(r.status)))
        timer = setTimeout(load, 5000);
    }
  } catch (e) {
    if (alive && e.name !== 'AbortError') error.value = e.message;
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
async function retry(row) {
  busy.value = true;
  error.value = '';
  try {
    await writeJson(`/api/reports/${row.id}/retry`, 'POST', {});
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
watch(() => [props.inspectionId, props.refreshKey], load, { immediate: true });
onBeforeUnmount(() => {
  alive = false;
  clearTimeout(timer);
  request?.abort();
});
</script>
<template>
  <section class="panel reports-panel">
    <div class="section-heading">
      <h2>Reports</h2>
      <button class="link" :disabled="loading || busy" @click="load">Refresh</button>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-else-if="!rows.length" class="muted" role="status">
      {{
        loading
          ? 'Checking reports…'
          : 'Your report is being prepared. You can leave this page and return later.'
      }}
    </p>
    <div v-for="row in rows" :key="row.id" class="report-row">
      <strong>Version {{ row.revisionNo }}</strong
      ><span class="status-badge">{{ row.status }}</span
      ><template v-if="row.status === 'READY'"
        ><a :href="`/api/reports/${row.id}/download.pdf`" download="InspectFirst-report.pdf"
          >Download PDF</a
        ><a :href="`/api/reports/${row.id}/download`" download="InspectFirst-report.html"
          >HTML</a
        ></template
      ><button
        v-if="row.status === 'FAILED'"
        class="secondary"
        :disabled="busy"
        @click="retry(row)"
      >
        Retry report
      </button>
    </div>
  </section>
</template>

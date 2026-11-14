<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
import InspectionRecord from './InspectionRecord.vue';
const props = defineProps({ owner: Object });
const emit = defineEmits(['expired']);
const rows = ref([]),
  page = ref(0),
  total = ref(0),
  more = ref(false),
  status = ref(''),
  ownerId = ref(props.owner?.id || ''),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  selected = ref(''),
  reports = ref(false);
let request,
  alive = true;
async function load(reset = false) {
  if (reset) page.value = 0;
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  try {
    const url = reports.value
      ? `/api/ops/reports?page=${page.value}&size=12`
      : `/api/ops/inspections?${new URLSearchParams({ ownerUserId: ownerId.value, status: status.value, page: page.value, size: 12 })}`;
    const d = await getJson(url, current.signal);
    if (alive && request === current) {
      rows.value = d.items;
      total.value = d.total;
      more.value = d.hasNext;
    }
  } catch (e) {
    if (alive && e.name !== 'AbortError') {
      error.value = e.message;
      if (e.status === 401) emit('expired');
    }
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
function mode(value) {
  reports.value = value;
  selected.value = '';
  load(true);
}
async function retry(row) {
  busy.value = true;
  try {
    await writeJson(`/api/reports/${row.id}/retry`, 'POST', {});
    await load();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) emit('expired');
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.owner,
  (u) => {
    ownerId.value = u?.id || '';
    load(true);
  },
  { immediate: true }
);
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
});
</script>
<template>
  <InspectionRecord
    v-if="selected"
    :key="selected"
    :id="selected"
    admin
    @close="selected = ''"
    @expired="emit('expired')"
  />
  <section v-else>
    <div class="page-heading">
      <h1>{{ reports ? 'Report jobs' : 'Inspections' }}</h1>
      <button class="secondary" :disabled="loading" @click="load()">Refresh</button>
    </div>
    <nav class="record-tabs" aria-label="Inspection administration">
      <button :aria-pressed="!reports" @click="mode(false)">Inspections</button
      ><button :aria-pressed="reports" @click="mode(true)">Report jobs</button>
    </nav>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <form v-if="!reports" class="toolbar" @submit.prevent="load(true)">
      <label>User ID<input v-model.trim="ownerId" placeholder="All users" /></label
      ><label
        >Status<select v-model="status" @change="load(true)">
          <option value="">All statuses</option>
          <option>IN_PROGRESS</option>
          <option>COMPLETED</option>
          <option>DELETED</option>
        </select></label
      ><button class="secondary">Filter</button
      ><button
        v-if="ownerId"
        class="link"
        type="button"
        @click="
          ownerId = '';
          load(true);
        "
      >
        Clear user filter
      </button>
    </form>
    <p class="small-note">
      {{
        reports
          ? 'Report downloads require an accessible inspection.'
          : 'Staff views of inspection details are audited. Deleted records retain metadata only.'
      }}
    </p>
    <div v-if="loading" class="state">Loading…</div>
    <div v-else class="library-list">
      <article v-for="r in rows" :key="r.id" class="library-row">
        <div class="library-info">
          <h2>
            {{ reports ? 'Report version ' + r.revisionNo : 'Inspection ' + r.id.slice(0, 8) }}
          </h2>
          <p class="small-note">
            User {{ r.ownerUserId }} · {{ new Date(r.createdAt).toLocaleString('en-GB') }}
          </p>
          <span class="status-badge">{{ r.status }}</span>
        </div>
        <div class="library-actions">
          <template v-if="reports"
            ><a
              v-if="r.status === 'READY'"
              :href="`/api/reports/${r.id}/download.pdf`"
              download="InspectFirst-report.pdf"
              >Download PDF</a
            ><button
              v-if="r.status === 'FAILED'"
              class="secondary"
              :disabled="busy"
              @click="retry(r)"
            >
              Retry report
            </button></template
          ><button
            v-else
            class="secondary"
            :disabled="r.status === 'DELETED'"
            @click="selected = r.id"
          >
            View record
          </button>
        </div>
      </article>
      <p v-if="!rows.length" class="state">No matching records.</p>
    </div>
    <div v-if="total > 12" class="pagination">
      <button
        class="secondary"
        :disabled="!page || loading"
        @click="
          page--;
          load();
        "
      >
        Previous</button
      ><span>{{ page + 1 }}</span
      ><button
        class="secondary"
        :disabled="!more || loading"
        @click="
          page++;
          load();
        "
      >
        Next
      </button>
    </div>
  </section>
</template>

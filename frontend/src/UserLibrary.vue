<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
import Icon from './Icon.vue';
const props = defineProps({ mode: String });
const emit = defineEmits(['navigate', 'expired']);
const rows = ref([]),
  page = ref(0),
  total = ref(0),
  hasNext = ref(false),
  status = ref('');
const loading = ref(false),
  error = ref(''),
  actionError = ref(''),
  removing = ref('');
const saved = computed(() => props.mode === 'saved');
const labels = { IN_PROGRESS: 'In progress', COMPLETED: 'Completed' };
let request,
  alive = true;
function date(value) {
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}
async function load() {
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  rows.value = [];
  try {
    const params = new URLSearchParams({ page: page.value, size: 12 });
    if (!saved.value && status.value) params.set('status', status.value);
    const data = await getJson(
      `${saved.value ? '/api/saved-guides' : '/api/inspections'}?${params}`,
      current.signal
    );
    let items = data.items;
    if (!saved.value) {
      // Names come from the recorded guide snapshot, including archived models.
      items = await Promise.all(
        data.items.map(async (row) => {
          try {
            const detail = await getJson(`/api/inspections/${row.id}`, current.signal);
            return {
              ...row,
              name:
                detail.guideSnapshot?.product?.name ||
                detail.guideSnapshot?.category?.name ||
                'Inspection'
            };
          } catch (e) {
            if (e.name === 'AbortError' || e.status === 401) throw e;
            return { ...row, name: 'Inspection' };
          }
        })
      );
    }
    if (alive && request === current) {
      rows.value = items;
      total.value = data.total;
      hasNext.value = data.hasNext ?? (page.value + 1) * 12 < data.total;
    }
  } catch (e) {
    if (alive && e.name !== 'AbortError') {
      if (e.status === 401) emit('expired');
      else error.value = e.message;
    }
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
function filter(value) {
  status.value = value;
  page.value = 0;
  load();
}
function changePage(delta) {
  page.value += delta;
  load();
}
function openGuide(row) {
  emit('navigate', row.productId ? `/products/${row.productId}` : `/categories/${row.categoryId}`);
}
async function remove(row) {
  if (removing.value) return;
  removing.value = row.guideId;
  actionError.value = '';
  try {
    await writeJson(`/api/saved-guides/${row.guideId}`, 'DELETE');
    if (alive) {
      if (rows.value.length === 1 && page.value > 0) page.value--;
      await load();
    }
  } catch (e) {
    if (alive) {
      if (e.status === 401) emit('expired');
      else actionError.value = e.message;
    }
  } finally {
    if (alive) removing.value = '';
  }
}
onMounted(load);
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
});
</script>
<template>
  <section class="library-page">
    <div class="page-heading">
      <h1>{{ saved ? 'Saved guides' : 'My inspections' }}</h1>
      <span v-if="!loading && !error" class="result-count"
        >{{ total }}
        {{ saved ? (total === 1 ? 'guide' : 'guides') : total === 1 ? 'record' : 'records' }}</span
      >
    </div>
    <nav v-if="!saved" class="record-tabs" aria-label="Inspection status">
      <button
        v-for="tab in [
          { value: '', label: 'All' },
          { value: 'IN_PROGRESS', label: 'In progress' },
          { value: 'COMPLETED', label: 'Completed' }
        ]"
        :key="tab.value"
        :aria-pressed="status === tab.value"
        @click="filter(tab.value)"
      >
        {{ tab.label }}
      </button>
    </nav>
    <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
    <div v-if="loading" class="state" role="status">Loading…</div>
    <section v-else-if="error" class="state" role="alert">
      <p>{{ error }}</p>
      <button class="secondary" @click="load">Retry</button>
    </section>
    <div v-else-if="rows.length" class="library-list">
      <article v-for="row in rows" :key="row.guideId || row.id" class="library-row">
        <div class="library-icon"><Icon :name="saved ? 'heart' : 'clipboard'" :size="24" /></div>
        <div class="library-info">
          <h2>{{ row.name }}</h2>
          <template v-if="saved"
            ><p class="muted">Saved {{ date(row.createdAt) }}</p>
            <span v-if="!row.available" class="status-badge unavailable">Unavailable</span
            ><span v-else-if="row.updated" class="status-badge">Updated guide</span>
            <p v-if="!row.available && row.withdrawalReason" class="small-note">
              {{ row.withdrawalReason }}
            </p></template
          >
          <template v-else
            ><p class="muted">{{ date(row.updatedAt) }}</p>
            <span class="status-badge" :class="{ complete: row.status === 'COMPLETED' }">{{
              labels[row.status] || row.status
            }}</span></template
          >
        </div>
        <div class="library-actions">
          <template v-if="saved"
            ><button class="secondary" :disabled="!row.available" @click="openGuide(row)">
              View guide</button
            ><button
              class="icon-button"
              :aria-label="`Remove ${row.name} from saved guides`"
              :disabled="Boolean(removing)"
              @click="remove(row)"
            >
              <Icon name="close" :size="18" /></button></template
          ><button v-else class="secondary" @click="emit('navigate', `/inspections/${row.id}`)">
            View record
          </button>
        </div>
      </article>
    </div>
    <section v-else class="state empty-state">
      <Icon :name="saved ? 'heart' : 'clipboard'" :size="36" />
      <h2>
        {{ saved ? 'No saved guides' : status ? 'No matching inspections' : 'No inspections yet' }}
      </h2>
      <p class="muted">
        {{ saved ? 'Save a guide to find it here.' : 'Your inspection records will appear here.' }}
      </p>
      <button class="secondary" @click="emit('navigate', '/')">Browse guides</button>
    </section>
    <div v-if="!loading && !error && total > 12" class="pagination">
      <button class="secondary" :disabled="page === 0" @click="changePage(-1)">Previous</button
      ><span>{{ page + 1 }}</span
      ><button class="secondary" :disabled="!hasNext" @click="changePage(1)">Next</button>
    </div>
  </section>
</template>

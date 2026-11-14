<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
const emit = defineEmits(['expired', 'inspections']);
const rows = ref([]),
  page = ref(0),
  total = ref(0),
  q = ref(''),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  selected = ref(null);
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
    const d = await getJson(
      `/api/ops/users?${new URLSearchParams({ q: q.value, page: page.value, size: 12 })}`,
      current.signal
    );
    if (alive && request === current) {
      rows.value = d.items;
      total.value = d.total;
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      error.value = e.message;
      if (e.status === 401) emit('expired');
    }
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
async function status() {
  busy.value = true;
  error.value = '';
  try {
    await writeJson(`/api/ops/users/${selected.value.id}/status`, 'PATCH', {
      status: selected.value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    });
    selected.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) emit('expired');
  } finally {
    busy.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
});
</script>
<template>
  <section>
    <div class="page-heading">
      <h1>Users</h1>
      <span class="muted">{{ total }} accounts</span>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <form class="toolbar" @submit.prevent="load(true)">
      <label
        >Find user<input
          v-model.trim="q"
          maxlength="100"
          type="search"
          placeholder="Name or email" /></label
      ><button class="secondary" :disabled="loading || busy">Search</button>
    </form>
    <section v-if="selected" class="panel confirmation">
      <h2>{{ selected.status === 'ACTIVE' ? 'Disable' : 'Enable' }} {{ selected.displayName }}?</h2>
      <p>
        {{
          selected.status === 'ACTIVE'
            ? 'They will lose access immediately. Their saved guides and inspection history will be retained.'
            : 'They will be able to sign in again.'
        }}
      </p>
      <div class="action-row">
        <button class="primary" :disabled="busy" @click="status">Confirm</button
        ><button class="secondary" :disabled="busy" @click="selected = null">Cancel</button>
      </div>
    </section>
    <div v-if="loading" class="state">Loading…</div>
    <div v-else class="library-list">
      <article v-for="u in rows" :key="u.id" class="library-row">
        <div class="library-info">
          <h2>{{ u.displayName }}</h2>
          <p>{{ u.email }}</p>
          <p class="small-note">
            {{ u.role }} · {{ u.status }} · Joined
            {{ new Date(u.createdAt).toLocaleDateString('en-GB') }}
          </p>
        </div>
        <div class="library-actions">
          <button class="secondary" :disabled="busy" @click="emit('inspections', u)">
            View inspections</button
          ><button
            v-if="u.role !== 'ADMIN'"
            class="secondary"
            :disabled="busy"
            @click="selected = u"
          >
            {{ u.status === 'ACTIVE' ? 'Disable account' : 'Enable account' }}</button
          ><span v-else class="small-note">Sole administrator</span>
        </div>
      </article>
      <p v-if="!rows.length" class="state">No users found.</p>
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
        :disabled="(page + 1) * 12 >= total || loading"
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

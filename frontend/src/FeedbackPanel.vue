<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson, requestIdentity } from './api.js';
const props = defineProps({ admin: Boolean, context: Object });
const emit = defineEmits(['expired', 'navigate', 'dirty']);
const rows = ref([]),
  detail = ref(null),
  total = ref(0),
  page = ref(0),
  more = ref(false),
  status = ref(''),
  type = ref(''),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  compose = ref(false),
  reply = ref(''),
  nextStatus = ref('');
const form = ref({ type: 'APP_PROBLEM', subject: '', body: '' }),
  newKey = requestIdentity();
const prefix = computed(() => (props.admin ? '/api/ops/feedback' : '/api/feedback'));
const transitions = {
  OPEN: ['IN_REVIEW', 'NEED_DETAILS', 'CLOSED'],
  IN_REVIEW: ['NEED_DETAILS', 'RESOLVED', 'CLOSED'],
  NEED_DETAILS: ['IN_REVIEW', 'CLOSED'],
  RESOLVED: ['IN_REVIEW', 'CLOSED'],
  CLOSED: []
};
const types = {
  INACCURATE: 'Inaccurate guide',
  MISSING_PRODUCT: 'Missing product',
  APP_PROBLEM: 'App problem'
};
const stateLabel = (s) =>
  s
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^./, (c) => c.toUpperCase());
watch(
  () => !!(form.value.subject || form.value.body || reply.value),
  (v) => emit('dirty', v)
);
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
      `${prefix.value}?${new URLSearchParams({ status: status.value, type: type.value, page: page.value, size: 12 })}`,
      current.signal
    );
    if (alive && request === current) {
      rows.value = d.items;
      total.value = d.total;
      more.value = d.hasNext;
    }
  } catch (e) {
    fail(e);
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
function fail(e) {
  if (alive && e.name !== 'AbortError') {
    error.value =
      e.status === 409
        ? 'This feedback changed. Reload the conversation before sending again.'
        : e.message;
    if (e.status === 401) emit('expired');
  }
}
async function open(id) {
  busy.value = true;
  error.value = '';
  try {
    const d = await getJson(`${prefix.value}/${id}`);
    if (alive) {
      detail.value = d;
      reply.value = '';
      nextStatus.value = transitions[d.status]?.[0] || '';
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function create() {
  busy.value = true;
  error.value = '';
  notice.value = '';
  const payload = { ...form.value, ...(props.context || {}) };
  try {
    const d = await writeJson('/api/feedback', 'POST', { ...payload, requestKey: newKey(payload) });
    if (alive) {
      compose.value = false;
      detail.value = d;
      form.value = { type: 'APP_PROBLEM', subject: '', body: '' };
      notice.value = 'Feedback sent';
      await load(true);
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function send() {
  busy.value = true;
  error.value = '';
  try {
    const d = await writeJson(
      props.admin
        ? `${prefix.value}/${detail.value.id}/status`
        : `${prefix.value}/${detail.value.id}/replies`,
      props.admin ? 'PATCH' : 'POST',
      props.admin
        ? {
            status: nextStatus.value,
            reply: reply.value,
            expectedVersion: detail.value.lockVersion
          }
        : { body: reply.value, expectedVersion: detail.value.lockVersion }
    );
    if (alive) {
      detail.value = d;
      reply.value = '';
      nextStatus.value = transitions[d.status]?.[0] || '';
      await load();
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function change(n) {
  page.value += n;
  load();
}
onMounted(() => {
  if (!props.admin && props.context?.productId) {
    compose.value = true;
    form.value.type = 'INACCURATE';
  }
  load();
});
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
  emit('dirty', false);
});
</script>
<template>
  <section class="feedback-page">
    <div class="page-heading">
      <h1>{{ admin ? 'Feedback inbox' : 'Feedback' }}</h1>
      <button
        v-if="!admin && !compose"
        class="primary"
        @click="
          compose = true;
          detail = null;
        "
      >
        Send feedback
      </button>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success-note" role="status">{{ notice }}</p>
    <form v-if="compose && !admin" class="panel workflow-form" @submit.prevent="create">
      <h2>{{ context?.productId ? 'Report a guide issue' : 'Send feedback' }}</h2>
      <fieldset :disabled="busy">
        <label
          >Type<select v-model="form.type">
            <option v-for="(label, k) in types" :key="k" :value="k">{{ label }}</option>
          </select></label
        >
        <p v-if="context?.productId" class="small-note">
          This feedback includes the guide you were viewing.
        </p>
        <label>Subject<input v-model.trim="form.subject" required maxlength="160" /></label
        ><label
          >Details<textarea v-model.trim="form.body" required rows="4" maxlength="5000" />
        </label>
        <div class="action-row">
          <button class="primary">{{ busy ? 'Sending…' : 'Send feedback' }}</button
          ><button class="secondary" type="button" @click="compose = false">Cancel</button>
        </div>
      </fieldset>
    </form>
    <section v-if="detail" class="panel feedback-thread">
      <div class="section-heading">
        <h2>{{ detail.subject }}</h2>
        <button class="link" :disabled="busy" @click="detail = null">Close conversation</button>
      </div>
      <p class="small-note">
        {{ types[detail.type] }} · {{ stateLabel(detail.status) }} · Reference
        {{ detail.id.slice(0, 8) }}
      </p>
      <p class="thread-body">{{ detail.body }}</p>
      <p v-if="detail.productId" class="small-note">
        <a :href="`#/products/${detail.productId}`">Related guide ↗</a>
      </p>
      <article v-for="r in detail.replies" :key="r.id" class="thread-reply">
        <strong>{{
          r.actorRole === 'ADMIN' ? 'InspectFirst team' : admin ? 'Customer' : 'You'
        }}</strong
        ><span class="small-note"> {{ new Date(r.createdAt).toLocaleString('en-GB') }}</span>
        <p>{{ r.body }}</p>
      </article>
      <form v-if="detail.status !== 'CLOSED'" class="workflow-form" @submit.prevent="send">
        <fieldset :disabled="busy">
          <label v-if="admin"
            >Update status<select v-model="nextStatus" required>
              <option v-for="s in transitions[detail.status]" :key="s" :value="s">
                {{ stateLabel(s) }}
              </option>
            </select></label
          ><label
            >{{ admin ? 'Public response' : 'Reply'
            }}<textarea v-model.trim="reply" required maxlength="5000" rows="3" />
          </label>
          <div class="action-row">
            <button class="primary">
              {{ busy ? 'Saving…' : admin ? 'Update feedback' : 'Send reply' }}</button
            ><button type="button" class="secondary" @click="open(detail.id)">
              Reload conversation
            </button>
          </div>
        </fieldset>
      </form>
      <p v-else class="small-note">
        This conversation is closed. Send new feedback if you need more help.
      </p>
    </section>
    <div class="toolbar">
      <label
        >Status<select v-model="status" @change="load(true)">
          <option value="">All statuses</option>
          <option v-for="s in Object.keys(transitions)" :key="s" :value="s">
            {{ stateLabel(s) }}
          </option>
        </select></label
      ><label
        >Type<select v-model="type" @change="load(true)">
          <option value="">All types</option>
          <option v-for="(label, k) in types" :key="k" :value="k">{{ label }}</option>
        </select></label
      ><button class="secondary" :disabled="loading || busy" @click="load()">Refresh</button>
    </div>
    <div v-if="loading" class="state" role="status">Loading…</div>
    <div v-else class="library-list">
      <button
        v-for="r in rows"
        :key="r.id"
        class="library-row list-button"
        :disabled="busy"
        @click="open(r.id)"
      >
        <div class="library-info">
          <h2>{{ r.subject }}</h2>
          <p class="small-note">
            {{ types[r.type] }} · {{ new Date(r.updatedAt).toLocaleDateString('en-GB') }}
          </p>
        </div>
        <span class="status-badge">{{ stateLabel(r.status) }}</span>
      </button>
      <p v-if="!rows.length" class="state">No feedback yet.</p>
    </div>
    <div v-if="total > 12" class="pagination">
      <button class="secondary" :disabled="!page || loading" @click="change(-1)">Previous</button
      ><span>{{ page + 1 }}</span
      ><button class="secondary" :disabled="!more || loading" @click="change(1)">Next</button>
    </div>
  </section>
</template>

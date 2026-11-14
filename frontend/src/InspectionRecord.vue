<script setup>
import { confirmDiscard } from './confirm.js';
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson, uploadPhoto } from './api.js';
import Icon from './Icon.vue';
import CheckExamples from './CheckExamples.vue';
import InspectionReports from './InspectionReports.vue';
const props = defineProps({ id: String, admin: Boolean });
const emit = defineEmits(['navigate', 'expired', 'dirty', 'close']);
const record = ref(null),
  loading = ref(false),
  error = ref(''),
  actionError = ref(''),
  busy = ref(false),
  notice = ref(''),
  confirm = ref(''),
  revisions = ref([]),
  selectedRevision = ref('');
const form = ref(null),
  baseline = ref('');
const labels = {
  UNCHECKED: 'Unchecked',
  NO_ISSUE_OBSERVED: 'No issue observed',
  ISSUE_OBSERVED: 'Issue observed',
  UNABLE_TO_ASSESS: 'Unable to assess'
};
const prefix = computed(() => (props.admin ? '/api/ops/inspections' : '/api/inspections'));
const editable = computed(
  () => record.value?.status === 'IN_PROGRESS' && !props.admin && !selectedRevision.value
);
const visibleSummary = computed(() =>
  form.value
    ? Object.fromEntries(
        Object.keys(labels).map((key) => [
          key,
          form.value.results.filter((r) => r.status === key).length
        ])
      )
    : {}
);
const dirty = computed(() => !!form.value && JSON.stringify(form.value) !== baseline.value);
watch(dirty, (value) => {
  emit('dirty', value);
  if (value) notice.value = '';
});
let request,
  alive = true,
  completionKey = crypto.randomUUID();
function apply(data) {
  record.value = data;
  form.value = {
    userRequirement: data.userRequirement || '',
    sellerClaim: data.sellerClaim || '',
    results: data.items.map((i) => ({ itemId: i.id, status: i.resultStatus, note: i.note || '' }))
  };
  baseline.value = JSON.stringify(form.value);
  emit('dirty', false);
}
async function load() {
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  try {
    const [data, history] = await Promise.all([
      getJson(`${prefix.value}/${props.id}`, current.signal),
      getJson(`${prefix.value}/${props.id}/revisions`, current.signal)
    ]);
    if (alive && request === current) {
      apply(data);
      revisions.value = history;
      selectedRevision.value = '';
      confirm.value = '';
      completionKey = crypto.randomUUID();
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
async function saveInternal() {
  const data = await writeJson(`/api/inspections/${props.id}`, 'PUT', {
    ...form.value,
    expectedVersion: record.value.lockVersion
  });
  if (alive) apply(data);
}
async function act(action) {
  if (busy.value) return;
  busy.value = true;
  actionError.value = '';
  notice.value = '';
  try {
    if (action === 'save') {
      await saveInternal();
      notice.value = 'Saved';
    }
    if (action === 'complete') {
      if (dirty.value) await saveInternal();
      await writeJson(`/api/inspections/${props.id}/complete`, 'POST', {
        expectedVersion: record.value.lockVersion,
        requestKey: completionKey
      });
      await load();
      notice.value = 'Inspection completed. Your observations have been saved.';
    }
    if (action === 'reopen') {
      const data = await writeJson(`/api/inspections/${props.id}/reopen`, 'POST', {
        expectedVersion: record.value.lockVersion
      });
      if (alive) {
        apply(data);
        completionKey = crypto.randomUUID();
        confirm.value = '';
        notice.value = 'Reopened. Previous completed versions are retained.';
      }
    }
    if (action === 'delete') {
      await writeJson(
        `/api/inspections/${props.id}?expectedVersion=${record.value.lockVersion}`,
        'DELETE'
      );
      if (alive) {
        emit('dirty', false);
        emit('navigate', '/my-inspections');
      }
    }
  } catch (e) {
    if (alive) {
      actionError.value =
        e.status === 409
          ? 'This record changed. Your edits are still here. Reload before trying again.'
          : e.message;
      if (e.status === 401) emit('expired');
    }
  } finally {
    if (alive) busy.value = false;
  }
}
async function revisionChanged() {
  if (!selectedRevision.value) {
    await load();
    return;
  }
  busy.value = true;
  actionError.value = '';
  try {
    const v = await getJson(`${prefix.value}/${props.id}/revisions/${selectedRevision.value}`);
    if (alive) apply(v.snapshot);
  } catch (e) {
    actionError.value = e.message;
    if (e.status === 401) emit('expired');
  } finally {
    busy.value = false;
  }
}
async function photo(event, item) {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    actionError.value = 'Choose a JPEG or PNG up to 5 MB.';
    return;
  }
  busy.value = true;
  actionError.value = '';
  try {
    if (dirty.value) await saveInternal();
    await uploadPhoto(
      `/api/inspections/${props.id}/items/${item.id}/attachments?expectedVersion=${record.value.lockVersion}`,
      file
    );
    await load();
    notice.value = 'Photo added';
  } catch (e) {
    actionError.value = e.message;
    if (e.status === 401) emit('expired');
  } finally {
    busy.value = false;
  }
}
async function removePhoto(photo) {
  busy.value = true;
  actionError.value = '';
  try {
    if (dirty.value) await saveInternal();
    await writeJson(
      `/api/attachments/${photo.id}?expectedVersion=${record.value.lockVersion}`,
      'DELETE'
    );
    await load();
  } catch (e) {
    actionError.value = e.message;
    if (e.status === 401) emit('expired');
  } finally {
    busy.value = false;
  }
}
async function reload() {
  if (!dirty.value || (await confirmDiscard('Discard unsaved changes and reload?'))) load();
}
function beforeUnload(e) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = '';
  }
}
onMounted(() => {
  load();
  window.addEventListener('beforeunload', beforeUnload);
});
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
  emit('dirty', false);
  window.removeEventListener('beforeunload', beforeUnload);
});
</script>
<template>
  <section class="record-page">
    <div class="breadcrumb">
      <button @click="admin ? emit('close') : emit('navigate', '/my-inspections')">
        {{ admin ? 'All inspections' : 'My inspections' }}</button
      ><Icon name="chevron" :size="13" /><span>Record</span>
    </div>
    <div v-if="loading" class="state" role="status">Loading…</div>
    <section v-else-if="error" class="state" role="alert">
      <p>{{ error }}</p>
      <button class="secondary" @click="load">Retry</button>
    </section>
    <template v-else-if="record && form"
      ><div class="page-heading">
        <h1>
          {{
            record.guideSnapshot?.product?.name ||
            record.guideSnapshot?.category?.name ||
            'Inspection'
          }}
        </h1>
        <span class="status-badge" :class="{ complete: record.status === 'COMPLETED' }">{{
          record.status === 'COMPLETED' ? 'Completed' : 'In progress'
        }}</span>
      </div>
      <p v-if="notice" class="success-note" role="status">{{ notice }}</p>
      <p v-if="actionError" class="form-error" role="alert">
        {{ actionError }} <button class="link" @click="reload">Reload</button>
      </p>
      <div class="record-summary">
        <span v-for="(label, key) in labels" :key="key"
          ><strong>{{ visibleSummary[key] ?? 0 }}</strong
          >{{ label }}</span
        >
      </div>
      <div class="action-row" v-if="!admin && !selectedRevision">
        <template v-if="editable"
          ><button class="primary" :disabled="busy || !dirty" @click="act('save')">
            {{ busy ? 'Please wait…' : 'Save changes' }}</button
          ><button class="secondary" :disabled="busy" @click="confirm = 'complete'">
            Complete inspection</button
          ><span v-if="dirty" class="small-note">Unsaved changes</span></template
        ><button v-else class="secondary" :disabled="busy" @click="confirm = 'reopen'">
          Reopen inspection</button
        ><button class="link danger" :disabled="busy" @click="confirm = 'delete'">
          Delete record
        </button>
      </div>
      <section v-if="confirm" class="panel confirmation">
        <h2>
          {{
            confirm === 'complete'
              ? 'Complete this inspection?'
              : confirm === 'reopen'
                ? 'Reopen this inspection?'
                : 'Delete this record?'
          }}
        </h2>
        <p>
          {{
            confirm === 'complete'
              ? 'Unchecked and inconclusive items remain visible; they are not passes. Unsaved changes will be saved first.'
              : confirm === 'delete'
                ? 'The record and its reports will no longer be available to you. Audit history may be retained.'
                : 'You can edit a new working copy. Earlier completion versions stay unchanged.'
          }}
        </p>
        <div class="action-row">
          <button class="primary" :disabled="busy" @click="act(confirm)">
            Confirm {{ confirm }}</button
          ><button class="secondary" :disabled="busy" @click="confirm = ''">Cancel</button>
        </div>
      </section>
      <label v-if="revisions.length" class="revision-picker"
        >Version<select
          v-model="selectedRevision"
          :disabled="busy || dirty"
          @change="revisionChanged"
        >
          <option value="">Current record</option>
          <option v-for="v in revisions" :value="v.id" :key="v.id">
            Completed version {{ v.revisionNo }}
          </option>
        </select></label
      >
      <fieldset :disabled="!editable || busy" class="record-context panel workflow-form">
        <label
          >My requirements<textarea
            v-model="form.userRequirement"
            rows="2"
            maxlength="3000"
          /></label
        ><label
          >Seller’s description<textarea v-model="form.sellerClaim" rows="2" maxlength="3000" />
        </label>
      </fieldset>
      <section class="panel checks-panel">
        <div class="section-heading">
          <h2>{{ editable ? 'Your checklist' : 'Recorded checks' }}</h2>
          <span>Guide v{{ record.guideSnapshot?.version }}</span>
        </div>
        <details
          v-for="(item, index) in record.items"
          :key="item.id"
          class="check-item"
          :open="index === 0"
        >
          <summary>
            <span class="step-number">{{ item.sortOrder }}</span
            ><span>{{ item.contentSnapshot?.title }}</span
            ><span class="status-badge">{{ labels[form.results[index].status] }}</span
            ><Icon name="chevron" class="expand-icon" :size="17" />
          </summary>
          <div class="check-content">
            <ol>
              <li v-for="step in item.contentSnapshot?.steps" :key="step">{{ step }}</li>
            </ol>
            <CheckExamples :images="item.contentSnapshot?.images || []" />
            <div class="observations">
              <section>
                <h3>Expected</h3>
                <p>{{ item.contentSnapshot?.expectedObservation }}</p>
              </section>
              <section>
                <h3>Watch for</h3>
                <p>{{ item.contentSnapshot?.abnormalSigns }}</p>
              </section>
            </div>
            <p class="small-note">{{ item.contentSnapshot?.limitations }}</p>
            <fieldset :disabled="!editable || busy" class="workflow-form">
              <label :for="`result-${item.id}`"
                >Result<select :id="`result-${item.id}`" v-model="form.results[index].status">
                  <option v-for="(label, key) in labels" :key="key" :value="key">
                    {{ label }}
                  </option>
                </select></label
              ><label
                >Notes<textarea
                  v-model="form.results[index].note"
                  rows="2"
                  maxlength="3000"
                  placeholder="What did you observe?"
                />
              </label>
            </fieldset>
            <div class="attachment-links">
              <div v-for="p in item.attachments" :key="p.id" class="action-row">
                <a :href="`/api/attachments/${p.id}`" target="_blank" rel="noopener noreferrer"
                  >Photo {{ item.attachments.indexOf(p) + 1 }} ↗</a
                ><button v-if="editable" class="link" :disabled="busy" @click="removePhoto(p)">
                  Remove photo
                </button>
              </div>
            </div>
            <label v-if="editable && (item.attachments?.length || 0) < 5" class="photo-upload"
              >Add photo <span class="muted">JPEG/PNG, up to 5 MB</span
              ><input
                type="file"
                accept="image/jpeg,image/png"
                :disabled="busy"
                @change="photo($event, item)"
            /></label>
          </div>
        </details>
      </section>
      <InspectionReports
        v-if="!admin && record.latestRevisionNo > 0"
        :inspection-id="id"
        :refresh-key="record.lockVersion"
      />
    </template>
  </section>
</template>

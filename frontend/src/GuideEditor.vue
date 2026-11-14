<script setup>
import { confirmDiscard } from './confirm.js';
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
import CheckExamples from './CheckExamples.vue';
const props = defineProps({ product: Object, guideId: String });
const emit = defineEmits(['close', 'expired', 'changed', 'dirty']);
const versions = ref([]),
  version = ref(null),
  form = ref(null),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  baseline = ref(''),
  withdrawReason = ref(''),
  showWithdraw = ref(false),
  containerId = ref(props.guideId || '');
const editable = computed(() => version.value?.status === 'DRAFT');
const dirty = computed(() => form.value && JSON.stringify(form.value) !== baseline.value);
watch(dirty, (v) => {
  emit('dirty', !!v);
  if (v) notice.value = '';
});
const blankCheck = () => ({
  title: '',
  purpose: '',
  stepsText: '',
  expectedObservation: '',
  abnormalSigns: '',
  limitations: '',
  sources: [{ title: '', url: '', publisher: '' }],
  images: []
});
let alive = true;
function set(data) {
  version.value = data;
  form.value = {
    title: data.title || '',
    tools: data.tools || '',
    reviewedOn: data.reviewedOn || new Date().toLocaleDateString('en-CA'),
    contentNote: data.contentNote || '',
    items: (data.items || []).map((i) => ({
      ...i,
      stepsText: i.steps.join('\n'),
      sources: i.sources.map((s) => ({ ...s })),
      images: (i.images || []).map((im) => ({ ...im }))
    }))
  };
  baseline.value = JSON.stringify(form.value);
}
function fail(e) {
  if (alive) {
    error.value = e.message;
    if (e.status === 401) emit('expired');
  }
}
async function list() {
  if (containerId.value)
    versions.value = await getJson(`/api/ops/content/guides/${containerId.value}/versions`);
}
async function read(id) {
  if (dirty.value && !(await confirmDiscard('Discard unsaved guide changes?'))) return;
  busy.value = true;
  error.value = '';
  try {
    const d = await getJson(`/api/ops/content/versions/${id}`);
    if (alive) set(d);
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function init() {
  busy.value = true;
  try {
    await list();
    if (versions.value.length) {
      const draft = versions.value.find((v) => v.status === 'DRAFT');
      set(await getJson(`/api/ops/content/versions/${(draft || versions.value[0]).id}`));
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function newDraft() {
  busy.value = true;
  error.value = '';
  try {
    const previous = form.value ? structuredClone(JSON.parse(JSON.stringify(form.value))) : null;
    const d = await writeJson('/api/ops/content/guides', 'POST', { productId: props.product.id });
    containerId.value = d.id;
    set(d);
    if (previous) {
      form.value = previous;
      baseline.value = '';
    } else form.value.items = [blankCheck()];
    await list();
    emit('changed');
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function payload() {
  return {
    expectedVersion: version.value.editVersion,
    title: form.value.title,
    tools: form.value.tools,
    reviewedOn: form.value.reviewedOn,
    contentNote: form.value.contentNote,
    items: form.value.items.map((i) => ({
      title: i.title,
      purpose: i.purpose,
      steps: i.stepsText
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean),
      expectedObservation: i.expectedObservation,
      abnormalSigns: i.abnormalSigns,
      limitations: i.limitations,
      sources: i.sources.map(({ title, url, publisher }) => ({ title, url, publisher })),
      images: i.images.map(({ url, alt, caption, kind, sourceUrl, sourceTitle }) => ({
        url,
        alt,
        caption,
        kind,
        sourceUrl,
        sourceTitle
      }))
    }))
  };
}
async function save() {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    const d = await writeJson(
      `/api/ops/content/versions/${version.value.versionId}`,
      'PUT',
      payload()
    );
    if (alive) {
      set(d);
      await list();
      notice.value = 'Draft saved';
      emit('changed');
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function publish() {
  busy.value = true;
  error.value = '';
  try {
    const d = await writeJson(
      `/api/ops/content/versions/${version.value.versionId}/publish`,
      'POST',
      { expectedVersion: version.value.editVersion }
    );
    if (alive) {
      set(d);
      await list();
      notice.value = 'Guide published';
      emit('changed');
    }
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function withdraw() {
  busy.value = true;
  error.value = '';
  try {
    await writeJson(`/api/ops/content/guides/${containerId.value}/withdraw`, 'POST', {
      reason: withdrawReason.value
    });
    showWithdraw.value = false;
    await init();
    notice.value = 'Guide withdrawn from public browsing';
    emit('changed');
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function close() {
  if (!dirty.value || (await confirmDiscard('Discard unsaved guide changes?'))) emit('close');
}
onMounted(init);
onBeforeUnmount(() => {
  alive = false;
  emit('dirty', false);
});
</script>
<template>
  <section class="guide-editor">
    <div class="breadcrumb">
      <button @click="close">Content</button><span> / {{ product.name }}</span>
    </div>
    <div class="page-heading">
      <h1>Guide editor</h1>
      <span v-if="version" class="status-badge">{{ version.status }} · v{{ version.version }}</span>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success-note" role="status">{{ notice }}</p>
    <div class="toolbar">
      <label v-if="versions.length"
        >Version<select
          :value="version?.versionId"
          :disabled="busy"
          @change="read($event.target.value)"
        >
          <option v-for="v in versions" :value="v.id" :key="v.id">
            v{{ v.versionNo }} · {{ v.status }}
          </option>
        </select></label
      ><button
        v-if="!versions.some((v) => v.status === 'DRAFT')"
        class="primary"
        :disabled="busy"
        @click="newDraft"
      >
        {{ version ? 'Create new draft' : 'Create first draft' }}</button
      ><button
        v-if="containerId && versions.some((v) => v.status === 'PUBLISHED')"
        class="link danger"
        :disabled="busy"
        @click="showWithdraw = !showWithdraw"
      >
        Withdraw guide
      </button>
    </div>
    <form v-if="showWithdraw" class="panel workflow-form" @submit.prevent="withdraw">
      <label
        >Why is this guide being withdrawn?<textarea
          v-model.trim="withdrawReason"
          required
          maxlength="600"
          rows="2"
        />
      </label>
      <p class="small-note">
        This removes the guide from public browsing. Existing inspection records remain unchanged.
      </p>
      <button class="primary" :disabled="busy">Confirm withdrawal</button>
    </form>
    <form v-if="form" class="workflow-form" @submit.prevent="save">
      <fieldset :disabled="!editable || busy">
        <section class="panel">
          <label>Guide title<input v-model.trim="form.title" maxlength="200" required /></label
          ><label
            >Preparation and tools<textarea
              v-model.trim="form.tools"
              maxlength="600"
              rows="2"
              required
            /></label
          ><label>Reviewed on<input type="date" v-model="form.reviewedOn" required /></label
          ><label
            >Scope and limitations<textarea
              v-model.trim="form.contentNote"
              maxlength="1200"
              rows="3"
              required
            />
          </label>
        </section>
        <section v-for="(item, index) in form.items" :key="index" class="panel editor-check">
          <div class="section-heading">
            <h2>Check {{ index + 1 }}</h2>
            <div class="action-row">
              <button
                type="button"
                class="link"
                :disabled="index === 0"
                @click="
                  [form.items[index - 1], form.items[index]] = [
                    form.items[index],
                    form.items[index - 1]
                  ]
                "
              >
                Move up</button
              ><button
                type="button"
                class="link danger"
                :disabled="form.items.length === 1"
                @click="form.items.splice(index, 1)"
              >
                Remove check
              </button>
            </div>
          </div>
          <label>Title<input v-model.trim="item.title" maxlength="200" required /></label
          ><label>Purpose<input v-model.trim="item.purpose" maxlength="600" required /></label
          ><label
            >Steps <span class="muted">One per line</span
            ><textarea v-model="item.stepsText" rows="4" maxlength="2000" required />
          </label>
          <div class="form-columns">
            <label
              >Expected observation<textarea
                v-model.trim="item.expectedObservation"
                maxlength="1200"
                required
              /></label
            ><label
              >Warning signs<textarea v-model.trim="item.abnormalSigns" maxlength="1200" required />
            </label>
          </div>
          <label
            >What this cannot confirm<textarea
              v-model.trim="item.limitations"
              maxlength="1200"
              required
            />
          </label>
          <h3>Sources</h3>
          <div v-for="(s, n) in item.sources" :key="n" class="source-editor">
            <label>Source title<input v-model.trim="s.title" maxlength="200" required /></label
            ><label>URL<input v-model.trim="s.url" type="url" maxlength="1000" required /></label
            ><label>Publisher<input v-model.trim="s.publisher" maxlength="80" required /></label
            ><button
              type="button"
              class="link"
              :disabled="item.sources.length === 1"
              @click="item.sources.splice(n, 1)"
            >
              Remove source
            </button>
          </div>
          <button
            type="button"
            class="secondary"
            :disabled="item.sources.length >= 10"
            @click="item.sources.push({ title: '', url: '', publisher: '' })"
          >
            Add source
          </button>
          <details class="editor-image-details">
            <summary>Example images ({{ item.images.length }})</summary>
            <div v-for="(im, n) in item.images" :key="n" class="source-editor">
              <label
                >Local example image path<input
                  v-model.trim="im.url"
                  placeholder="/images/checks/example.svg"
                  required
                  maxlength="1000" /></label
              ><label
                >Image description<input v-model.trim="im.alt" required maxlength="400" /></label
              ><label>Caption<textarea v-model.trim="im.caption" required maxlength="600" /></label
              ><label
                >Kind<select v-model="im.kind">
                  <option>DIAGRAM</option>
                  <option>PHOTO</option>
                  <option>SCREENSHOT</option>
                </select></label
              ><label>Reference URL<input type="url" v-model.trim="im.sourceUrl" required /></label
              ><label
                >Reference title<input
                  v-model.trim="im.sourceTitle"
                  required
                  maxlength="200" /></label
              ><button type="button" class="link" @click="item.images.splice(n, 1)">
                Remove image
              </button>
            </div>
            <button
              type="button"
              class="secondary"
              :disabled="item.images.length >= 3"
              @click="
                item.images.push({
                  url: '',
                  alt: '',
                  caption: '',
                  kind: 'DIAGRAM',
                  sourceUrl: '',
                  sourceTitle: ''
                })
              "
            >
              Add example image</button
            ><CheckExamples :images="item.images.filter((im) => im.url && im.alt && im.caption)" />
          </details>
        </section>
        <button
          type="button"
          class="secondary"
          :disabled="form.items.length >= 40"
          @click="form.items.push(blankCheck())"
        >
          Add check
        </button>
      </fieldset>
      <div class="sticky-actions action-row" v-if="editable">
        <button class="primary" :disabled="busy">{{ busy ? 'Saving…' : 'Save draft' }}</button
        ><button
          class="secondary"
          type="button"
          :disabled="busy || dirty || !version.items?.length"
          @click="publish"
        >
          Publish guide</button
        ><span class="small-note">{{
          dirty ? 'Save changes before publishing.' : 'Saved draft ready for review.'
        }}</span>
      </div>
    </form>
    <div v-if="form && !editable" class="small-note">
      Published versions are read-only. Create a new draft to make changes.
    </div>
  </section>
</template>

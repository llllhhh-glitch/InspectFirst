<script setup>
import { confirmDiscard } from './confirm.js';
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { getJson, writeJson } from './api.js';
import GuideEditor from './GuideEditor.vue';
const emit = defineEmits(['expired', 'dirty']);
const categories = ref([]),
  rows = ref([]),
  guides = ref([]),
  page = ref(0),
  total = ref(0),
  more = ref(false),
  q = ref(''),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  editing = ref(null),
  productForm = ref(null),
  categoryForm = ref(null),
  selected = ref(null);
const productBaseline = ref(''),
  categoryBaseline = ref('');
const dirty = computed(
  () =>
    !!(
      (productForm.value && JSON.stringify(productForm.value) !== productBaseline.value) ||
      (categoryForm.value && JSON.stringify(categoryForm.value) !== categoryBaseline.value)
    )
);
watch(dirty, (v) => emit('dirty', v));
function category(c) {
  categoryForm.value = { ...c };
  categoryBaseline.value = JSON.stringify(categoryForm.value);
}
async function manage(p) {
  if (dirty.value && !(await confirmDiscard('Discard unsaved product changes?'))) return;
  productForm.value = null;
  categoryForm.value = null;
  selected.value = p;
}
let request,
  alive = true;
function fail(e) {
  if (alive && e.name !== 'AbortError') {
    error.value = e.message;
    if (e.status === 401) emit('expired');
  }
}
async function load(reset = false) {
  if (reset) page.value = 0;
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  try {
    const [cs, ps] = await Promise.all([
      getJson('/api/ops/content/categories', current.signal),
      getJson(
        `/api/ops/content/products?${new URLSearchParams({ q: q.value, page: page.value, size: 12 })}`,
        current.signal
      )
    ]);
    let gs = [],
      n = 0,
      hasNext = true;
    while (hasNext) {
      const d = await getJson(`/api/ops/content/guides?page=${n++}&size=100`, current.signal);
      gs.push(...d.items);
      hasNext = d.hasNext;
    }
    if (alive && request === current) {
      categories.value = cs;
      rows.value = ps.items;
      total.value = ps.total;
      more.value = ps.hasNext;
      guides.value = gs;
    }
  } catch (e) {
    fail(e);
  } finally {
    if (alive && request === current) loading.value = false;
  }
}
function product(row) {
  editing.value = row?.id || null;
  productForm.value = row
    ? {
        categoryId: row.categoryId,
        brand: row.brand,
        modelCode: row.modelCode,
        name: row.name,
        status: row.status
      }
    : {
        categoryId: categories.value[0]?.id || '',
        brand: '',
        modelCode: '',
        name: '',
        status: 'ACTIVE'
      };
  productBaseline.value = JSON.stringify(productForm.value);
}
async function saveProduct() {
  busy.value = true;
  error.value = '';
  try {
    await writeJson(
      '/api/ops/content/products' + (editing.value ? '/' + editing.value : ''),
      editing.value ? 'PUT' : 'POST',
      productForm.value
    );
    productForm.value = null;
    notice.value = 'Product saved. Only active products with a published guide appear publicly.';
    await load();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function saveCategory() {
  busy.value = true;
  error.value = '';
  try {
    const { id, ...body } = categoryForm.value;
    await writeJson(
      '/api/ops/content/categories' + (id ? '/' + id : ''),
      id ? 'PUT' : 'POST',
      body
    );
    categoryForm.value = null;
    notice.value = 'Category saved';
    await load();
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
function guideFor(id) {
  return guides.value.find((g) => g.productId === id);
}
onMounted(load);
onBeforeUnmount(() => {
  alive = false;
  request?.abort();
  emit('dirty', false);
});
</script>
<template>
  <GuideEditor
    v-if="selected"
    :key="selected.id"
    :product="selected"
    :guide-id="guideFor(selected.id)?.id"
    @dirty="emit('dirty', $event)"
    @close="
      selected = null;
      load();
    "
    @expired="emit('expired')"
    @changed="load()"
  />
  <section v-else>
    <div class="page-heading">
      <h1>Content</h1>
      <button class="primary" :disabled="busy" @click="product()">Add product</button>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success-note" role="status">{{ notice }}</p>
    <details class="panel category-manager">
      <summary>Categories ({{ categories.length }})</summary>
      <div class="category-chips">
        <button v-for="c in categories" :key="c.id" class="secondary" @click="category(c)">
          {{ c.name }}</button
        ><button class="secondary" @click="category({ slug: '', name: '' })">Add category</button>
      </div>
    </details>
    <form v-if="categoryForm" class="panel workflow-form" @submit.prevent="saveCategory">
      <h2>{{ categoryForm.id ? 'Edit category' : 'New category' }}</h2>
      <fieldset :disabled="busy">
        <label>Name<input v-model.trim="categoryForm.name" maxlength="80" required /></label
        ><label
          >Category key<input
            v-model.trim="categoryForm.slug"
            pattern="[a-z-]{1,60}"
            required
            placeholder="phones"
        /></label>
        <div class="action-row">
          <button class="primary">Save category</button
          ><button class="secondary" type="button" @click="categoryForm = null">Cancel</button>
        </div>
      </fieldset>
    </form>
    <form v-if="productForm" class="panel workflow-form" @submit.prevent="saveProduct">
      <h2>{{ editing ? 'Edit product' : 'New product' }}</h2>
      <fieldset :disabled="busy">
        <label>Name<input v-model.trim="productForm.name" maxlength="160" required /></label>
        <div class="form-columns">
          <label>Brand<input v-model.trim="productForm.brand" maxlength="80" required /></label
          ><label
            >Model<input v-model.trim="productForm.modelCode" maxlength="80" required
          /></label>
        </div>
        <label
          >Category<select v-model="productForm.categoryId" required>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select></label
        ><label
          >Status<select v-model="productForm.status">
            <option>ACTIVE</option>
            <option>ARCHIVED</option>
          </select></label
        >
        <p class="small-note">
          Archiving hides a product from browsing and keeps existing records.
        </p>
        <div class="action-row">
          <button class="primary">Save product</button
          ><button class="secondary" type="button" @click="productForm = null">Cancel</button>
        </div>
      </fieldset>
    </form>
    <form class="toolbar" @submit.prevent="load(true)">
      <label
        >Find product<input
          v-model.trim="q"
          type="search"
          placeholder="Name, brand or model" /></label
      ><button class="secondary" :disabled="loading">Search</button
      ><span class="muted">{{ total }} products</span>
    </form>
    <div v-if="loading" class="state">Loading…</div>
    <div v-else class="library-list">
      <article v-for="p in rows" :key="p.id" class="library-row">
        <div class="library-info">
          <h2>{{ p.name }}</h2>
          <p class="small-note">
            {{ p.brand }} · {{ p.modelCode }} ·
            {{ categories.find((c) => c.id === p.categoryId)?.name }}
          </p>
          <span class="status-badge">{{ p.status }}</span
          ><span class="status-badge">{{ guideFor(p.id)?.availability || 'No guide' }}</span>
        </div>
        <div class="library-actions">
          <button class="secondary" @click="product(p)">Edit product</button
          ><button class="primary" @click="manage(p)">Manage guide</button>
        </div>
      </article>
      <p v-if="!rows.length" class="state">No products found.</p>
    </div>
    <div v-if="total > 12" class="pagination">
      <button class="secondary" :disabled="!page || loading" @click="change(-1)">Previous</button
      ><span>{{ page + 1 }}</span
      ><button class="secondary" :disabled="!more || loading" @click="change(1)">Next</button>
    </div>
  </section>
</template>

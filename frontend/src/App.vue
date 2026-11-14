<script setup>
import { confirmDiscard } from './confirm.js';
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { getJson, authAction, writeJson } from './api.js';
import AccountPanel from './AccountPanel.vue';
import ConfirmDialog from './ConfirmDialog.vue';
import Icon from './Icon.vue';
import ProductVisual from './ProductVisual.vue';
import CheckExamples from './CheckExamples.vue';
import ProfileMenu from './ProfileMenu.vue';
import UserLibrary from './UserLibrary.vue';
import InspectionRecord from './InspectionRecord.vue';
import StartInspection from './StartInspection.vue';
import FeedbackPanel from './FeedbackPanel.vue';
import AdminConsole from './AdminConsole.vue';
const categoryIcons = {
  cameras: 'camera',
  monitors: 'monitor',
  consoles: 'gamepad',
  audio: 'headphones',
  phones: 'phone',
  tablets: 'tablet',
  computers: 'laptop',
  furniture: 'chair',
  luxury: 'bag'
};

const startOpen = ref(false),
  inspectionDirty = ref(false);
const categories = ref([]),
  products = ref([]),
  query = ref(''),
  category = ref('');
const page = ref(0),
  total = ref(0),
  hasNext = ref(false),
  loading = ref(false),
  error = ref('');
const guide = ref(null),
  selectedId = ref(''),
  selectedKind = ref('products'),
  categoryError = ref('');
const savedGuide = ref(false),
  checkingSaved = ref(false),
  savingGuide = ref(false),
  saveError = ref('');
let savedRequest,
  saveGeneration = 0;
const route = ref('/'),
  user = ref(null),
  sessionReady = ref(false),
  sessionError = ref('');
const authNotice = ref(''),
  loggingOut = ref(false);
let returnRoute = '/',
  sessionRequest,
  sessionGeneration = 0;
const isAuth = computed(() => ['login', 'register'].includes(route.value));
const recordId = computed(() => route.value.match(/^\/inspections\/([a-f0-9-]{36})$/)?.[1] || '');
const displayProduct = computed(
  () =>
    guide.value?.product || {
      name: guide.value?.category?.name || 'Guide',
      category: guide.value?.category?.name,
      categorySlug: guide.value?.category?.slug,
      modelCode: 'General guide'
    }
);
function sessionExpired() {
  inspectionDirty.value = false;
  user.value = null;
  returnRoute = route.value;
  navigate('login');
}
async function checkSaved() {
  savedRequest?.abort();
  const generation = ++saveGeneration;
  savedGuide.value = false;
  saveError.value = '';
  checkingSaved.value = false;
  if (!user.value || !guide.value) return;
  const id = guide.value.id,
    current = new AbortController();
  savedRequest = current;
  checkingSaved.value = true;
  try {
    let pageIndex = 0,
      found = false,
      more = true;
    while (more && !found) {
      const data = await getJson(`/api/saved-guides?page=${pageIndex++}&size=100`, current.signal);
      const entry = data.items.find((item) => item.guideId === id);
      found = Boolean(entry);
      more = data.hasNext;
      if (entry?.updated && entry.available && generation === saveGeneration)
        await writeJson(`/api/saved-guides/${id}/seen`, 'POST', {});
    }
    if (generation === saveGeneration) savedGuide.value = found;
  } catch (e) {
    if (e.name !== 'AbortError' && generation === saveGeneration) {
      if (e.status === 401) sessionExpired();
      else saveError.value = 'Unable to load saved status.';
    }
  } finally {
    if (generation === saveGeneration) checkingSaved.value = false;
  }
}
async function toggleSaved() {
  if (!user.value) {
    returnRoute = route.value;
    navigate('login');
    return;
  }
  if (savingGuide.value || checkingSaved.value || !guide.value) return;
  const id = guide.value.id;
  const wasSaved = savedGuide.value;
  const owner = user.value.id;
  savingGuide.value = true;
  saveError.value = '';
  try {
    await writeJson(`/api/saved-guides/${id}`, wasSaved ? 'DELETE' : 'PUT');
    if (guide.value?.id === id && user.value?.id === owner) savedGuide.value = !wasSaved;
  } catch (e) {
    if (guide.value?.id === id && user.value?.id === owner) {
      if (e.status === 401) sessionExpired();
      else saveError.value = e.message;
    }
  } finally {
    savingGuide.value = false;
  }
}
watch([() => user.value?.id, () => guide.value?.id], checkSaved);
function navigate(target) {
  window.location.hash = target;
}
async function refreshSession() {
  const wasReady = sessionReady.value,
    previousOwner = user.value?.id;
  const generation = ++sessionGeneration;
  sessionRequest?.abort();
  sessionRequest = new AbortController();
  sessionError.value = '';
  try {
    const current = await getJson('/api/auth/me', sessionRequest.signal);
    if (generation === sessionGeneration) user.value = current;
  } catch (e) {
    if (generation === sessionGeneration && e.name !== 'AbortError') {
      user.value = null;
      if (e.status !== 401)
        sessionError.value = 'Accounts are temporarily unavailable. You can still browse guides.';
    }
  } finally {
    if (generation === sessionGeneration) {
      sessionReady.value = true;
      if (!wasReady || previousOwner !== user.value?.id) syncRoute();
    }
  }
}
function loggedIn(account) {
  sessionGeneration++;
  sessionRequest?.abort();
  user.value = account;
  sessionError.value = '';
  authNotice.value = '';
  navigate(account.role === 'ADMIN' ? '/ops' : returnRoute);
  returnRoute = '/';
}
async function logout() {
  if (loggingOut.value) return;
  if (inspectionDirty.value && !(await confirmDiscard('Discard unsaved changes and log out?')))
    return;
  loggingOut.value = true;
  authNotice.value = '';
  try {
    await authAction('logout');
    inspectionDirty.value = false;
    sessionGeneration++;
    sessionRequest?.abort();
    user.value = null;
    returnRoute = '/';
    navigate('/');
  } catch (e) {
    authNotice.value = e.message;
  } finally {
    loggingOut.value = false;
  }
}
let request, categoryRequest;
const isDetail = computed(() => Boolean(selectedId.value));

async function loadCategories() {
  categoryRequest?.abort();
  categoryRequest = new AbortController();
  try {
    categories.value = await getJson('/api/categories', categoryRequest.signal);
    const categoryOrder = [
      'phones',
      'tablets',
      'audio',
      'computers',
      'furniture',
      'luxury',
      'cameras',
      'monitors',
      'consoles'
    ];
    categories.value.sort((a, b) => categoryOrder.indexOf(a.slug) - categoryOrder.indexOf(b.slug));
    categoryError.value = '';
  } catch (e) {
    if (e.name !== 'AbortError')
      categoryError.value = 'Categories are unavailable. You can still retry or search.';
  }
}
async function load() {
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  guide.value = null;
  try {
    if (selectedId.value) {
      const data = await getJson(
        `/api/${selectedKind.value}/${encodeURIComponent(selectedId.value)}/guide`,
        current.signal
      );
      if (request === current) guide.value = data;
    } else {
      const params = new URLSearchParams({
        q: query.value.trim(),
        category: category.value,
        page: page.value,
        size: 12
      });
      const result = await getJson(`/api/products?${params}`, current.signal);
      if (request !== current) return;
      products.value = result.items;
      total.value = result.total;
      hasNext.value = result.hasNext;
    }
  } catch (e) {
    if (e.name !== 'AbortError' && request === current) error.value = e.message;
  } finally {
    if (request === current) loading.value = false;
  }
}
async function syncRoute() {
  const hash = window.location.hash.slice(1) || '/';
  if (hash !== route.value && inspectionDirty.value && user.value) {
    if (!(await confirmDiscard('Leave without saving your changes?'))) {
      window.location.hash = route.value;
      return;
    }
    inspectionDirty.value = false;
  }
  if (hash !== route.value) startOpen.value = false;
  route.value = hash;
  if (!sessionReady.value) return;
  if (
    (['/ops', '/account', '/saved-guides', '/my-inspections'].includes(hash) ||
      /^\/inspections\//.test(hash) ||
      /^\/feedback(?:\?|$)/.test(hash)) &&
    !user.value
  ) {
    returnRoute = hash;
    navigate('login');
    return;
  }
  if (['login', 'register'].includes(hash) && user.value) {
    navigate(user.value.role === 'ADMIN' ? '/ops' : '/account');
    return;
  }
  const match = hash.match(/^\/(products|categories)\/([a-f0-9-]{36})$/);
  selectedKind.value = match?.[1] || 'products';
  selectedId.value = match?.[2] || '';
  if (hash === '/' || match) load();
  else request?.abort();
}
const feedbackContext = computed(() => {
  const params = new URLSearchParams(route.value.split('?')[1] || '');
  return Object.fromEntries(
    ['productId', 'guideVersionId', 'checkItemId']
      .filter((k) => params.get(k))
      .map((k) => [k, params.get(k)])
  );
});
function startInspection() {
  if (!user.value) {
    returnRoute = route.value;
    navigate('login');
    return;
  }
  startOpen.value = true;
}
function reportGuide(item) {
  const params = new URLSearchParams({
    ...(guide.value.product ? { productId: guide.value.product.id } : {}),
    guideVersionId: guide.value.versionId,
    ...(item ? { checkItemId: item.id } : {})
  });
  navigate('/feedback?' + params);
}
function search() {
  page.value = 0;
  if (route.value !== '/') navigate('/');
  else load();
}
function selectCategory(slug) {
  category.value = slug;
  search();
}
function openProduct(id) {
  window.location.hash = `/products/${id}`;
}
function home() {
  loadCategories();
  window.location.hash = '/';
  if (!selectedId.value) load();
}
function clear() {
  query.value = '';
  category.value = '';
  search();
}
function changePage(delta) {
  page.value += delta;
  load();
}
onMounted(() => {
  loadCategories();
  refreshSession();
  window.addEventListener('hashchange', syncRoute);
  window.addEventListener('focus', refreshSession);
});
onBeforeUnmount(() => {
  request?.abort();
  categoryRequest?.abort();
  sessionRequest?.abort();
  savedRequest?.abort();
  window.removeEventListener('hashchange', syncRoute);
  window.removeEventListener('focus', refreshSession);
});
</script>

<template>
  <ConfirmDialog />
  <header class="header">
    <div class="header-inner">
      <button class="logo" aria-label="InspectFirst home" @click="home">
        <span><Icon name="check" :size="23" /></span>InspectFirst
      </button>
      <form
        v-if="!isAuth && route !== '/ops'"
        class="search"
        role="search"
        @submit.prevent="search"
      >
        <Icon name="search" :size="19" /><label class="sr-only" for="catalog-search"
          >Search product or model</label
        >
        <input
          id="catalog-search"
          v-model="query"
          type="search"
          maxlength="100"
          placeholder="Search product or model"
        />
        <button class="primary">Search</button>
      </form>
      <div class="account-nav">
        <template v-if="sessionReady && user">
          <ProfileMenu :user="user" :route="route" :busy="loggingOut" @logout="logout" />
        </template>
        <template v-else-if="sessionReady"
          ><button class="nav-button" @click="navigate('login')">Log in</button
          ><button class="secondary signup" @click="navigate('register')">Sign up</button></template
        >
      </div>
    </div>
  </header>
  <main :class="{ 'auth-main': isAuth }">
    <p v-if="sessionError" class="notice" role="status">
      {{ sessionError }} <button class="link" @click="refreshSession">Retry</button>
    </p>
    <p v-if="authNotice" class="form-error" role="alert">{{ authNotice }}</p>
    <div v-if="!sessionReady" class="state" role="status">Loading…</div>
    <AccountPanel v-else-if="isAuth" :mode="route" @logged-in="loggedIn" @navigate="navigate" />
    <UserLibrary
      v-else-if="user && ['/saved-guides', '/my-inspections'].includes(route)"
      :key="`${user.id}-${route}`"
      :mode="route === '/saved-guides' ? 'saved' : 'inspections'"
      @navigate="navigate"
      @expired="sessionExpired"
    />
    <InspectionRecord
      v-else-if="user && recordId"
      :key="`${user.id}-${recordId}`"
      :id="recordId"
      @navigate="navigate"
      @expired="sessionExpired"
      @dirty="inspectionDirty = $event"
    />
    <FeedbackPanel
      v-else-if="user && route.startsWith('/feedback')"
      :key="`${user.id}-${route}`"
      :context="feedbackContext"
      @dirty="inspectionDirty = $event"
      @expired="sessionExpired"
      @navigate="navigate"
    />
    <section v-else-if="route === '/account' && user" class="panel account-page">
      <h1>Account</h1>
      <div class="account-identity">
        <span class="avatar">{{ user.displayName.slice(0, 1).toUpperCase() }}</span>
        <div>
          <h2>{{ user.displayName }}</h2>
          <p class="muted">{{ user.email }}</p>
        </div>
      </div>
      <button class="primary" @click="home">Browse guides</button
      ><button v-if="user.role === 'ADMIN'" class="secondary" @click="navigate('/ops')">
        Admin dashboard
      </button>
    </section>
    <template v-else-if="route === '/ops'"
      ><AdminConsole
        v-if="user?.role === 'ADMIN'"
        :key="user.id"
        @dirty="inspectionDirty = $event"
        @expired="sessionExpired"
        @navigate="navigate"
      />
      <section v-else class="panel state">
        <h1>Staff access only</h1>
        <button class="secondary" @click="home">Back to guides</button>
      </section></template
    >
    <section v-else-if="route !== '/' && !isDetail" class="state">
      <h1>Page not found</h1>
      <button class="secondary" @click="home">Back to guides</button>
    </section>
    <template v-else>
      <template v-if="!isDetail">
        <nav class="filters" aria-label="Product category">
          <button :aria-pressed="!category" @click="selectCategory('')">
            <Icon name="grid" :size="18" />All
          </button>
          <button
            v-for="c in categories"
            :key="c.id"
            :aria-pressed="category === c.slug"
            @click="selectCategory(c.slug)"
          >
            <Icon :name="categoryIcons[c.slug] || 'grid'" :size="19" />{{ c.name }}
          </button>
        </nav>
        <p v-if="categoryError" class="notice" role="status">
          Categories unavailable. <button class="link" @click="loadCategories">Retry</button>
        </p>
        <div class="page-heading catalogue-heading">
          <h1>
            {{
              query
                ? 'Search results'
                : category
                  ? categories.find((c) => c.slug === category)?.name || 'Guides'
                  : 'Inspection guides'
            }}
          </h1>
          <span v-if="!loading && !error" class="result-count"
            >{{ total }} {{ total === 1 ? 'guide' : 'guides' }}</span
          >
        </div>
      </template>
      <div v-else class="breadcrumb">
        <button @click="home">Guides</button><Icon name="chevron" :size="13" /><span>{{
          displayProduct.category || 'Details'
        }}</span>
      </div>
      <div v-if="loading" class="state" role="status">Loading…</div>
      <section v-else-if="error" class="state" role="alert">
        <h2>Unable to load {{ isDetail ? 'guide' : 'products' }}</h2>
        <p class="muted">{{ error }}</p>
        <button class="secondary" @click="load">Try again</button>
      </section>
      <template v-else-if="!isDetail">
        <div v-if="products.length" class="product-grid">
          <button v-for="p in products" :key="p.id" class="product" @click="openProduct(p.id)">
            <ProductVisual :product="p" />
            <div class="product-info">
              <span class="product-category">{{ p.category }}</span>
              <h2>{{ p.name }}</h2>
              <p>{{ p.modelCode }}</p>
              <div class="product-action">View guide <Icon name="arrow" :size="17" /></div>
            </div>
          </button>
        </div>
        <section v-else class="state empty-state">
          <Icon name="search" :size="34" />
          <h2>No guides found</h2>
          <p class="muted">Try another model or category.</p>
          <button class="secondary" @click="clear">Browse all</button>
        </section>
        <div v-if="total > 12" class="pagination">
          <button class="secondary" :disabled="page === 0" @click="changePage(-1)">Previous</button
          ><span>{{ page + 1 }}</span
          ><button class="secondary" :disabled="!hasNext" @click="changePage(1)">Next</button>
        </div>
      </template>
      <template v-else-if="guide">
        <div class="guide-heading">
          <ProductVisual :product="displayProduct" />
          <div>
            <span class="product-category">{{ displayProduct.category }}</span>
            <h1>{{ displayProduct.name }}</h1>
            <p class="muted">
              {{ displayProduct.modelCode }}<span class="dot">·</span
              >{{ guide.items.length }} checks
            </p>
          </div>
          <button
            class="secondary save-guide-button"
            :class="{ saved: savedGuide }"
            :aria-pressed="savedGuide"
            :disabled="checkingSaved || savingGuide"
            @click="toggleSaved"
          >
            <Icon name="heart" :size="18" />{{
              savingGuide || checkingSaved ? 'Please wait…' : savedGuide ? 'Saved' : 'Save guide'
            }}
          </button>
        </div>
        <p v-if="saveError" class="form-error" role="alert">
          {{ saveError }} <button class="link" @click="checkSaved">Retry</button>
        </p>
        <div class="guide-actions action-row">
          <button class="primary" @click="startInspection">Start inspection</button
          ><button class="link" @click="reportGuide()">Report an issue</button>
        </div>
        <StartInspection
          v-if="startOpen"
          :key="guide.versionId"
          :guide="guide"
          @dirty="inspectionDirty = $event"
          @started="navigate('/inspections/' + $event)"
          @cancel="startOpen = false"
          @expired="sessionExpired"
        />
        <article class="guide-layout">
          <section class="panel checks-panel">
            <div class="section-heading">
              <h2>Inspection checklist</h2>
              <span>{{ guide.items.length }} checks</span>
            </div>
            <details v-for="item in guide.items" :key="item.id" class="check-item">
              <summary>
                <span class="step-number">{{ item.order }}</span
                ><span>{{ item.title }}</span
                ><Icon class="expand-icon" name="chevron" :size="17" />
              </summary>
              <div class="check-content">
                <p class="muted">{{ item.purpose }}</p>
                <ol>
                  <li v-for="step in item.steps" :key="step">{{ step }}</li>
                </ol>
                <CheckExamples :images="item.images || []" />
                <div class="observations">
                  <section>
                    <h3>Expected</h3>
                    <p>{{ item.expectedObservation }}</p>
                  </section>
                  <section>
                    <h3>Watch for</h3>
                    <p>{{ item.abnormalSigns }}</p>
                  </section>
                </div>
                <details class="reference-details">
                  <summary>Limits &amp; sources</summary>
                  <p>{{ item.limitations }}</p>
                  <ul class="sources">
                    <li v-for="source in item.sources" :key="source.url">
                      <a :href="source.url" target="_blank" rel="noopener noreferrer"
                        >{{ source.title }} ↗</a
                      ><span>{{ source.publisher }}</span>
                    </li>
                  </ul>
                  <button class="link" @click="reportGuide(item)">Report this check</button>
                </details>
              </div>
            </details>
          </section>
          <aside class="guide-sidebar">
            <section class="panel">
              <h2>Before you check</h2>
              <p>{{ guide.tools }}</p>
              <div class="model-note">
                <strong>Model</strong><span>{{ displayProduct.modelCode }}</span>
              </div>
            </section>
            <details class="panel about-guide">
              <summary>About this guide</summary>
              <p>{{ guide.contentNote }}</p>
              <p class="muted">Version {{ guide.version }}<br />Reviewed {{ guide.reviewedOn }}</p>
            </details>
          </aside>
        </article>
      </template>
    </template>
  </main>
  <footer>
    <span>© InspectFirst</span><span>Self-check guides. Not a quality guarantee.</span>
  </footer>
</template>

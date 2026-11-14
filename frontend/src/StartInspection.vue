<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { writeJson } from './api.js';
const props = defineProps({ guide: Object });
const emit = defineEmits(['started', 'cancel', 'expired', 'dirty']);
const requirements = ref(''),
  seller = ref(''),
  busy = ref(false),
  error = ref('');
let key = crypto.randomUUID(),
  last = '',
  alive = true;
watch([requirements, seller], () => emit('dirty', !!(requirements.value || seller.value)));
onBeforeUnmount(() => {
  alive = false;
  emit('dirty', false);
});
async function start() {
  busy.value = true;
  error.value = '';
  const data = {
    ...(props.guide.product
      ? { productId: props.guide.product.id }
      : { categoryId: props.guide.category.id }),
    guideVersionId: props.guide.versionId,
    userRequirement: requirements.value.trim(),
    sellerClaim: seller.value.trim()
  };
  const fingerprint = JSON.stringify(data);
  if (last && last !== fingerprint) key = crypto.randomUUID();
  last = fingerprint;
  try {
    const result = await writeJson('/api/inspections', 'POST', { ...data, requestKey: key });
    if (alive) {
      emit('dirty', false);
      emit('started', result.id);
    }
  } catch (e) {
    if (alive) {
      error.value = e.message;
      if (e.status === 401) emit('expired');
    }
  } finally {
    if (alive) busy.value = false;
  }
}
</script>
<template>
  <form class="panel workflow-form" @submit.prevent="start">
    <h2>Start an inspection</h2>
    <fieldset :disabled="busy">
      <label
        >My requirements <span class="muted">Optional</span
        ><textarea
          v-model="requirements"
          maxlength="3000"
          rows="2"
          placeholder="What matters to you?"
        ></textarea></label
      ><label
        >Seller’s description <span class="muted">Optional</span
        ><textarea
          v-model="seller"
          maxlength="3000"
          rows="2"
          placeholder="Condition and accessories promised"
        ></textarea>
      </label>
      <p class="small-note">
        Your observations are private to your account and authorised staff. Unchecked items are not
        passes.
      </p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="action-row">
        <button class="primary" type="submit">{{ busy ? 'Starting…' : 'Create inspection' }}</button
        ><button class="secondary" type="button" @click="emit('cancel')">Cancel</button>
      </div>
    </fieldset>
  </form>
</template>

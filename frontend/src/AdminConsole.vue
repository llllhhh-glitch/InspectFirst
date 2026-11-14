<script setup>
import { confirmDiscard } from './confirm.js';
import { ref } from 'vue';
import ContentManager from './ContentManager.vue';
import FeedbackPanel from './FeedbackPanel.vue';
import AdminUsers from './AdminUsers.vue';
import AdminInspections from './AdminInspections.vue';
const emit = defineEmits(['expired', 'navigate', 'dirty']);
const tab = ref('Content'),
  owner = ref(null),
  dirty = ref(false);
function changed(v) {
  dirty.value = v;
  emit('dirty', v);
}
async function choose(name) {
  if (name === tab.value) return;
  if (dirty.value && !(await confirmDiscard('Discard unsaved changes?'))) return;
  changed(false);
  tab.value = name;
}
function userInspections(user) {
  owner.value = user;
  choose('Inspections');
}
</script>
<template>
  <section class="operations">
    <div class="admin-heading">
      <strong>Operations</strong><span class="tag">Admin</span
      ><button class="link" @click="emit('navigate', '/')">View site ↗</button>
    </div>
    <nav class="admin-tabs" aria-label="Operations sections">
      <button
        v-for="name in ['Content', 'Feedback', 'Users', 'Inspections']"
        :key="name"
        :aria-pressed="tab === name"
        @click="choose(name)"
      >
        {{ name }}
      </button>
    </nav>
    <ContentManager
      v-if="tab === 'Content'"
      @dirty="changed"
      @expired="emit('expired')"
    /><FeedbackPanel
      v-else-if="tab === 'Feedback'"
      admin
      @dirty="changed"
      @expired="emit('expired')"
    /><AdminUsers
      v-else-if="tab === 'Users'"
      @inspections="userInspections"
      @expired="emit('expired')"
    /><AdminInspections v-else :owner="owner" @expired="emit('expired')" />
  </section>
</template>

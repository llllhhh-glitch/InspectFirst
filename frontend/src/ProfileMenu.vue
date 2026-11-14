<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import Icon from './Icon.vue';
const props = defineProps({ user: Object, route: String, busy: Boolean });
const emit = defineEmits(['logout']);
const open = ref(false),
  root = ref(null),
  trigger = ref(null);
function outside(event) {
  if (!root.value?.contains(event.target)) open.value = false;
}
function escape(event) {
  if (event.key === 'Escape' && open.value) {
    open.value = false;
    nextTick(() => trigger.value?.focus());
  }
}
function focusOut(event) {
  if (!root.value?.contains(event.relatedTarget)) open.value = false;
}
watch(
  () => props.route,
  () => {
    open.value = false;
  }
);
watch(
  () => props.user?.id,
  () => {
    open.value = false;
  }
);
onMounted(() => {
  document.addEventListener('pointerdown', outside);
  document.addEventListener('keydown', escape);
});
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside);
  document.removeEventListener('keydown', escape);
});
</script>
<template>
  <div ref="root" class="profile-menu" @focusout="focusOut">
    <button
      ref="trigger"
      class="avatar-button"
      aria-label="Account menu"
      :aria-expanded="open"
      aria-controls="profile-links"
      @click="open = !open"
    >
      <Icon name="user" :size="22" />
    </button>
    <nav v-if="open" id="profile-links" class="profile-dropdown" aria-label="Your account">
      <div class="profile-summary">
        <strong>{{ user.displayName }}</strong
        ><span>{{ user.email }}</span>
      </div>
      <a href="#/saved-guides" @click="open = false"
        ><Icon name="heart" :size="18" />Saved guides</a
      >
      <a href="#/my-inspections" @click="open = false"
        ><Icon name="clipboard" :size="18" />My inspections</a
      >
      <a href="#/feedback" @click="open = false"><Icon name="clipboard" :size="18" />Feedback</a>
      <a href="#/account" @click="open = false"><Icon name="user" :size="18" />Account</a>
      <a v-if="user.role === 'ADMIN'" href="#/ops" @click="open = false"
        ><Icon name="grid" :size="18" />Admin dashboard</a
      >
      <div class="profile-divider"></div>
      <button :disabled="busy" @click="emit('logout')">
        <Icon name="logout" :size="18" />{{ busy ? 'Logging out…' : 'Log out' }}
      </button>
    </nav>
  </div>
</template>

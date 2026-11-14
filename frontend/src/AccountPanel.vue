<script setup>
import { ref, onBeforeUnmount } from 'vue';
import { authAction } from './api.js';
const props = defineProps({ mode: String });
const emit = defineEmits(['logged-in', 'navigate']);
const email = ref(''),
  password = ref(''),
  displayName = ref(''),
  confirm = ref('');
const visible = ref(false),
  busy = ref(false),
  error = ref(''),
  success = ref('');
let active = true;
onBeforeUnmount(() => {
  active = false;
  password.value = '';
  confirm.value = '';
});
async function submit() {
  if (busy.value) return;
  error.value = '';
  success.value = '';
  if (props.mode === 'register' && password.value !== confirm.value) {
    error.value = 'Passwords do not match.';
    return;
  }
  busy.value = true;
  try {
    if (props.mode === 'register') {
      await authAction('register', {
        email: email.value.trim(),
        displayName: displayName.value.trim(),
        password: password.value
      });
      if (active) {
        success.value = 'Account created. Log in to continue.';
        password.value = '';
        confirm.value = '';
        emit('navigate', 'login');
      }
    } else {
      const user = await authAction('login', {
        email: email.value.trim(),
        password: password.value
      });
      if (active) {
        password.value = '';
        emit('logged-in', user);
      }
    }
  } catch (e) {
    if (active) error.value = e.message;
  } finally {
    if (active) busy.value = false;
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="panel auth-card">
      <h1>{{ mode === 'register' ? 'Create account' : 'Log in' }}</h1>
      <p v-if="success" class="notice" role="status">{{ success }}</p>
      <form @submit.prevent="submit">
        <label v-if="mode === 'register'" for="display-name"
          >Name<input
            id="display-name"
            v-model="displayName"
            autocomplete="nickname"
            required
            maxlength="60"
            :disabled="busy"
        /></label>
        <label for="email"
          >Email<input
            id="email"
            v-model="email"
            type="email"
            autocomplete="username"
            required
            maxlength="254"
            :disabled="busy"
            placeholder="you@example.com"
        /></label>
        <label for="password"
          >Password
          <div class="password-field">
            <input
              id="password"
              v-model="password"
              :type="visible ? 'text' : 'password'"
              :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
              required
              :minlength="mode === 'register' ? 12 : undefined"
              maxlength="128"
              :disabled="busy"
            /><button
              type="button"
              :aria-pressed="visible"
              :aria-label="visible ? 'Hide password' : 'Show password'"
              @click="visible = !visible"
            >
              {{ visible ? 'Hide' : 'Show' }}
            </button>
          </div></label
        >
        <p v-if="mode === 'register'" class="hint">12–128 characters</p>
        <label v-if="mode === 'register'" for="confirm-password"
          >Confirm password<input
            id="confirm-password"
            v-model="confirm"
            :type="visible ? 'text' : 'password'"
            autocomplete="new-password"
            required
            maxlength="128"
            :disabled="busy"
        /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary full-width" :disabled="busy">
          {{ busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Log in' }}
        </button>
      </form>
      <p class="auth-switch">
        {{ mode === 'register' ? 'Already registered?' : 'New here?' }}
        <button
          class="link"
          :disabled="busy"
          @click="
            error = '';
            success = '';
            password = '';
            confirm = '';
            visible = false;
            emit('navigate', mode === 'register' ? 'login' : 'register');
          "
        >
          {{ mode === 'register' ? 'Log in' : 'Create account' }}
        </button>
      </p>
    </div>
    <button class="back-link" @click="emit('navigate', '/')">← Back to guides</button>
  </section>
</template>

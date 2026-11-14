<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue';
import { confirmation, answerConfirmation } from './confirm.js';
const dialog = ref(null);
let trigger;
watch(confirmation, async (pending) => {
  if (pending) {
    trigger = document.activeElement;
    await nextTick();
    dialog.value?.showModal();
  } else {
    dialog.value?.close();
    trigger?.focus();
  }
});
onBeforeUnmount(() => answerConfirmation(false));
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="discard-dialog"
      aria-labelledby="discard-title"
      @cancel.prevent="answerConfirmation(false)"
    >
      <h2 id="discard-title">Unsaved changes</h2>
      <p>{{ confirmation?.message }}</p>
      <div class="action-row">
        <button class="primary" autofocus @click="answerConfirmation(false)">Keep editing</button>
        <button class="secondary danger" @click="answerConfirmation(true)">Discard changes</button>
      </div>
    </dialog>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue';
import Icon from './Icon.vue';
const props = defineProps({ images: { type: Array, default: () => [] } });
const dialog = ref(null),
  selected = ref(null),
  failed = ref(new Set());
let trigger = null;
const kindLabel = {
  DIAGRAM: 'Illustrated example',
  PHOTO: 'Photo example',
  SCREENSHOT: 'Screen example'
};
function imageFailed(url) {
  failed.value = new Set([...failed.value, url]);
}
async function open(image, event) {
  trigger = event.currentTarget;
  selected.value = image;
  await nextTick();
  dialog.value?.showModal();
}
function close() {
  dialog.value?.close();
}
function closed() {
  selected.value = null;
  trigger?.focus();
  trigger = null;
}
watch(
  () => props.images,
  () => {
    close();
    failed.value = new Set();
  }
);
onBeforeUnmount(close);
</script>
<template>
  <section v-if="images?.length" class="check-examples" aria-label="Visual examples">
    <figure v-for="image in images" :key="image.url" class="check-example">
      <div class="example-heading">
        <span>{{ kindLabel[image.kind] || 'Example' }}</span
        ><span v-if="image.kind === 'DIAGRAM'" class="example-kind">Schematic</span>
      </div>
      <button
        v-if="!failed.has(image.url)"
        type="button"
        class="example-preview"
        :aria-label="`Enlarge example: ${image.alt}`"
        @click="open(image, $event)"
      >
        <img
          :src="image.url"
          :alt="image.alt"
          loading="lazy"
          decoding="async"
          @error="imageFailed(image.url)"
        /><span class="example-enlarge">View larger <Icon name="search" :size="15" /></span>
      </button>
      <p v-else class="example-unavailable" role="status">
        Image unavailable. Please follow the written steps.
      </p>
      <figcaption>
        {{ image.caption }}
        <a
          :href="image.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="example-reference"
          >Reference ↗</a
        >
      </figcaption>
    </figure>
  </section>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="example-dialog"
      aria-label="Enlarged inspection example"
      @close="closed"
      @click="
        (event) => {
          if (event.target === dialog) close();
        }
      "
    >
      <template v-if="selected"
        ><div class="example-dialog-toolbar">
          <strong>{{ kindLabel[selected.kind] || 'Example' }}</strong
          ><button
            type="button"
            class="secondary"
            aria-label="Close example"
            autofocus
            @click="close"
          >
            <Icon name="close" :size="18" />Close
          </button>
        </div>
        <img :src="selected.url" :alt="selected.alt" />
        <p>{{ selected.caption }}</p>
        <a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer"
          >{{ selected.sourceTitle }} ↗</a
        ></template
      >
    </dialog>
  </Teleport>
</template>

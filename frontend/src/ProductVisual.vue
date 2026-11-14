<script setup>
import { computed, ref, watch } from 'vue';
import Icon from './Icon.vue';
import productImages from './data/product-images.json';
const props = defineProps({ product: Object });
const failed = ref(false);
const image = computed(() => productImages[props.product?.modelCode]);
watch(
  () => props.product?.id,
  () => {
    failed.value = false;
  }
);
const icons = {
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
</script>
<template>
  <div class="product-visual">
    <img
      v-if="image && !failed"
      :src="image.src"
      :alt="product.name"
      loading="lazy"
      decoding="async"
      @error="failed = true"
    />
    <Icon v-else :name="icons[product?.categorySlug] || 'grid'" :size="64" />
  </div>
</template>

<script setup>
defineProps({
  name: { type: String, default: 'grid' },
  size: { type: [Number, String], default: 20 }
});
const paths = {
  search: ['m21 21-4.5-4.5', 'M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z'],
  camera: [
    'M14.5 4h-5L8 7H4a2 2 0 0 0-2 2v10h20V9a2 2 0 0 0-2-2h-4l-1.5-3Z',
    'M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z'
  ],
  monitor: ['M3 3h18v13H3z', 'M8 21h8m-4-5v5'],
  gamepad: [
    'M7 7h10a4 4 0 0 1 4 3l1 7a2 2 0 0 1-3 2l-4-3H9l-4 3a2 2 0 0 1-3-2l1-7a4 4 0 0 1 4-3Z',
    'M7 10v5m-2.5-2.5h5M16 11h.01M19 14h.01'
  ],
  headphones: ['M3 14v-3a9 9 0 0 1 18 0v3', 'M3 12h4v9H3zM17 12h4v9h-4z'],
  phone: ['M6 2h12v20H6z', 'M10 18h4'],
  tablet: ['M4 2h16v20H4z', 'M11 18h2'],
  laptop: ['M4 3h16v13H4z', 'M2 20h20l-2-4H4z'],
  chair: ['M5 3h14v9H5z', 'M4 12h16v4H4z', 'M6 16v6M18 16v6'],
  bag: ['M4 7h16l1 15H3z', 'M8 8V5a4 4 0 0 1 8 0v3'],
  grid: ['M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z'],
  arrow: ['M5 12h14m-6-6 6 6-6 6'],
  check: ['m5 12 4 4L19 6'],
  user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  heart: [
    'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z'
  ],
  clipboard: ['M9 4H5v18h14V4h-4M9 2h6v4H9zM8 11h8M8 16h6'],
  logout: ['M9 3H4v18h5M10 12h11m-4-4 4 4-4 4'],
  close: ['m6 6 12 12M6 18 18 6'],
  chevron: ['m9 5 7 7-7 7']
};
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(path, index) in paths[name] || paths.grid" :key="index" :d="path" />
  </svg>
</template>

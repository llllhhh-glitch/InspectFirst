import { shallowRef } from 'vue';

export const confirmation = shallowRef(null);

// One app-level dialog keeps route and editor guards consistent and keyboard accessible.
export function confirmDiscard(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}

export function answerConfirmation(answer) {
  const pending = confirmation.value;
  confirmation.value = null;
  pending?.resolve(answer);
}

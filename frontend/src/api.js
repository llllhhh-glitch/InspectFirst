export async function apiRequest(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...options,
    headers: { Accept: 'application/json', ...options.headers }
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(
      body.detail || `The request could not be completed (${response.status}).`
    );
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
export function getJson(path, signal) {
  return apiRequest(path, { signal });
}
export async function authAction(action, data) {
  // Fetch afresh: login and logout rotate the session's CSRF token.
  const csrf = await apiRequest('/api/auth/csrf');
  const login = action === 'login';
  return apiRequest(`/api/auth/${action}`, {
    method: 'POST',
    headers: {
      [csrf.headerName]: csrf.token,
      'Content-Type': login ? 'application/x-www-form-urlencoded' : 'application/json'
    },
    body: data ? (login ? new URLSearchParams(data) : JSON.stringify(data)) : undefined
  });
}

export async function writeJson(path, method, data) {
  const csrf = await apiRequest('/api/auth/csrf');
  return apiRequest(path, {
    method,
    headers: {
      [csrf.headerName]: csrf.token,
      ...(data === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    body: data === undefined ? undefined : JSON.stringify(data)
  });
}

export async function uploadPhoto(path, file) {
  const csrf = await apiRequest('/api/auth/csrf');
  const body = new FormData();
  body.append('file', file);
  return apiRequest(path, { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body });
}
export function requestIdentity() {
  let fingerprint = '',
    key = crypto.randomUUID();
  return (payload) => {
    const next = JSON.stringify(payload);
    if (fingerprint && fingerprint !== next) key = crypto.randomUUID();
    fingerprint = next;
    return key;
  };
}

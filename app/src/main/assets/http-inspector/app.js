(() => {
  'use strict';
  let bearerToken = null;
  const form = document.querySelector('#pair-form');
  const actions = document.querySelector('.actions');
  const result = document.querySelector('#result');

  const show = (value) => {
    result.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  };

  const api = async (path, body = {}) => {
    const response = await fetch(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
      },
      body: JSON.stringify(body),
      cache: 'no-store',
      credentials: 'omit',
    });
    const value = await response.json();
    if (!response.ok) throw new Error(value.code || 'REQUEST_FAILED');
    return value;
  };

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const paired = await api('/api/session/pair', {
        code: document.querySelector('#pair-code').value,
      });
      bearerToken = paired.token;
      form.hidden = true;
      actions.hidden = false;
      show({ expiresAtMillis: paired.expiresAtMillis, scopes: paired.scopes });
    } catch (error) {
      show(error.message);
    }
  });

  document.querySelector('#server-info').addEventListener('click', async () => {
    try { show(await api('/api/getServerInfo')); } catch (error) { show(error.message); }
  });
  document.querySelector('#snapshot-list').addEventListener('click', async () => {
    try { show(await api('/api/getSnapshots')); } catch (error) { show(error.message); }
  });
})();

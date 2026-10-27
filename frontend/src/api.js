const BASE = '/api'

async function handle(res) {
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    throw new Error(data.error || `Request failed (${res.status})`)
  }
  return data
}

export const api = {
  search: (vector, k, metric, algo) =>
    fetch(`${BASE}/search?v=${vector.join(',')}&k=${k}&metric=${metric}&algo=${algo}`).then(handle),

  benchmark: (vector, k, metric) =>
    fetch(`${BASE}/benchmark?v=${vector.join(',')}&k=${k}&metric=${metric}`).then(handle),

  items: () => fetch(`${BASE}/items`).then(handle),

  hnswInfo: () => fetch(`${BASE}/hnsw-info`).then(handle),

  stats: () => fetch(`${BASE}/stats`).then(handle),

  status: () => fetch(`${BASE}/status`).then(handle),

  docInsert: (title, text) =>
    fetch(`${BASE}/doc/insert`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, text }),
    }).then(handle),

  docList: () => fetch(`${BASE}/doc/list`).then(handle),

  docDelete: (id) => fetch(`${BASE}/doc/delete/${id}`, { method: 'DELETE' }).then(handle),

  ask: (question, k) =>
    fetch(`${BASE}/doc/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, k }),
    }).then(handle),
}

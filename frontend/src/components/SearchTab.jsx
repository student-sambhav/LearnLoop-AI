import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { textToEmbedding } from '../textToEmbedding.js'
import ScatterPlot from './ScatterPlot.jsx'

const ALGOS = ['hnsw', 'kdtree', 'bruteforce']
const METRICS = ['cosine', 'euclidean', 'manhattan']

export default function SearchTab() {
  const [items, setItems] = useState([])
  const [query, setQuery] = useState('binary tree algorithm')
  const [algo, setAlgo] = useState('hnsw')
  const [metric, setMetric] = useState('cosine')
  const [results, setResults] = useState(null)
  const [bench, setBench] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api.items().then(setItems).catch((e) => setError(e.message))
  }, [])

  async function runSearch() {
    setError(null)
    setLoading(true)
    setBench(null)
    try {
      const vector = textToEmbedding(query || 'binary tree algorithm')
      const res = await api.search(vector, 5, metric, algo)
      setResults(res)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function compareAll() {
    setError(null)
    setLoading(true)
    try {
      const vector = textToEmbedding(query || 'binary tree algorithm')
      const [res, b] = await Promise.all([
        api.search(vector, 5, metric, algo),
        api.benchmark(vector, 5, metric),
      ])
      setResults(res)
      setBench(b)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const fastest = bench
    ? Object.entries({ bruteforce: bench.bruteforceUs, kdtree: bench.kdtreeUs, hnsw: bench.hnswUs })
        .sort((a, b) => a[1] - b[1])[0][0]
    : null

  return (
    <>
      <div className="panel">
        <p className="panel-title">Query</p>
        <div className="row">
          <input
            type="text"
            style={{ flex: 1, minWidth: 220 }}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="binary tree, sushi, basketball…"
            onKeyDown={(e) => e.key === 'Enter' && runSearch()}
          />
          <div className="pill-select">
            {ALGOS.map((a) => (
              <button key={a} className={algo === a ? 'active' : ''} onClick={() => setAlgo(a)}>
                {a}
              </button>
            ))}
          </div>
          <div className="pill-select">
            {METRICS.map((m) => (
              <button key={m} className={metric === m ? 'active' : ''} onClick={() => setMetric(m)}>
                {m}
              </button>
            ))}
          </div>
        </div>
        <div className="row" style={{ marginTop: 14 }}>
          <button className="primary" onClick={runSearch} disabled={loading}>
            ⚡ Search
          </button>
          <button className="ghost" onClick={compareAll} disabled={loading}>
            ▶ Compare all algos
          </button>
          {results && <span className="latency-badge">{results.latencyUs}µs · {results.algo}</span>}
        </div>
        {error && <div className="error-banner">{error}</div>}
      </div>

      {bench && (
        <div className="panel">
          <p className="panel-title">Benchmark</p>
          <div className="bench-grid">
            {[
              ['bruteforce', bench.bruteforceUs],
              ['kdtree', bench.kdtreeUs],
              ['hnsw', bench.hnswUs],
            ].map(([name, us]) => (
              <div key={name} className={`bench-card ${name === fastest ? 'fastest' : ''}`}>
                <div className="algo-name">{name}</div>
                <div className="algo-time">{us}µs</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="panel">
        <p className="panel-title">Semantic space (PCA, 16D → 2D)</p>
        <ScatterPlot items={items} highlightIds={results ? results.results.map((r) => r.id) : []} />
      </div>

      {results && (
        <div className="panel">
          <p className="panel-title">Results</p>
          <div className="hit-list">
            {results.results.map((r, i) => (
              <div className="hit-row" key={r.id}>
                <span className="hit-rank">{i + 1}</span>
                <div className="hit-meta">
                  {r.metadata}
                  <div className="hit-cat">{r.category}</div>
                </div>
                <span className="hit-dist">{r.distance.toFixed(4)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  )
}

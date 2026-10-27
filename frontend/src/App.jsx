import { useEffect, useState } from 'react'
import { api } from './api.js'
import SearchTab from './components/SearchTab.jsx'
import DocumentsTab from './components/DocumentsTab.jsx'
import AskAITab from './components/AskAITab.jsx'

const TABS = [
  { id: 'search', label: 'Search' },
  { id: 'docs', label: 'Documents' },
  { id: 'ask', label: 'Ask AI' },
]

export default function App() {
  const [tab, setTab] = useState('search')
  const [status, setStatus] = useState(null)

  useEffect(() => {
    const load = () => api.status().then(setStatus).catch(() => setStatus(null))
    load()
    const interval = setInterval(load, 10000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="app">
      <div className="app-header">
        <div>
          <h1 className="app-title">
            <span className="dot" />
            VectorDB
          </h1>
          <div className="app-subtitle">HNSW · KD-Tree · Brute Force — Spring Boot + Postgres + React</div>
        </div>
        {status && (
          <span className={`status-pill ${status.ollamaAvailable ? 'online' : 'offline'}`}>
            <span className="led" />
            Ollama {status.ollamaAvailable ? 'online' : 'offline'} · {status.demoCount} demo vectors · {status.docCount} chunks
          </span>
        )}
      </div>

      <div className="tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            className={`tab-btn ${tab === t.id ? 'active' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'search' && <SearchTab />}
      {tab === 'docs' && <DocumentsTab />}
      {tab === 'ask' && <AskAITab />}
    </div>
  )
}

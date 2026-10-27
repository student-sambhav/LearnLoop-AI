import { useEffect, useState } from 'react'
import { api } from '../api.js'

export default function DocumentsTab() {
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [docs, setDocs] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [lastResult, setLastResult] = useState(null)

  function refreshList() {
    api.docList().then(setDocs).catch((e) => setError(e.message))
  }

  useEffect(() => { refreshList() }, [])

  async function embedAndInsert() {
    if (!title.trim() || !text.trim()) {
      setError('Add a title and some text first.')
      return
    }
    setError(null)
    setLoading(true)
    setLastResult(null)
    try {
      const res = await api.docInsert(title, text)
      setLastResult(res)
      setTitle('')
      setText('')
      refreshList()
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function deleteChunk(id) {
    try {
      await api.docDelete(id)
      refreshList()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <div className="panel">
        <p className="panel-title">Embed a document</p>
        <label className="field-label">Title</label>
        <input
          type="text"
          style={{ width: '100%', marginBottom: 12 }}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Operating Systems Notes"
        />
        <label className="field-label">Text</label>
        <textarea
          style={{ width: '100%' }}
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Paste lecture notes, textbook paragraphs, or any text…"
        />
        <div className="row" style={{ marginTop: 14 }}>
          <button className="primary" onClick={embedAndInsert} disabled={loading}>
            {loading ? 'Embedding…' : '⚡ Embed & insert'}
          </button>
          {lastResult && (
            <span className="latency-badge">
              {lastResult.chunks} chunk{lastResult.chunks !== 1 ? 's' : ''} · {lastResult.dims}D
            </span>
          )}
        </div>
        {error && <div className="error-banner">{error}</div>}
      </div>

      <div className="panel">
        <p className="panel-title">Stored documents ({docs.length} chunks)</p>
        {docs.length === 0 ? (
          <div className="empty-state">No documents embedded yet.</div>
        ) : (
          <div className="doc-list">
            {docs.map((d) => (
              <div className="doc-row" key={d.id}>
                <div>
                  <div className="doc-title">{d.title}</div>
                  <div className="doc-preview">{d.preview}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
                  <span className="doc-words">{d.words} words</span>
                  <button className="ghost" onClick={() => deleteChunk(d.id)}>delete</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}

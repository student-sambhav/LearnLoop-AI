import { useState } from 'react'
import { api } from '../api.js'

export default function AskAITab() {
  const [question, setQuestion] = useState('')
  const [log, setLog] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [openContext, setOpenContext] = useState(null)

  async function ask() {
    if (!question.trim()) return
    const q = question
    setQuestion('')
    setError(null)
    setLog((prev) => [...prev, { role: 'question', text: q }])
    setLoading(true)
    try {
      const res = await api.ask(q, 3)
      setLog((prev) => [...prev, { role: 'answer', text: res.answer, contexts: res.contexts, model: res.model }])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="panel">
      <p className="panel-title">Ask AI — RAG over your documents</p>

      <div className="chat-log">
        {log.length === 0 && (
          <div className="empty-state">
            Insert some documents in the Documents tab first, then ask a question about them here.
          </div>
        )}
        {log.map((entry, i) => (
          <div key={i} className={`chat-bubble ${entry.role}`}>
            {entry.text}
            {entry.role === 'answer' && entry.contexts && entry.contexts.length > 0 && (
              <div>
                {entry.contexts.map((c) => (
                  <span
                    key={c.id}
                    className="context-chip"
                    onClick={() => setOpenContext(openContext === c.id ? null : c.id)}
                  >
                    [{c.title}] {c.distance.toFixed(3)}
                  </span>
                ))}
                {openContext && entry.contexts.find((c) => c.id === openContext) && (
                  <div className="doc-preview" style={{ marginTop: 10 }}>
                    {entry.contexts.find((c) => c.id === openContext).text}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="row">
        <input
          type="text"
          style={{ flex: 1 }}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="What is dynamic programming?"
          onKeyDown={(e) => e.key === 'Enter' && ask()}
        />
        <button className="primary" onClick={ask} disabled={loading}>
          {loading ? 'Thinking…' : '🤖 Ask AI'}
        </button>
      </div>
    </div>
  )
}

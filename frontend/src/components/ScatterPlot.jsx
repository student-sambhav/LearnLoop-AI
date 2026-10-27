import { useMemo } from 'react'
import { pca2D } from '../pca.js'

const CATEGORY_COLORS = {
  cs: '#8b7fff',
  math: '#5eead4',
  food: '#ffb86b',
  sports: '#ff6b9d',
}

export default function ScatterPlot({ items, highlightIds = [] }) {
  const points = useMemo(() => {
    if (!items || items.length === 0) return []
    const vectors = items.map((it) => it.embedding)
    const projected = pca2D(vectors)

    const xs = projected.map((p) => p[0])
    const ys = projected.map((p) => p[1])
    const minX = Math.min(...xs), maxX = Math.max(...xs)
    const minY = Math.min(...ys), maxY = Math.max(...ys)
    const pad = 40
    const w = 560, h = 360

    return items.map((item, i) => {
      const [px, py] = projected[i]
      const x = maxX === minX ? w / 2 : pad + ((px - minX) / (maxX - minX)) * (w - 2 * pad)
      const y = maxY === minY ? h / 2 : pad + ((py - minY) / (maxY - minY)) * (h - 2 * pad)
      return { ...item, x, y }
    })
  }, [items])

  if (points.length === 0) {
    return <div className="empty-state">No vectors to plot yet.</div>
  }

  const highlightSet = new Set(highlightIds)

  return (
    <svg viewBox="0 0 560 360" width="100%" height="360" style={{ overflow: 'visible' }}>
      {points.map((p) => {
        const isHighlighted = highlightSet.has(p.id)
        return (
          <g key={p.id}>
            <circle
              cx={p.x}
              cy={p.y}
              r={isHighlighted ? 9 : 5}
              fill={CATEGORY_COLORS[p.category] || '#8a8f9c'}
              opacity={isHighlighted ? 1 : 0.75}
              stroke={isHighlighted ? '#fff' : 'none'}
              strokeWidth={isHighlighted ? 2 : 0}
            >
              {isHighlighted && (
                <animate attributeName="r" values="9;13;9" dur="1.2s" repeatCount="indefinite" />
              )}
            </circle>
          </g>
        )
      })}
      {Object.entries(CATEGORY_COLORS).map(([cat, color], i) => (
        <g key={cat} transform={`translate(${20 + i * 130}, 20)`}>
          <circle cx={0} cy={0} r={4} fill={color} />
          <text x={10} y={4} fill="#838a9c" fontSize="10" fontFamily="IBM Plex Mono, monospace">
            {cat}
          </text>
        </g>
      ))}
    </svg>
  )
}

// Turns a typed concept ("binary tree", "sushi", "basketball") into a 16D
// query vector matching the demo dataset's dimension layout, using simple
// keyword matching. This is NOT a real embedding model - it's how the demo
// tab lets you search the 16D hand-crafted vectors without needing Ollama.
// (Real embeddings come from Ollama in the Documents / Ask AI tabs.)

const KEYWORDS = {
  cs: ['algorithm','data','tree','graph','array','linked','hash','stack','queue','sort','binary','dynamic','programming','recursion','complexity','pointer','node','search','insert','bfs','dfs','heap','trie'],
  math: ['calculus','matrix','probability','theorem','integral','derivative','linear','algebra','equation','function','prime','modular','combinatorics','permutation','eigenvalue','statistics','proof'],
  food: ['food','pizza','sushi','ramen','pasta','recipe','cook','eat','restaurant','dish','ingredient','flavor','spice','noodle','bread','croissant','taco','fish','rice','soup'],
  sports: ['sport','basketball','football','tennis','chess','swim','game','play','score','team','athlete','competition','match','tournament','olympic','dribble','tackle','serve'],
}

const CATEGORY_OFFSET = { cs: 0, math: 4, food: 8, sports: 12 }

export function textToEmbedding(text) {
  const words = text.toLowerCase().trim().split(/\s+/)
  const scores = { cs: 0, math: 0, food: 0, sports: 0 }

  for (const w of words) {
    for (const [cat, kws] of Object.entries(KEYWORDS)) {
      for (const kw of kws) {
        if (w.includes(kw) || kw.startsWith(w)) {
          scores[cat] += 0.35
          break
        }
      }
    }
  }

  const maxScore = Math.max(...Object.values(scores), 0.01)
  const normalize = (v) => Math.min((v / maxScore) * 0.88, 0.94)
  const jitter = () => (Math.random() - 0.5) * 0.04

  const emb = new Array(16).fill(0.08)
  for (const [cat, offset] of Object.entries(CATEGORY_OFFSET)) {
    const score = scores[cat]
    if (score < 0.01) continue
    const base = normalize(score)
    emb[offset] = Math.max(0.05, base + jitter())
    emb[offset + 1] = Math.max(0.05, base + jitter())
    emb[offset + 2] = Math.max(0.05, base * 0.92 + jitter())
    emb[offset + 3] = Math.max(0.05, base * 0.87 + jitter())
  }
  return emb
}

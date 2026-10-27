// Minimal PCA: projects N-dimensional vectors down to 2D so the demo
// vectors' semantic clusters (CS / Math / Food / Sports) can be plotted.
// Implemented with power iteration over the covariance matrix, no
// external math library needed for a 16D demo dataset.

function mean(vectors, dims) {
  const m = new Array(dims).fill(0)
  for (const v of vectors) for (let i = 0; i < dims; i++) m[i] += v[i]
  for (let i = 0; i < dims; i++) m[i] /= vectors.length
  return m
}

function covariance(centered, dims) {
  const cov = Array.from({ length: dims }, () => new Array(dims).fill(0))
  for (const v of centered) {
    for (let i = 0; i < dims; i++) {
      for (let j = 0; j < dims; j++) {
        cov[i][j] += v[i] * v[j]
      }
    }
  }
  const n = centered.length || 1
  for (let i = 0; i < dims; i++) for (let j = 0; j < dims; j++) cov[i][j] /= n
  return cov
}

function matVec(mat, vec) {
  const dims = vec.length
  const out = new Array(dims).fill(0)
  for (let i = 0; i < dims; i++) {
    let s = 0
    for (let j = 0; j < dims; j++) s += mat[i][j] * vec[j]
    out[i] = s
  }
  return out
}

function normalize(v) {
  const norm = Math.sqrt(v.reduce((s, x) => s + x * x, 0)) || 1
  return v.map((x) => x / norm)
}

function dot(a, b) {
  return a.reduce((s, x, i) => s + x * b[i], 0)
}

// Finds the dominant eigenvector of `mat` via power iteration.
function powerIteration(mat, dims, iterations = 100) {
  let v = normalize(new Array(dims).fill(0).map(() => Math.random()))
  for (let it = 0; it < iterations; it++) {
    v = normalize(matVec(mat, v))
  }
  return v
}

// Deflates the covariance matrix by removing the found component,
// so the second call to powerIteration finds the *next* principal axis.
function deflate(mat, eigenvector, dims) {
  const lambda = dot(eigenvector, matVec(mat, eigenvector))
  const out = mat.map((row) => row.slice())
  for (let i = 0; i < dims; i++) {
    for (let j = 0; j < dims; j++) {
      out[i][j] -= lambda * eigenvector[i] * eigenvector[j]
    }
  }
  return out
}

/**
 * Projects an array of N-dimensional vectors (plain arrays of numbers)
 * onto their top 2 principal components. Returns an array of [x, y] pairs.
 */
export function pca2D(vectors) {
  if (vectors.length === 0) return []
  const dims = vectors[0].length
  const m = mean(vectors, dims)
  const centered = vectors.map((v) => v.map((x, i) => x - m[i]))
  const cov = covariance(centered, dims)

  const pc1 = powerIteration(cov, dims)
  const cov2 = deflate(cov, pc1, dims)
  const pc2 = powerIteration(cov2, dims)

  return centered.map((v) => [dot(v, pc1), dot(v, pc2)])
}

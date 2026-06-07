import { request } from './httpClient.js'

export function getEmbeddingModels() {
  return request('/rag/embedding-models')
}

export function createEmbeddingModel(payload) {
  return request('/rag/embedding-models', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function prepareEmbeddings(payload) {
  return request('/rag/embeddings/prepare', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function retrieve(payload) {
  return request('/rag/retrieve', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createRetrievalQuery(payload) {
  return request('/rag/retrieval-queries', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getRetrievalQueries(workspaceId) {
  const params = workspaceId ? `?workspaceId=${workspaceId}` : ''
  return request(`/rag/retrieval-queries${params}`)
}

export function getRetrievalResults(retrievalQueryId) {
  const params = retrievalQueryId ? `?retrievalQueryId=${retrievalQueryId}` : ''
  return request(`/rag/retrieval-results${params}`)
}

export function getCitations(assistantMessageId) {
  const params = assistantMessageId ? `?assistantMessageId=${assistantMessageId}` : ''
  return request(`/rag/citations${params}`)
}

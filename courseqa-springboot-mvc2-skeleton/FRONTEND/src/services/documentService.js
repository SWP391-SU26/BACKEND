import { request } from './httpClient.js'
import { env } from '../config/env.js'

export async function getDocumentsByWorkspace(workspaceId) {
  const documents = await request(`/documents/workspace/${workspaceId}`)
  return Promise.all(documents.map((document) => toUiDocument(document)))
}

export async function getDocument(documentId) {
  const document = await request(`/documents/${documentId}`)
  return toUiDocument(document)
}

export async function getDocumentPages(documentId) {
  return request(`/documents/${documentId}/pages`)
}

export async function getDocumentChunks(documentId) {
  const chunks = await request(`/documents/${documentId}/chunks`)
  return chunks.map(toUiChunk)
}

export function deleteDocument(documentId) {
  return request(`/documents/${documentId}`, {
    method: 'DELETE',
  })
}

export function getDocumentFileUrl(documentId) {
  return `${env.apiBaseUrl}/documents/${documentId}/file`
}

export function getDocumentPreviewUrl(documentId) {
  return `${env.apiBaseUrl}/documents/${documentId}/preview`
}

export async function uploadDocument({ file, workspaceId, courseId, chapterId, uploadedBy }) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('workspaceId', workspaceId)
  formData.append('courseId', courseId)

  if (chapterId) formData.append('chapterId', chapterId)
  if (uploadedBy) formData.append('uploadedBy', uploadedBy)

  const document = await request('/documents/upload', {
    method: 'POST',
    body: formData,
  })

  return toUiDocument(document)
}

export function toUiDocument(document, extra = {}) {
  const status = toUiStatus(document.processingStatus)

  return {
    id: document.documentId,
    name: document.originalFilename,
    displayName: document.documentTitle || document.originalFilename,
    type: document.fileType,
    subject: extra.subject ?? 'Course Workspace',
    chapter: extra.chapter ?? 'General',
    status,
    chunks: extra.chunks ?? 0,
    embeddingModel: status === 'Indexed' ? 'keyword-hash-128' : 'Not embedded',
    uploadedAt: 'From backend',
    size: extra.size ?? 'Stored',
    pages: document.totalPages ?? 0,
    relevance: status === 'Indexed' ? 80 : 0,
    workspaceId: document.workspaceId,
    courseId: document.courseId,
    preview:
      document.errorMessage ??
      'Document processed by Spring Boot: file stored, text extracted, and chunks prepared.',
  }
}

export function toUiChunk(chunk) {
  return {
    id: chunk.chunkId,
    documentId: chunk.documentId,
    page: chunk.pageStart ?? 1,
    tokenLength: chunk.tokenCount ?? 0,
    metadata: chunk.chunkStrategy ?? 'fixed chunk',
    relevance: 80,
    content: chunk.content,
  }
}

function toUiStatus(status) {
  switch (status) {
    case 'INDEXED':
    case 'PROCESSED':
      return 'Indexed'
    case 'PROCESSING':
      return 'Processing'
    case 'FAILED':
      return 'Failed'
    case 'NO_TEXT':
      return 'Uploaded'
    default:
      return status ?? 'Uploaded'
  }
}

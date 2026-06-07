import { motion } from 'framer-motion'
import {
  AlertTriangle,
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  Database,
  FileText,
  HardDrive,
  Layers3,
  Search,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { BentoCard, Button, ConfirmModal, EmptyState, Field, Panel, StatusBadge } from '../components/ui.jsx'
import { chunks, documents } from '../data/mockData.js'

function DocumentDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [activeChunk, setActiveChunk] = useState('C-1024')
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const doc = documents.find((item) => item.id === id)

  const docChunks = useMemo(() => {
    return chunks
      .filter((chunk) => chunk.documentId === id)
      .filter((chunk) => {
        const normalizedQuery = query.trim().toLowerCase()
        return (
          !normalizedQuery ||
          chunk.content.toLowerCase().includes(normalizedQuery) ||
          chunk.metadata.toLowerCase().includes(normalizedQuery) ||
          chunk.id.toLowerCase().includes(normalizedQuery)
        )
      })
  }, [id, query])

  if (!doc) {
    return (
      <EmptyState
        action={
          <Link to="/library">
            <Button variant="secondary">
              <ArrowLeft size={16} />
              Back to Library
            </Button>
          </Link>
        }
        description="This document does not exist in the frontend mock data or has been removed from the list."
        title="Document not found"
      />
    )
  }

  const selectedChunk = docChunks.find((chunk) => chunk.id === activeChunk) ?? docChunks[0]

  return (
    <div className="space-y-4">
      <Panel className="overflow-hidden p-5">
        <div className="pointer-events-none absolute inset-0 opacity-60">
          <div className="abstract-canvas" />
        </div>
        <div className="relative flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-4xl">
            <Link
              className="mb-5 inline-flex items-center gap-2 rounded-full bg-white/72 px-3 py-2 text-sm font-black text-slate-600 shadow-sm transition hover:bg-white hover:text-slate-950"
              to="/library"
            >
              <ArrowLeft size={16} />
              Library
            </Link>
            <div className="flex flex-wrap items-center gap-3">
              <div className="grid size-12 place-items-center rounded-xl bg-teal-50 text-primary shadow-lg shadow-teal-100">
                <FileText size={22} />
              </div>
              <StatusBadge status={doc.status} />
            </div>
            <h1 className="mt-5 text-4xl font-black leading-tight tracking-tight text-slate-950 sm:text-5xl">
              {doc.displayName}
            </h1>
            <p className="mt-3 max-w-3xl text-sm font-semibold leading-6 text-slate-600">
              {doc.preview}
            </p>
          </div>
          <Button onClick={() => setShowDeleteModal(true)} variant="danger">
            <Trash2 size={16} />
            Delete document
          </Button>
        </div>
        <div className="relative mt-5 grid gap-3 sm:grid-cols-3">
          <StatTile label="Pages" value={doc.pages} />
          <StatTile label="Chunks" value={doc.chunks} />
          <StatTile label="Relevance" value={doc.relevance ? `${doc.relevance}%` : 'sync'} />
        </div>
      </Panel>

      <section className="studio-grid">
        <Panel className="overflow-hidden">
          {doc.status === 'Processing' || doc.status === 'Uploaded' ? (
            <Notice tone="amber">
              This file is still processing, so chunk previews may be incomplete. The status will change to Indexed after text extraction, chunking, and embedding finish.
            </Notice>
          ) : null}

          {doc.status === 'Failed' ? (
            <Notice tone="red">
              Indexing failed. Return to Library and run Re-index to simulate another processing attempt.
            </Notice>
          ) : null}

          <div className="border-b border-border p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-xl font-black tracking-tight">Chunk reader</h2>
                <p className="text-sm font-semibold text-slate-500">
                  Select a chunk to sync the preview on the right.
                </p>
              </div>
              <div className="w-full sm:w-80">
                <Field
                  icon={Search}
                  label="Search chunks"
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search chunk content or metadata..."
                  value={query}
                />
              </div>
            </div>
          </div>

          <div className="grid gap-4 p-5 lg:grid-cols-2">
            {docChunks.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-200 p-8 text-center lg:col-span-2">
                <Layers3 className="mx-auto text-slate-300" size={34} />
                <h3 className="mt-3 text-sm font-black text-slate-900">No chunks to display</h3>
                <p className="mt-1 text-sm font-semibold text-slate-500">
                  The file may still be processing, indexing may have failed, or the search does not match any chunks.
                </p>
              </div>
            ) : (
              docChunks.map((chunk, index) => (
                <motion.button
                  className={`bento-card p-4 text-left transition ${
                    selectedChunk?.id === chunk.id ? 'ring-2 ring-teal-400' : ''
                  }`}
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.05, duration: 0.32 }}
                  key={chunk.id}
                  onClick={() => setActiveChunk(chunk.id)}
                  whileHover={{ y: -5, rotateX: 1 }}
                  whileTap={{ scale: 0.985 }}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-700">{chunk.id}</span>
                      <span className="rounded-full bg-teal-50 px-3 py-1 text-xs font-black text-teal-700">Page {chunk.page}</span>
                      <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-700">{chunk.tokenLength} tokens</span>
                    </div>
                    <span className="relevance-ring text-[10px] font-black text-primary" style={{ '--value': `${chunk.relevance}%` }}>
                      {chunk.relevance}%
                    </span>
                  </div>
                  <p className="mt-4 text-sm font-medium leading-7 text-slate-700">{chunk.content}</p>
                  <p className="mt-3 text-xs font-black text-slate-500">Metadata: {chunk.metadata}</p>
                </motion.button>
              ))
            )}
          </div>
        </Panel>

        <aside className="space-y-4">
          <BentoCard className="border-teal-200 bg-teal-50 p-5 text-slate-900">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-base font-black">Active source</p>
                <p className="text-xs font-semibold text-slate-500">Highlighted source preview</p>
              </div>
              <Sparkles className="text-primary" size={18} />
            </div>
            {selectedChunk ? (
              <div className="mt-5">
                <p className="text-4xl font-black">{selectedChunk.id}</p>
                <p className="mt-3 text-sm font-medium leading-7 text-slate-700">
                  {selectedChunk.content}
                </p>
                <div className="mt-4 flex flex-wrap gap-2 text-xs font-black">
                  <span className="rounded-full bg-white px-3 py-1.5 text-slate-600">Page {selectedChunk.page}</span>
                  <span className="rounded-full bg-white px-3 py-1.5 text-slate-600">{selectedChunk.tokenLength} tokens</span>
                </div>
              </div>
            ) : (
              <p className="mt-5 text-sm font-semibold text-slate-500">No matching chunk selected.</p>
            )}
          </BentoCard>

          <Panel className="p-5">
            <h2 className="text-base font-black tracking-tight">Metadata</h2>
            <div className="mt-4 grid gap-3">
              <MetaRow icon={FileText} label="File type" value={doc.type} />
              <MetaRow icon={HardDrive} label="File size" value={doc.size} />
              <MetaRow icon={Layers3} label="Pages" value={doc.pages} />
              <MetaRow icon={Database} label="Chunks" value={doc.chunks} />
              <MetaRow icon={FileText} label="Subject" value={doc.subject} />
              <MetaRow icon={FileText} label="Chapter" value={doc.chapter} />
              <MetaRow icon={CalendarDays} label="Uploaded" value={doc.uploadedAt} />
            </div>
          </Panel>

          <Panel className="p-5">
            <h2 className="text-base font-black tracking-tight">Index timeline</h2>
            <div className="mt-4 space-y-4">
              {['Uploaded', 'Extracting text', 'Chunking', 'Embedding', doc.status].map((step, index) => (
                <div className="timeline-step flex gap-3" key={`${step}-${index}`}>
                  <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary">
                    <CheckCircle2 size={17} />
                  </div>
                  <div>
                    <p className="text-sm font-black text-slate-800">{step}</p>
                    <p className="text-xs font-semibold text-slate-500">Pipeline step {index + 1}</p>
                  </div>
                </div>
              ))}
            </div>
          </Panel>
        </aside>
      </section>

      {showDeleteModal ? (
        <ConfirmModal
          actionLabel="Delete document"
          onCancel={() => setShowDeleteModal(false)}
          onConfirm={() => navigate('/library')}
          title="Delete document?"
        >
          This is a frontend simulation. After confirmation, you will be redirected to Library.
        </ConfirmModal>
      ) : null}
    </div>
  )
}

function StatTile({ label, value }) {
  return (
    <BentoCard className="p-4">
      <div className="mb-4 h-2 w-16 rounded-full bg-primary" />
      <p className="text-4xl font-black tracking-tight text-slate-950">{value}</p>
      <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">{label}</p>
    </BentoCard>
  )
}

function Notice({ children, tone }) {
  const styles =
    tone === 'red'
      ? 'border-red-200 bg-red-50 text-red-800 shadow-red-100'
      : 'border-amber-200 bg-amber-50 text-amber-800 shadow-amber-100'

  return (
    <div className={`m-5 rounded-lg border p-4 text-sm font-semibold leading-6 shadow-lg ${styles}`}>
      <div className="flex gap-3">
        <AlertTriangle className="mt-0.5 shrink-0" size={17} />
        {children}
      </div>
    </div>
  )
}

function MetaRow({ icon: Icon, label, value }) {
  return (
    <motion.div className="bento-card flex items-center gap-3 px-3 py-3" whileHover={{ x: 3, y: -2 }}>
      <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary shadow-sm">
        <Icon size={17} />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-black uppercase tracking-[0.12em] text-slate-400">{label}</p>
        <p className="truncate text-sm font-black text-slate-800">{value}</p>
      </div>
    </motion.div>
  )
}

export default DocumentDetailPage

import { AnimatePresence, motion } from 'framer-motion'
import {
  ArrowUpDown,
  Database,
  Eye,
  FilePlus2,
  FileText,
  Grid2X2,
  PencilLine,
  RefreshCcw,
  Search,
  Table2,
  Trash2,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  EmptyState,
  Field,
  IconButton,
  Panel,
  SelectField,
  StatusBadge,
} from '../components/ui.jsx'
import { getSavedUser } from '../services/authService.js'
import { getWorkspaces } from '../services/courseService.js'
import {
  deleteDocument,
  getDocumentChunks,
  getDocumentFileUrl,
  getDocumentsByWorkspace,
  uploadDocument,
} from '../services/documentService.js'
import { cn } from '../utils/cn.js'

const allOption = 'All'
const fileTypes = [allOption, 'PDF', 'DOCX', 'PPTX']
const statuses = [allOption, 'Uploaded', 'Processing', 'Indexed', 'Failed']

function LibraryPage() {
  const [docs, setDocs] = useState([])
  const [workspaceList, setWorkspaceList] = useState([])
  const [activeWorkspaceId, setActiveWorkspaceId] = useState('')
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [subject, setSubject] = useState(allOption)
  const [chapter, setChapter] = useState(allOption)
  const [type, setType] = useState(allOption)
  const [status, setStatus] = useState(allOption)
  const [date, setDate] = useState(allOption)
  const [viewMode, setViewMode] = useState('bento')
  const [docToDelete, setDocToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [renamingDoc, setRenamingDoc] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  const fileInputRef = useRef(null)

  const chapters = useMemo(() => [allOption, ...Array.from(new Set(docs.map((doc) => doc.chapter)))], [docs])
  const subjects = useMemo(() => [allOption, ...workspaceList.map((workspace) => workspace.name)], [workspaceList])

  useEffect(() => {
    let isMounted = true

    async function loadLibrary() {
      setLoading(true)
      setError('')

      try {
        const workspaces = await getWorkspaces()
        if (!isMounted) return

        setWorkspaceList(workspaces)
        const selectedWorkspaceId = activeWorkspaceId || workspaces[0]?.id || ''
        setActiveWorkspaceId(selectedWorkspaceId)

        if (!selectedWorkspaceId) {
          setDocs([])
          return
        }

        const documents = await getDocumentsByWorkspace(selectedWorkspaceId)
        const documentsWithChunks = await Promise.all(
          documents.map(async (doc) => {
            const chunks = await getDocumentChunks(doc.id).catch(() => [])
            const workspace = workspaces.find((item) => item.id === doc.workspaceId)
            return {
              ...doc,
              subject: workspace?.name ?? doc.subject,
              chunks: chunks.length,
              embeddingModel: chunks.length > 0 ? doc.embeddingModel : 'Not embedded',
            }
          }),
        )

        if (isMounted) setDocs(documentsWithChunks)
      } catch (loadError) {
        if (isMounted) setError(loadError.message)
      } finally {
        if (isMounted) setLoading(false)
      }
    }

    loadLibrary()

    return () => {
      isMounted = false
    }
  }, [activeWorkspaceId])

  const filteredDocs = useMemo(() => {
    return docs.filter((doc) => {
      const normalizedQuery = query.trim().toLowerCase()
      const matchesQuery =
        !normalizedQuery ||
        doc.displayName.toLowerCase().includes(normalizedQuery) ||
        doc.preview.toLowerCase().includes(normalizedQuery)
      const matchesSubject = subject === allOption || doc.subject === subject
      const matchesChapter = chapter === allOption || doc.chapter === chapter
      const matchesType = type === allOption || doc.type === type
      const matchesStatus = status === allOption || doc.status === status
      const matchesDate =
        date === allOption ||
        (date === 'Today' && doc.uploadedAt.startsWith('May 27, 2026')) ||
        (date === 'This week' && doc.uploadedAt.includes('May'))

      return matchesQuery && matchesSubject && matchesChapter && matchesType && matchesStatus && matchesDate
    })
  }, [chapter, date, docs, query, status, subject, type])

  function resetFilters() {
    setQuery('')
    setSubject(allOption)
    setChapter(allOption)
    setType(allOption)
    setStatus(allOption)
    setDate(allOption)
  }

  async function handleUpload(files) {
    const activeWorkspace = workspaceList.find((workspace) => workspace.id === activeWorkspaceId)
    const acceptedFiles = Array.from(files ?? []).filter((file) => /\.(pdf|docx|pptx|txt)$/i.test(file.name))

    if (!activeWorkspace) {
      setError('Bạn cần tạo course workspace trước khi upload tài liệu.')
      return
    }

    if (acceptedFiles.length === 0) {
      setError('Chỉ hỗ trợ PDF, DOCX, PPTX, TXT.')
      return
    }

    setUploading(true)
    setError('')

    try {
      const user = getSavedUser()
      const uploadedDocuments = await Promise.all(
        acceptedFiles.map((file) =>
          uploadDocument({
            file,
            workspaceId: activeWorkspace.id,
            courseId: activeWorkspace.courseId,
            uploadedBy: user?.id,
          }),
        ),
      )

      const enrichedDocuments = await Promise.all(
        uploadedDocuments.map(async (doc) => {
          const chunks = await getDocumentChunks(doc.id).catch(() => [])
          return {
            ...doc,
            subject: activeWorkspace.name,
            chunks: chunks.length,
            embeddingModel: chunks.length > 0 ? doc.embeddingModel : 'Not embedded',
          }
        }),
      )

      setDocs((current) => [...enrichedDocuments, ...current])
    } catch (uploadError) {
      setError(uploadError.message)
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  function startRename(doc) {
    setRenamingDoc(doc.id)
    setRenameValue(doc.displayName)
  }

  function commitRename() {
    setDocs((current) =>
      current.map((doc) =>
        doc.id === renamingDoc ? { ...doc, displayName: renameValue || doc.displayName } : doc,
      ),
    )
    setRenamingDoc(null)
    setRenameValue('')
  }

  function reindexDoc(docId) {
    setDocs((current) =>
      current.map((doc) => (doc.id === docId ? { ...doc, status: 'Processing' } : doc)),
    )
    window.setTimeout(() => {
      setDocs((current) =>
        current.map((doc) =>
          doc.id === docId
            ? {
                ...doc,
                status: 'Indexed',
                chunks: doc.chunks || 28,
                embeddingModel: 'text-embedding-3-small',
                relevance: doc.relevance || 82,
              }
            : doc,
        ),
      )
    }, 1000)
  }

  async function confirmDeleteDocument() {
    if (!docToDelete) return

    setDeleting(true)
    setError('')

    try {
      await deleteDocument(docToDelete.id)
      setDocs((current) => current.filter((doc) => doc.id !== docToDelete.id))
      setDocToDelete(null)
    } catch (deleteError) {
      setError(deleteError.message)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-4">
      <Panel className="overflow-hidden p-5">
        <div className="pointer-events-none absolute inset-0 opacity-60">
          <div className="abstract-canvas" />
        </div>
        <div className="relative flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-4xl font-black tracking-tight text-slate-950 sm:text-5xl">
              Knowledge Library
            </h1>
            <p className="mt-2 max-w-2xl text-sm font-semibold leading-6 text-slate-600">
              Manage uploaded sources, filter quickly, re-index documents, and open chunk-level readers from one workspace.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <SelectField label="Workspace" onChange={(event) => setActiveWorkspaceId(event.target.value)} value={activeWorkspaceId}>
              {workspaceList.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
            </SelectField>
            <Button disabled={uploading || !activeWorkspaceId} onClick={() => fileInputRef.current?.click()}>
              <FilePlus2 size={17} />
              {uploading ? 'Uploading...' : 'Upload document'}
            </Button>
            <Button onClick={resetFilters} variant="secondary">
              <RefreshCcw size={16} />
              Reset
            </Button>
          </div>
        </div>
        <input
          accept=".pdf,.docx,.pptx,.txt"
          className="sr-only"
          multiple
          onChange={(event) => handleUpload(event.target.files)}
          ref={fileInputRef}
          type="file"
        />
        {error ? (
          <div className="relative mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">
            {error}
          </div>
        ) : null}
        {loading ? (
          <div className="relative mt-4 rounded-lg border border-teal-100 bg-teal-50 p-3 text-sm font-semibold text-teal-700">
            Loading documents from backend...
          </div>
        ) : null}
        <div className="relative mt-5 grid gap-3 md:grid-cols-3">
          <StatCard icon={FileText} label="Documents" value={docs.length} />
          <StatCard icon={Database} label="Indexed" value={docs.filter((doc) => doc.status === 'Indexed').length} />
          <StatCard icon={LayersIcon} label="Chunks" value={docs.reduce((total, doc) => total + doc.chunks, 0)} />
        </div>
      </Panel>

      <Panel className="sticky top-[78px] z-30 p-3 sm:p-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[1.35fr_1fr_1fr_1fr_1fr_1fr_auto]">
          <Field
            icon={Search}
            label="Search documents"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search by filename or content..."
            value={query}
          />
          <SelectField label="Subject" onChange={(event) => setSubject(event.target.value)} value={subject}>
            {subjects.map((item) => <option key={item}>{item}</option>)}
          </SelectField>
          <SelectField label="Chapter" onChange={(event) => setChapter(event.target.value)} value={chapter}>
            {chapters.map((item) => <option key={item}>{item}</option>)}
          </SelectField>
          <SelectField label="File type" onChange={(event) => setType(event.target.value)} value={type}>
            {fileTypes.map((item) => <option key={item}>{item}</option>)}
          </SelectField>
          <SelectField label="Status" onChange={(event) => setStatus(event.target.value)} value={status}>
            {statuses.map((item) => <option key={item}>{item}</option>)}
          </SelectField>
          <SelectField label="Uploaded" onChange={(event) => setDate(event.target.value)} value={date}>
            {[allOption, 'Today', 'This week'].map((item) => <option key={item}>{item}</option>)}
          </SelectField>
          <div className="flex rounded-lg bg-white/72 p-1 shadow-inner">
            <IconButton
              className={viewMode === 'bento' ? 'bg-primary text-white hover:bg-teal-800 hover:text-white' : ''}
              label="Bento view"
              onClick={() => setViewMode('bento')}
            >
              <Grid2X2 size={15} />
            </IconButton>
            <IconButton
              className={viewMode === 'table' ? 'bg-primary text-white hover:bg-teal-800 hover:text-white' : ''}
              label="Table view"
              onClick={() => setViewMode('table')}
            >
              <Table2 size={15} />
            </IconButton>
          </div>
        </div>
      </Panel>

      {docs.length === 0 ? (
        <EmptyState
          action={<Button onClick={() => fileInputRef.current?.click()}><FilePlus2 size={17} />Upload first document</Button>}
          description="The library has no documents yet. Upload a PDF, DOCX, PPTX, or TXT file to start indexing and source-grounded chat."
          title="No documents yet"
        />
      ) : filteredDocs.length === 0 ? (
        <EmptyState
          action={<Button onClick={resetFilters} variant="secondary">Clear filters</Button>}
          description="No documents match the current filters. Try another keyword or broaden the filters."
          title="No matching documents"
        />
      ) : viewMode === 'bento' ? (
        <DocumentCards docs={filteredDocs} onDelete={setDocToDelete} onReindex={reindexDoc} onRename={startRename} />
      ) : (
        <DocumentTable docs={filteredDocs} onDelete={setDocToDelete} onReindex={reindexDoc} onRename={startRename} />
      )}

      {viewMode === 'bento' && filteredDocs.length > 0 ? (
        <DocumentTable docs={filteredDocs} onDelete={setDocToDelete} onReindex={reindexDoc} onRename={startRename} compact />
      ) : null}

      {docToDelete ? (
        <ConfirmModal
          actionLabel="Delete document"
          onCancel={() => setDocToDelete(null)}
          onConfirm={confirmDeleteDocument}
          title="Delete document?"
        >
          "{docToDelete.displayName}" will be removed from the database and local upload folder.
          {deleting ? ' Deleting...' : ''}
        </ConfirmModal>
      ) : null}

      <AnimatePresence>
        {renamingDoc ? (
          <motion.div
            className="fixed inset-0 z-50 grid place-items-center bg-slate-950/30 p-4 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.div
              className="os-panel w-full max-w-md p-5 shadow-xl"
              initial={{ opacity: 0, y: 24, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.98 }}
            >
              <h2 className="text-lg font-black tracking-tight text-slate-950">Rename document</h2>
              <p className="mt-2 text-sm font-semibold text-slate-600">
                The new display name will be updated in the local list.
              </p>
              <input
                autoFocus
                className="mt-4 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm font-semibold outline-none transition focus:border-teal-400 focus:ring-4 focus:ring-teal-100"
                onChange={(event) => setRenameValue(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') commitRename()
                }}
                value={renameValue}
              />
              <div className="mt-5 flex justify-end gap-2">
                <Button onClick={() => { setRenamingDoc(null); setRenameValue('') }} variant="secondary">
                  Cancel
                </Button>
                <Button onClick={commitRename}>Save name</Button>
              </div>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

function LayersIcon(props) {
  return <Database {...props} />
}

function StatCard({ icon: Icon, label, value }) {
  return (
    <motion.div className="bento-card p-4" whileHover={{ y: -4 }}>
      <div className="flex items-center justify-between">
        <div className="grid size-10 place-items-center rounded-xl bg-teal-50 text-primary">
          <Icon size={17} />
        </div>
        <p className="text-3xl font-black">{value}</p>
      </div>
      <p className="mt-3 text-xs font-black uppercase tracking-[0.14em] text-slate-500">{label}</p>
    </motion.div>
  )
}

function DocumentCards({ docs, onDelete, onReindex, onRename }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {docs.map((doc, index) => (
        <motion.article
          className="bento-card p-4"
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: index * 0.03, duration: 0.32 }}
          key={doc.id}
          whileHover={{ y: -6, rotateX: 1.2 }}
        >
          <div className="flex items-start gap-3">
            <div className="grid size-11 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary shadow-sm">
              <FileText size={18} />
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="truncate text-sm font-black text-slate-950">{doc.displayName}</h2>
              <p className="mt-1 text-xs font-semibold text-slate-500">{doc.subject} / {doc.chapter}</p>
            </div>
            <StatusBadge status={doc.status} />
          </div>
          <p className="mt-4 line-clamp-3 min-h-[4.5rem] text-sm font-medium leading-6 text-slate-600">
            {doc.preview}
          </p>
          <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
            <span className="rounded-lg bg-teal-50 px-2 py-2 font-black text-teal-700">{doc.type}</span>
            <span className="rounded-lg bg-emerald-50 px-2 py-2 font-black text-emerald-700">{doc.chunks} chunks</span>
            <span className="rounded-lg bg-slate-100 px-2 py-2 font-black text-slate-600">{doc.size}</span>
          </div>
          <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
            <motion.div
              className="shimmer-line h-full rounded-full bg-gradient-to-r from-primary via-teal-400 to-emerald-300"
              initial={{ width: 0 }}
              animate={{ width: `${doc.relevance || (doc.status === 'Indexed' ? 78 : 26)}%` }}
              transition={{ duration: 0.6, delay: index * 0.04 }}
            />
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link className="flex-1" to={`/library/documents/${doc.id}`}>
              <Button className="w-full" variant="secondary"><Eye size={16} />View</Button>
            </Link>
            <IconButton label="Open original file" onClick={() => window.open(getDocumentFileUrl(doc.id), '_blank', 'noopener,noreferrer')}><FileText size={16} /></IconButton>
            <IconButton label="Rename" onClick={() => onRename(doc)}><PencilLine size={16} /></IconButton>
            <IconButton label="Re-index" onClick={() => onReindex(doc.id)}><RefreshCcw size={16} /></IconButton>
            <IconButton label="Delete" onClick={() => onDelete(doc)}><Trash2 size={16} /></IconButton>
          </div>
        </motion.article>
      ))}
    </div>
  )
}

function DocumentTable({ compact = false, docs, onDelete, onReindex, onRename }) {
  return (
    <Panel className={cn('overflow-hidden', compact ? 'hidden xl:block' : '')}>
      <div className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2 className="text-lg font-black tracking-tight">Table mode</h2>
          <p className="text-sm font-semibold text-slate-500">Scan metadata and actions at a glance.</p>
        </div>
        <Table2 className="text-slate-400" size={20} />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1100px] border-collapse text-left text-sm">
          <thead className="bg-white/52 text-xs font-black uppercase tracking-[0.12em] text-slate-500">
            <tr>
              {['Document', 'Type', 'Subject', 'Chapter', 'Status', 'Chunks', 'Embedding', 'Uploaded', 'Actions'].map((heading) => (
                <th className="border-b border-slate-200 px-4 py-3" key={heading}>
                  <span className="inline-flex items-center gap-1">
                    {heading}
                    {heading === 'Document' ? <ArrowUpDown size={13} /> : null}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {docs.map((doc) => (
              <motion.tr className="bg-white/70 transition hover:bg-teal-50/70" key={doc.id} whileHover={{ scale: 1.003 }}>
                <td className="max-w-[260px] px-4 py-4">
                  <div className="flex items-center gap-3">
                    <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary">
                      <FileText size={17} />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-black text-slate-950">{doc.displayName}</p>
                      <p className="truncate text-xs font-semibold text-slate-500">{doc.preview}</p>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-4 font-black text-slate-600">{doc.type}</td>
                <td className="px-4 py-4 text-slate-600">{doc.subject}</td>
                <td className="px-4 py-4 text-slate-600">{doc.chapter}</td>
                <td className="px-4 py-4"><StatusBadge status={doc.status} /></td>
                <td className="px-4 py-4 font-semibold text-slate-600">{doc.chunks}</td>
                <td className="px-4 py-4 text-xs font-semibold text-slate-500">{doc.embeddingModel}</td>
                <td className="px-4 py-4 text-slate-600">{doc.uploadedAt}</td>
                <td className="px-4 py-4">
                  <div className="flex items-center gap-1">
                    <Link to={`/library/documents/${doc.id}`}><IconButton label="View details"><Eye size={15} /></IconButton></Link>
                    <IconButton label="Open original file" onClick={() => window.open(getDocumentFileUrl(doc.id), '_blank', 'noopener,noreferrer')}><FileText size={15} /></IconButton>
                    <IconButton label="Rename" onClick={() => onRename(doc)}><PencilLine size={15} /></IconButton>
                    <IconButton label="Re-index" onClick={() => onReindex(doc.id)}><RefreshCcw size={15} /></IconButton>
                    <IconButton label="Delete" onClick={() => onDelete(doc)}><Trash2 size={15} /></IconButton>
                  </div>
                </td>
              </motion.tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

export default LibraryPage

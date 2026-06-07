import { AnimatePresence, motion, useScroll, useTransform } from 'framer-motion'
import {
  Bot,
  Check,
  Clipboard,
  FileText,
  History,
  Info,
  Layers3,
  Loader2,
  MessageSquarePlus,
  PencilLine,
  RefreshCcw,
  Search,
  Send,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  Upload,
  UserRound,
  X,
} from 'lucide-react'
import { useMemo, useRef, useState } from 'react'
import { Button, ConfirmModal, IconButton, StatusBadge } from '../components/ui.jsx'
import {
  chatSessions,
  chunks,
  documents,
  initialMessages,
  uploadSteps,
  workspaces,
} from '../data/mockData.js'
import { cn } from '../utils/cn.js'

const suggestions = [
  'Summarize chapter 1 in five key points',
  'Compare agents and knowledge bases',
  'Create six review questions with answers',
]

function WorkspacePage() {
  const { scrollY } = useScroll()
  const parallaxY = useTransform(scrollY, [0, 520], [0, -28])
  const [activeWorkspace, setActiveWorkspace] = useState(workspaces[0].id)
  const [sessions, setSessions] = useState(chatSessions)
  const [selectedSession, setSelectedSession] = useState(chatSessions[0].id)
  const [messages, setMessages] = useState(initialMessages)
  const [input, setInput] = useState('')
  const [activeCitation, setActiveCitation] = useState('C-1024')
  const [isAnswering, setIsAnswering] = useState(false)
  const [warning, setWarning] = useState('')
  const [feedback, setFeedback] = useState({})
  const [copiedId, setCopiedId] = useState('')
  const [uploadJobs, setUploadJobs] = useState([])
  const [sessionToDelete, setSessionToDelete] = useState(null)
  const [renamingSession, setRenamingSession] = useState('')
  const [renameValue, setRenameValue] = useState('')
  const fileInputRef = useRef(null)

  const workspaceDocs = useMemo(
    () => documents.filter((doc) => doc.workspaceId === activeWorkspace),
    [activeWorkspace],
  )
  const indexedDocs = workspaceDocs.filter((doc) => doc.status === 'Indexed')
  const sessionList = sessions.filter((session) => session.workspaceId === activeWorkspace)
  const activeChunks = chunks.filter((chunk) =>
    messages.some((message) => message.citations?.includes(chunk.id)),
  )
  const highlightedChunk =
    chunks.find((chunk) => chunk.id === activeCitation) ?? activeChunks[0]
  const canChat = indexedDocs.length > 0 && !isAnswering

  function handleNewSession() {
    const nextSession = {
      id: `session-${Date.now()}`,
      title: 'New chat session',
      updatedAt: 'Just now',
      workspaceId: activeWorkspace,
    }
    setSessions((current) => [nextSession, ...current])
    setSelectedSession(nextSession.id)
    setMessages([
      {
        id: `m-${Date.now()}`,
        role: 'assistant',
        content:
          'Your new session is ready. I will answer only from the indexed documents in this workspace.',
        citations: [],
      },
    ])
    setActiveCitation('')
    setWarning('')
  }

  function handleSubmit(event) {
    event.preventDefault()
    const question = input.trim()

    if (!question) {
      setWarning('Enter a question before sending.')
      return
    }
    if (workspaceDocs.length === 0) {
      setWarning('This workspace has no documents. Upload a file first.')
      return
    }
    if (indexedDocs.length === 0) {
      setWarning('Documents are still processing. Wait for an Indexed source before chatting.')
      return
    }

    setInput('')
    setWarning('')
    setMessages((current) => [
      ...current,
      { id: `m-user-${Date.now()}`, role: 'user', content: question },
    ])
    setIsAnswering(true)

    window.setTimeout(() => {
      const lowerQuestion = question.toLowerCase()
      const noSource =
        lowerQuestion.includes('weather') || lowerQuestion.includes('outside the sources')
      setMessages((current) => [
        ...current,
        {
          id: `m-ai-${Date.now()}`,
          role: 'assistant',
          content: noSource
            ? 'I could not find a relevant source in the indexed documents. To stay grounded in the material, I will not infer beyond the available sources.'
            : 'Based on the relevant source chunks, this concept should be interpreted through its context, evaluation goal, and supporting knowledge. Open a citation to review the exact chunk.',
          citations: noSource ? [] : ['C-1024', 'C-1031'],
          confidence: noSource ? 'low' : 'good',
        },
      ])
      setActiveCitation(noSource ? '' : 'C-1024')
      setWarning(noSource ? 'No relevant source was found in the indexed documents.' : '')
      setIsAnswering(false)
    }, 850)
  }

  function handleFiles(files) {
    Array.from(files)
      .filter((file) => /\.(pdf|docx|pptx|txt)$/i.test(file.name))
      .forEach((file) => {
        const jobId = `${file.name}-${Date.now()}`
        setUploadJobs((current) => [
          { id: jobId, name: file.name, progress: 10, step: uploadSteps[0] },
          ...current,
        ])
        uploadSteps.forEach((step, index) => {
          window.setTimeout(() => {
            setUploadJobs((current) =>
              current.map((item) =>
                item.id === jobId
                  ? { ...item, progress: Math.min(100, 18 + index * 21), step }
                  : item,
              ),
            )
          }, 420 * (index + 1))
        })
      })
  }

  function copyAnswer(message) {
    navigator.clipboard?.writeText(message.content)
    setCopiedId(message.id)
    window.setTimeout(() => setCopiedId(''), 1200)
  }

  function startRename(session) {
    setRenamingSession(session.id)
    setRenameValue(session.title)
  }

  function commitRename() {
    setSessions((current) =>
      current.map((session) =>
        session.id === renamingSession
          ? { ...session, title: renameValue || session.title }
          : session,
      ),
    )
    setRenamingSession('')
    setRenameValue('')
  }

  return (
    <div className="notebook-grid">
      <SourcePanel
        activeWorkspace={activeWorkspace}
        fileInputRef={fileInputRef}
        handleFiles={handleFiles}
        parallaxY={parallaxY}
        sessionList={sessionList}
        selectedSession={selectedSession}
        setActiveWorkspace={setActiveWorkspace}
        setSelectedSession={setSelectedSession}
        setSessionToDelete={setSessionToDelete}
        startRename={startRename}
        commitRename={commitRename}
        renamingSession={renamingSession}
        renameValue={renameValue}
        setRenameValue={setRenameValue}
        uploadJobs={uploadJobs}
        workspaceDocs={workspaceDocs}
      />

      <main className="notebook-panel flex min-h-[760px] flex-col">
        <div className="source-glow" />
        <div className="relative flex flex-wrap items-center justify-between gap-3 border-b border-border p-4">
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-950">
              AI Study Workspace
            </h1>
            <p className="text-sm font-semibold text-slate-500">
              Chat with indexed sources, preserve context, and verify every citation.
            </p>
          </div>
          <Button onClick={handleNewSession}>
            <MessageSquarePlus size={16} />
            New chat
          </Button>
        </div>

        {warning ? (
          <motion.div
            className="mx-4 mt-4 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-800"
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
          >
            <Info className="mt-0.5 shrink-0" size={16} />
            <span>{warning}</span>
            <button aria-label="Close warning" className="ml-auto" onClick={() => setWarning('')}>
              <X size={15} />
            </button>
          </motion.div>
        ) : null}

        <div className="card-rail relative min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          {messages.map((message) => (
            <ChatMessage
              activeCitation={activeCitation}
              copiedId={copiedId}
              feedback={feedback}
              key={message.id}
              message={message}
              onCitation={setActiveCitation}
              onCopy={copyAnswer}
              onFeedback={(value) =>
                setFeedback((current) => ({ ...current, [message.id]: value }))
              }
              onRegenerate={() => setWarning('Answer regeneration simulated.')}
            />
          ))}
          {isAnswering ? (
            <motion.div className="flex items-center gap-3 text-sm font-black text-slate-500" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <Loader2 className="animate-spin text-primary" size={18} />
              AI is reading sources and drafting an answer...
            </motion.div>
          ) : null}
        </div>

        <div className="border-t border-border bg-white/70 p-4">
          <div className="mb-3 flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
            {suggestions.map((suggestion) => (
              <motion.button
                className="shrink-0 rounded-full border border-border bg-white/90 px-3 py-2 text-xs font-black text-slate-600 shadow-sm hover:border-teal-200 hover:bg-teal-50 hover:text-primary"
                key={suggestion}
                onClick={() => setInput(suggestion)}
                whileHover={{ y: -2 }}
                whileTap={{ scale: 0.98 }}
              >
                {suggestion}
              </motion.button>
            ))}
          </div>
          <form
            className={cn(
              'flex items-end gap-3 rounded-xl border bg-white/90 p-2 shadow-inner transition focus-within:ring-4',
              canChat
                ? 'border-border focus-within:ring-teal-100'
                : 'border-amber-200 focus-within:ring-amber-100',
            )}
            onSubmit={handleSubmit}
          >
            <textarea
              className="max-h-36 min-h-12 flex-1 resize-none bg-transparent px-2 py-2 text-sm leading-6 outline-none placeholder:text-slate-400 disabled:cursor-not-allowed"
              disabled={!canChat}
              onChange={(event) => setInput(event.target.value)}
              placeholder={canChat ? 'Ask a question about the uploaded sources...' : 'Wait for an Indexed document to start chatting'}
              value={input}
            />
            <Button aria-label="Send question" disabled={!canChat} size="icon" type="submit">
              <Send size={17} />
            </Button>
          </form>
        </div>
      </main>

      <StudioPanel activeChunks={activeChunks} highlightedChunk={highlightedChunk} setActiveCitation={setActiveCitation} />

      {sessionToDelete ? (
        <ConfirmModal
          actionLabel="Delete session"
          onCancel={() => setSessionToDelete(null)}
          onConfirm={() => {
            setSessions((current) => current.filter((session) => session.id !== sessionToDelete.id))
            setSessionToDelete(null)
          }}
          title="Delete chat session?"
        >
          "{sessionToDelete.title}" will be removed from the local chat history.
        </ConfirmModal>
      ) : null}
    </div>
  )
}

function SourcePanel({
  activeWorkspace,
  fileInputRef,
  handleFiles,
  parallaxY,
  sessionList,
  selectedSession,
  setActiveWorkspace,
  setSelectedSession,
  setSessionToDelete,
  startRename,
  commitRename,
  renamingSession,
  renameValue,
  setRenameValue,
  uploadJobs,
  workspaceDocs,
}) {
  return (
    <aside className="notebook-panel flex min-h-[760px] flex-col">
      <div className="source-glow" />
      <div className="relative border-b border-border p-4">
        <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">Sources</p>
        <div className="mt-3 grid gap-2">
          {workspaces.map((workspace) => (
            <motion.button
              className={cn(
                'rounded-lg border px-3 py-3 text-left transition',
                activeWorkspace === workspace.id
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-white/80 text-slate-700 hover:border-teal-200 hover:bg-teal-50',
              )}
              key={workspace.id}
              onClick={() => setActiveWorkspace(workspace.id)}
              whileHover={{ x: 3 }}
              whileTap={{ scale: 0.98 }}
            >
              <span className="block text-sm font-black">{workspace.name}</span>
              <span className="block text-xs font-semibold opacity-60">{workspace.term}</span>
            </motion.button>
          ))}
        </div>
      </div>

      <div className="relative min-h-0 flex-1 overflow-y-auto p-4">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">Documents</p>
          <span className="text-xs font-black text-slate-400">{workspaceDocs.length} files</span>
        </div>
        <div className="space-y-2">
          {workspaceDocs.map((doc) => (
            <motion.article
              className="rounded-xl border border-border bg-white/82 p-3 shadow-sm"
              key={doc.id}
              style={{ y: parallaxY }}
              whileHover={{ y: -3, rotateX: 1 }}
            >
              <div className="flex gap-3">
                <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary">
                  <FileText size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-black text-slate-900">{doc.displayName}</p>
                  <p className="text-xs font-semibold text-slate-500">{doc.chapter} / {doc.chunks} chunks</p>
                </div>
              </div>
              <div className="mt-3 flex items-center justify-between">
                <StatusBadge status={doc.status} />
                <span className="text-xs font-black text-slate-400">{doc.type}</span>
              </div>
            </motion.article>
          ))}
        </div>

        <UploadBox fileInputRef={fileInputRef} handleFiles={handleFiles} uploadJobs={uploadJobs} />
        <SessionList
          commitRename={commitRename}
          renameValue={renameValue}
          renamingSession={renamingSession}
          selectedSession={selectedSession}
          sessionList={sessionList}
          setRenameValue={setRenameValue}
          setSelectedSession={setSelectedSession}
          setSessionToDelete={setSessionToDelete}
          startRename={startRename}
        />
      </div>
    </aside>
  )
}

function UploadBox({ fileInputRef, handleFiles, uploadJobs }) {
  return (
    <section className="mt-5 rounded-xl border border-dashed border-teal-300 bg-teal-50/60 p-4 text-center">
      <input
        accept=".pdf,.docx,.pptx,.txt"
        className="sr-only"
        multiple
        onChange={(event) => handleFiles(event.target.files)}
        ref={fileInputRef}
        type="file"
      />
      <Upload className="mx-auto text-primary" size={22} />
      <p className="mt-2 text-sm font-black text-slate-900">Upload document</p>
      <p className="mt-1 text-xs font-semibold leading-5 text-slate-500">PDF, DOCX, PPTX, TXT</p>
      <Button className="mt-3" onClick={() => fileInputRef.current?.click()} size="sm" type="button" variant="accent">
        Choose files
      </Button>
      <AnimatePresence>
        {uploadJobs.length > 0 ? (
          <div className="mt-4 space-y-2 text-left">
            {uploadJobs.map((job) => (
              <motion.div className="rounded-lg bg-white/78 p-3 shadow-sm" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} key={job.id}>
                <div className="flex items-center justify-between gap-2">
                  <p className="min-w-0 truncate text-xs font-black">{job.name}</p>
                  <span className="text-xs font-black text-primary">{job.step}</span>
                </div>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <motion.div
                    className="shimmer-line h-full rounded-full bg-gradient-to-r from-primary via-teal-400 to-emerald-300"
                    animate={{ width: `${job.progress}%` }}
                    transition={{ duration: 0.35 }}
                  />
                </div>
              </motion.div>
            ))}
          </div>
        ) : null}
      </AnimatePresence>
    </section>
  )
}

function SessionList({
  commitRename,
  renameValue,
  renamingSession,
  selectedSession,
  sessionList,
  setRenameValue,
  setSelectedSession,
  setSessionToDelete,
  startRename,
}) {
  return (
    <section className="mt-6">
      <div className="mb-3 flex items-center gap-2 text-xs font-black uppercase tracking-[0.14em] text-slate-500">
        <History size={14} />
        Chat history
      </div>
      <div className="space-y-2">
        {sessionList.map((session) => (
          <motion.div
            className={cn(
              'group rounded-lg border p-2 transition',
              selectedSession === session.id
                ? 'border-primary bg-teal-50'
                : 'border-border bg-white/80 hover:bg-teal-50',
            )}
            key={session.id}
            whileHover={{ y: -2 }}
          >
            {renamingSession === session.id ? (
              <div className="flex gap-2">
                <input
                  className="min-w-0 flex-1 rounded-lg border border-slate-200 px-2 py-1 text-sm font-semibold outline-none focus:border-teal-400"
                  onChange={(event) => setRenameValue(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') commitRename()
                  }}
                  value={renameValue}
                />
                <IconButton label="Save name" onClick={commitRename}>
                  <Check size={15} />
                </IconButton>
              </div>
            ) : (
              <div className="flex items-start gap-2">
                <button className="min-w-0 flex-1 text-left" onClick={() => setSelectedSession(session.id)}>
                  <p className="truncate text-sm font-black text-slate-800">{session.title}</p>
                  <p className="text-xs font-semibold text-slate-500">{session.updatedAt}</p>
                </button>
                <div className="flex opacity-100 sm:opacity-0 sm:transition sm:group-hover:opacity-100">
                  <IconButton label="Rename chat session" onClick={() => startRename(session)}>
                    <PencilLine size={14} />
                  </IconButton>
                  <IconButton label="Delete chat session" onClick={() => setSessionToDelete(session)}>
                    <Trash2 size={14} />
                  </IconButton>
                </div>
              </div>
            )}
          </motion.div>
        ))}
      </div>
    </section>
  )
}

function StudioPanel({ activeChunks, highlightedChunk, setActiveCitation }) {
  return (
    <aside className="notebook-panel notebook-panel-dark flex min-h-[760px] flex-col">
      <div className="border-b border-border p-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-black tracking-tight">Studio</h2>
            <p className="text-xs font-semibold text-slate-500">Citation previews and suggested follow-up questions.</p>
          </div>
          <Sparkles className="text-primary" size={18} />
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-4">
        {highlightedChunk ? (
          <motion.section className="rounded-xl border border-teal-100 bg-white p-4 shadow-sm" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-400">Active citation</p>
                <p className="mt-2 text-4xl font-black">{highlightedChunk.id}</p>
              </div>
              <span className="relevance-ring text-[10px] font-black text-primary" style={{ '--value': `${highlightedChunk.relevance}%` }}>
                {highlightedChunk.relevance}%
              </span>
            </div>
            <p className="mt-4 text-sm font-medium leading-7 text-slate-700">{highlightedChunk.content}</p>
            <div className="mt-4 flex flex-wrap gap-2 text-xs font-black">
              <span className="rounded-full bg-teal-50 px-3 py-1.5 text-teal-700">Page {highlightedChunk.page}</span>
              <span className="rounded-full bg-slate-100 px-3 py-1.5 text-slate-600">{highlightedChunk.tokenLength} tokens</span>
              <span className="rounded-full bg-slate-100 px-3 py-1.5 text-slate-600">{highlightedChunk.metadata}</span>
            </div>
          </motion.section>
        ) : (
          <div className="rounded-xl border border-dashed border-teal-200 p-5 text-center text-slate-500">
            <Layers3 className="mx-auto" size={26} />
            <p className="mt-3 text-sm font-black text-slate-800">No citation selected</p>
            <p className="mt-1 text-xs font-semibold leading-5">Citations appear after an answer includes indexed sources.</p>
          </div>
        )}

        <section className="mt-5">
          <p className="mb-3 text-xs font-black uppercase tracking-[0.14em] text-slate-400">Related sources</p>
          <div className="space-y-2">
            {(activeChunks.length ? activeChunks : chunks.slice(0, 3)).map((chunk) => (
              <motion.button
                className="w-full rounded-xl border border-border bg-white/80 p-3 text-left transition hover:border-teal-200 hover:bg-teal-50"
                key={chunk.id}
                onClick={() => setActiveCitation(chunk.id)}
                whileHover={{ x: 4 }}
                whileTap={{ scale: 0.98 }}
              >
                <div className="flex items-center justify-between">
                  <p className="text-sm font-black text-slate-800">{chunk.id}</p>
                  <span className="text-xs font-black text-primary">{chunk.relevance}%</span>
                </div>
                <p className="mt-2 line-clamp-2 text-xs font-medium leading-5 text-slate-500">
                  {chunk.content}
                </p>
              </motion.button>
            ))}
          </div>
        </section>

        <section className="mt-5 rounded-xl border border-border bg-white/80 p-4">
          <div className="flex items-center gap-2 text-sm font-black">
            <Search size={15} />
            Suggested questions
          </div>
          <div className="mt-3 space-y-2">
            {suggestions.map((suggestion) => (
              <div className="rounded-lg bg-teal-50 px-3 py-2 text-xs font-semibold text-slate-600" key={suggestion}>
                {suggestion}
              </div>
            ))}
          </div>
        </section>
      </div>
    </aside>
  )
}

function ChatMessage({
  activeCitation,
  copiedId,
  feedback,
  message,
  onCitation,
  onCopy,
  onFeedback,
  onRegenerate,
}) {
  const isUser = message.role === 'user'

  return (
    <motion.article
      className={cn('flex gap-3', isUser ? 'justify-end' : 'justify-start')}
      initial={{ opacity: 0, y: 18, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.28 }}
    >
      {!isUser ? (
        <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary text-white shadow-lg shadow-teal-900/10">
          <Bot size={18} />
        </div>
      ) : null}
      <motion.div
        className={cn(
          'max-w-[760px] rounded-lg border p-4 shadow-lg',
          isUser
            ? 'border-primary bg-primary text-white'
            : 'border-border bg-white/95 text-slate-800',
        )}
        whileHover={{ y: -2 }}
      >
        <p className="text-sm leading-7">{message.content}</p>
        {message.citations?.length ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {message.citations.map((citation) => {
              const chunk = chunks.find((item) => item.id === citation)
              const doc = documents.find((item) => item.id === chunk?.documentId)

              return (
                <motion.button
                  className={cn(
                    'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-black transition',
                    activeCitation === citation
                      ? 'border-teal-300 bg-teal-50 text-primary'
                      : 'border-slate-200 bg-white text-slate-600 hover:border-teal-200',
                  )}
                  key={citation}
                  onClick={() => onCitation(citation)}
                  whileHover={{ y: -2 }}
                  whileTap={{ scale: 0.97 }}
                >
                  <FileText size={13} />
                  {doc?.displayName} / p.{chunk?.page}
                </motion.button>
              )
            })}
          </div>
        ) : message.role === 'assistant' && message.confidence === 'low' ? (
          <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-bold text-amber-800">
            This answer does not have enough source support to verify.
          </p>
        ) : null}

        {!isUser ? (
          <div className="mt-4 flex flex-wrap items-center gap-1 border-t border-slate-100 pt-3">
            <IconButton label="Copy answer" onClick={() => onCopy(message)}>
              {copiedId === message.id ? <Check size={15} /> : <Clipboard size={15} />}
            </IconButton>
            <IconButton label="Regenerate" onClick={onRegenerate}>
              <RefreshCcw size={15} />
            </IconButton>
            <IconButton
              className={feedback[message.id] === 'up' ? 'bg-emerald-50 text-emerald-700' : ''}
              label="Helpful answer"
              onClick={() => onFeedback('up')}
            >
              <ThumbsUp size={15} />
            </IconButton>
            <IconButton
              className={feedback[message.id] === 'down' ? 'bg-red-50 text-red-700' : ''}
              label="Unhelpful answer"
              onClick={() => onFeedback('down')}
            >
              <ThumbsDown size={15} />
            </IconButton>
          </div>
        ) : null}
      </motion.div>
      {isUser ? (
        <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary shadow-lg shadow-teal-100">
          <UserRound size={18} />
        </div>
      ) : null}
    </motion.article>
  )
}

export default WorkspacePage

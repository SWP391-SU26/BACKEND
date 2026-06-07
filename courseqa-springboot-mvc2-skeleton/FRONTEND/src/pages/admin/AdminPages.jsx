import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  BarChart3,
  BookOpen,
  Brain,
  Check,
  CheckCircle2,
  ClipboardList,
  Database,
  Download,
  Eye,
  FilePlus2,
  FileText,
  FlaskConical,
  Gauge,
  Layers3,
  Lock,
  PencilLine,
  Play,
  Plus,
  RefreshCcw,
  Save,
  Search,
  Settings,
  ShieldCheck,
  Trash2,
  Unlock,
  Upload,
  Users,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  BentoCard,
  Button,
  ConfirmModal,
  Field,
  IconButton,
  Panel,
  SelectField,
  StatusBadge,
} from '../../components/ui.jsx'
import {
  adminLogs,
  adminSubjects,
  adminUsers,
  defaultModelSettings,
  experiments as seedExperiments,
  indexingJobs,
  modelComparison,
  ragasMetrics,
  testSet,
  tokenUsageByRange,
} from '../../data/adminMockData.js'
import { documents as seedDocuments } from '../../data/mockData.js'
import { AdminPageHeader } from '../../layouts/AdminLayout.jsx'
import { getUsers } from '../../services/authService.js'
import { cn } from '../../utils/cn.js'

const allOption = 'All'

function statusForBadge(status) {
  if (status === 'Completed' || status === 'Approved' || status === 'Active') return 'Indexed'
  if (status === 'Running' || status === 'Pending') return 'Processing'
  if (status === 'Locked') return 'Failed'
  return status
}

function toAdminUser(user) {
  const roles = user.roles ?? []
  const isAdmin = roles.some((role) => role?.toUpperCase() === 'ADMIN')

  return {
    id: user.userId,
    name: user.fullName,
    email: user.email,
    role: isAdmin ? 'Admin' : 'User',
    status: user.isActive === false ? 'Locked' : 'Active',
    documents: 0,
    chats: 0,
    lastActive: user.lastLoginAt ?? 'Never',
  }
}

export function AdminDashboardPage() {
  const [tokenRange, setTokenRange] = useState('7 days')
  const indexed = seedDocuments.filter((doc) => doc.status === 'Indexed').length
  const processing = seedDocuments.filter((doc) => doc.status === 'Processing').length
  const failed = seedDocuments.filter((doc) => doc.status === 'Failed').length
  const tokenUsage = tokenUsageByRange[tokenRange]
  const bestQuality = modelComparison.reduce((best, model) => (model.quality > best.quality ? model : best), modelComparison[0])
  const lowestToken = modelComparison.reduce((best, model) => (model.totalTokens < best.totalTokens ? model : best), modelComparison[0])
  const lowestLatency = modelComparison.reduce((best, model) => (model.latency < best.latency ? model : best), modelComparison[0])

  return (
    <div className="space-y-4">
      <AdminPageHeader
        description="Overview of users, token traffic, model operations, indexing, and RAGAS benchmarks."
        icon={Gauge}
        title="Admin Dashboard"
      />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={Users} label="Total users" value={tokenUsage.totalUsers} tone="amber" />
        <MetricCard icon={ShieldCheck} label="Active users" value={tokenUsage.activeUsers} tone="mint" />
        <MetricCard icon={Brain} label="Total tokens" value={formatCompact(tokenUsage.totalTokens)} tone="sky" />
        <MetricCard icon={FlaskConical} label="Avg quality" value={`${Math.round(tokenUsage.quality * 100)}%`} tone="rose" />
      </div>

      <Panel className="overflow-hidden p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <SectionTitle icon={BarChart3} title="Token traffic" subtitle="Input, output, and total tokens for the selected time range" />
          <RangeSelector onChange={setTokenRange} value={tokenRange} />
        </div>
        <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
          <TokenTrafficChart data={tokenUsage.series} />
          <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
            <TokenMiniCard label="Input tokens" value={formatCompact(tokenUsage.inputTokens)} tone="sky" />
            <TokenMiniCard label="Output tokens" value={formatCompact(tokenUsage.outputTokens)} tone="amber" />
            <TokenMiniCard label="Estimated cost" value={`$${formatMoney(tokenUsage.estimatedCost)}`} tone="mint" />
          </div>
        </div>
      </Panel>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(330px,.85fr)]">
        <Panel className="p-5">
          <SectionTitle icon={Brain} title="RAG vs Fine-tuning" subtitle="Compare tokens, quality, latency, and model operating cost" />
          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            {modelComparison.map((model) => (
              <ModelComparisonCard
                isBestQuality={model.id === bestQuality.id}
                isLowestToken={model.id === lowestToken.id}
                key={model.id}
                model={model}
              />
            ))}
          </div>
          <div className="mt-5">
            <ModelComparisonBars models={modelComparison} />
          </div>
        </Panel>

        <Panel className="p-5">
          <SectionTitle icon={CheckCircle2} title="Operations summary" subtitle="Operational recommendations from the current mock data" />
          <div className="mt-5 grid gap-3">
            <InsightCard label="Best quality" note={`${Math.round(bestQuality.quality * 100)}% quality score`} value={bestQuality.name} />
            <InsightCard label="Lowest token usage" note={`${formatCompact(lowestToken.totalTokens)} tokens`} value={lowestToken.name} />
            <InsightCard label="Lowest latency" note={`${lowestLatency.latency}s average response`} value={lowestLatency.name} />
            <InsightCard
              label="Recommendation"
              note="Prioritize RAG for source-grounded learning materials; fine-tuning fits low-latency flows after guardrails are in place."
              value={bestQuality.id === 'rag' && lowestToken.id === 'rag' ? 'Use RAG as default' : 'Route by workload'}
            />
          </div>
        </Panel>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(320px,.7fr)]">
        <Panel className="p-5">
          <SectionTitle icon={Database} title="Index status" subtitle="System-wide pipeline status" />
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <ProgressTile label="Indexed" value={indexed} percent={72} />
            <ProgressTile label="Processing" value={processing} percent={18} />
            <ProgressTile label="Failed" value={failed} percent={10} danger />
          </div>
          <div className="mt-6">
            <div className="mb-3 flex items-center justify-between gap-3">
              <p className="text-sm font-black">Quality detail</p>
              <p className="text-xs font-bold text-slate-500">RAGAS metrics</p>
            </div>
            <NativeBarChart data={ragasMetrics} />
          </div>
        </Panel>

        <Panel className="p-5">
          <SectionTitle icon={AlertTriangle} title="Alerts" subtitle="Recent issues requiring attention" />
          <div className="mt-4 space-y-3">
            {adminLogs.filter((log) => log.level !== 'Info').map((log) => (
              <motion.article className="rounded-lg border border-white/80 bg-white/72 p-3" key={log.id} whileHover={{ x: 3 }}>
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-black">{log.type}</p>
                  <span className={cn('rounded-full px-2 py-1 text-xs font-black', log.level === 'Error' ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700')}>
                    {log.level}
                  </span>
                </div>
                <p className="mt-2 text-xs font-semibold leading-5 text-slate-600">{log.message}</p>
              </motion.article>
            ))}
          </div>
        </Panel>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        {seedExperiments.map((experiment) => (
          <BentoCard className="p-4" key={experiment.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-black">{experiment.name}</p>
                <p className="mt-1 text-xs font-semibold text-slate-500">{experiment.method} / {experiment.embedding}</p>
              </div>
              <StatusBadge status={statusForBadge(experiment.status)} />
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
              <motion.div className="shimmer-line h-full rounded-full bg-[#14b8a6]" animate={{ width: `${experiment.ragas * 100}%` }} />
            </div>
            <p className="mt-3 text-3xl font-black">{experiment.ragas.toFixed(2)}</p>
            <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">RAGAS average</p>
          </BentoCard>
        ))}
      </div>
    </div>
  )
}

export function AdminUsersPage() {
  const [users, setUsers] = useState(adminUsers)
  const [query, setQuery] = useState('')
  const [role, setRole] = useState(allOption)
  const [status, setStatus] = useState(allOption)
  const [selectedUser, setSelectedUser] = useState(null)
  const [deleteUser, setDeleteUser] = useState(null)
  const [apiError, setApiError] = useState('')

  useEffect(() => {
    Promise.resolve().then(async () => {
      try {
        const backendUsers = await getUsers()
        setUsers(backendUsers.map(toAdminUser))
        setApiError('')
      } catch (error) {
        setApiError(error.message)
      }
    })
  }, [])

  const filtered = users.filter((user) => {
    const q = query.toLowerCase().trim()
    return (
      (!q || user.name.toLowerCase().includes(q) || user.email.toLowerCase().includes(q)) &&
      (role === allOption || user.role === role) &&
      (status === allOption || user.status === status)
    )
  })

  function changeRole(userId, nextRole) {
    setUsers((current) => current.map((user) => user.id === userId ? { ...user, role: nextRole } : user))
  }

  function toggleLock(userId) {
    setUsers((current) =>
      current.map((user) =>
        user.id === userId ? { ...user, status: user.status === 'Locked' ? 'Active' : 'Locked' } : user,
      ),
    )
  }

  return (
    <CrudPage
      actions={null}
      description="Search, filter, change roles, lock or unlock accounts, and inspect user activity."
      icon={Users}
      title="User Management"
    >
      <Toolbar>
        <Field icon={Search} label="Search user" onChange={(event) => setQuery(event.target.value)} placeholder="Name or email..." value={query} />
        <SelectField label="Role" onChange={(event) => setRole(event.target.value)} value={role}>
          {[allOption, 'User', 'Admin'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
        <SelectField label="Status" onChange={(event) => setStatus(event.target.value)} value={status}>
          {[allOption, 'Active', 'Locked'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
      </Toolbar>
      {apiError ? (
        <Panel className="mb-4 border-amber-200 bg-amber-50 p-3 text-sm font-bold text-amber-800">
          {apiError}
        </Panel>
      ) : null}

      <DataTable
        columns={['User', 'Role', 'Status', 'Documents', 'Chats', 'Last active', 'Actions']}
        rows={filtered.map((user) => [
          <Identity title={user.name} subtitle={user.email} key="user" />,
          <select className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs font-black" key="role" onChange={(event) => changeRole(user.id, event.target.value)} value={user.role}>
            {['User', 'Admin'].map((item) => <option key={item}>{item}</option>)}
          </select>,
          <StatusBadge key="status" status={statusForBadge(user.status)} />,
          user.documents,
          user.chats,
          user.lastActive,
          <RowActions key="actions">
            <IconButton label="View details" onClick={() => setSelectedUser(user)}><Eye size={15} /></IconButton>
            <IconButton label={user.status === 'Locked' ? 'Unlock' : 'Lock'} onClick={() => toggleLock(user.id)}>{user.status === 'Locked' ? <Unlock size={15} /> : <Lock size={15} />}</IconButton>
            <IconButton label="Delete user" onClick={() => setDeleteUser(user)}><Trash2 size={15} /></IconButton>
          </RowActions>,
        ])}
      />

      {selectedUser ? (
        <DrawerModal onClose={() => setSelectedUser(null)} title={selectedUser.name}>
          <DetailGrid items={[
            ['Email', selectedUser.email],
            ['Role', selectedUser.role],
            ['Status', selectedUser.status],
            ['Documents', selectedUser.documents],
            ['Chats', selectedUser.chats],
            ['Last active', selectedUser.lastActive],
          ]} />
        </DrawerModal>
      ) : null}
      {deleteUser ? (
        <ConfirmModal actionLabel="Delete user" onCancel={() => setDeleteUser(null)} onConfirm={() => { setUsers((current) => current.filter((user) => user.id !== deleteUser.id)); setDeleteUser(null) }} title="Delete user?">
          The account "{deleteUser.name}" will be removed from the mock data.
        </ConfirmModal>
      ) : null}
    </CrudPage>
  )
}

export function AdminDocumentsPage() {
  const [docs, setDocs] = useState(seedDocuments.map((doc, index) => ({
    ...doc,
    owner: adminUsers[index % adminUsers.length].name,
    moderation: index % 2 === 0 ? 'Approved' : 'Pending',
  })))
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState(allOption)
  const [chunkDoc, setChunkDoc] = useState(null)
  const [deleteDoc, setDeleteDoc] = useState(null)

  const filtered = docs.filter((doc) => {
    const q = query.toLowerCase().trim()
    return (
      (!q || doc.displayName.toLowerCase().includes(q) || doc.owner.toLowerCase().includes(q)) &&
      (status === allOption || doc.status === status)
    )
  })

  function reindexDoc(id) {
    setDocs((current) => current.map((doc) => doc.id === id ? { ...doc, status: 'Processing' } : doc))
    window.setTimeout(() => {
      setDocs((current) => current.map((doc) => doc.id === id ? { ...doc, status: 'Indexed', chunks: doc.chunks || 30 } : doc))
    }, 900)
  }

  function approveDoc(id) {
    setDocs((current) => current.map((doc) => doc.id === id ? { ...doc, moderation: 'Approved' } : doc))
  }

  return (
    <CrudPage description="Manage system-wide documents, review sources, re-index files, and inspect chunks." icon={FileText} title="Document Management">
      <Toolbar>
        <Field icon={Search} label="Search document" onChange={(event) => setQuery(event.target.value)} placeholder="Filename or user..." value={query} />
        <SelectField label="Status" onChange={(event) => setStatus(event.target.value)} value={status}>
          {[allOption, 'Uploaded', 'Processing', 'Indexed', 'Failed'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
      </Toolbar>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {filtered.map((doc) => (
          <BentoCard className="p-4" key={doc.id}>
            <div className="flex items-start gap-3">
              <div className="grid size-10 place-items-center rounded-xl bg-teal-50 text-primary"><FileText size={17} /></div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-black">{doc.displayName}</p>
                <p className="text-xs font-semibold text-slate-500">{doc.owner} / {doc.subject}</p>
              </div>
              <StatusBadge status={doc.status} />
            </div>
            <p className="mt-3 line-clamp-2 text-sm font-medium leading-6 text-slate-600">{doc.preview}</p>
            <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs font-black">
              <span className="rounded-lg bg-teal-50 px-2 py-2 text-teal-700">{doc.type}</span>
              <span className="rounded-lg bg-emerald-50 px-2 py-2 text-emerald-700">{doc.chunks} chunks</span>
              <span className="rounded-lg bg-amber-50 px-2 py-2 text-amber-700">{doc.moderation}</span>
            </div>
            <RowActions className="mt-4">
              <Button onClick={() => approveDoc(doc.id)} size="sm" variant="secondary"><Check size={14} />Approve</Button>
              <IconButton label="View chunks" onClick={() => setChunkDoc(doc)}><Eye size={15} /></IconButton>
              <IconButton label="Re-index" onClick={() => reindexDoc(doc.id)}><RefreshCcw size={15} /></IconButton>
              <IconButton label="Delete" onClick={() => setDeleteDoc(doc)}><Trash2 size={15} /></IconButton>
            </RowActions>
          </BentoCard>
        ))}
      </div>
      {chunkDoc ? (
        <DrawerModal onClose={() => setChunkDoc(null)} title={`Chunks / ${chunkDoc.displayName}`}>
          <p className="text-sm font-semibold leading-6 text-slate-600">Preview chunk quality, source pages, token length, and metadata.</p>
          <div className="mt-4 space-y-3">
            {[1, 2, 3].map((item) => (
              <div className="rounded-lg border border-white/80 bg-white/72 p-3" key={item}>
                <p className="text-xs font-black text-slate-500">Chunk {item} / Page {item * 4} / {180 + item * 22} tokens</p>
                <p className="mt-2 text-sm leading-6 text-slate-700">{chunkDoc.preview}</p>
              </div>
            ))}
          </div>
        </DrawerModal>
      ) : null}
      {deleteDoc ? (
        <ConfirmModal actionLabel="Delete document" onCancel={() => setDeleteDoc(null)} onConfirm={() => { setDocs((current) => current.filter((doc) => doc.id !== deleteDoc.id)); setDeleteDoc(null) }} title="Delete document?">
          "{deleteDoc.displayName}" will be removed from the mock data.
        </ConfirmModal>
      ) : null}
    </CrudPage>
  )
}

export function AdminSubjectsPage() {
  const [subjects, setSubjects] = useState(adminSubjects)
  const [selected, setSelected] = useState(subjects[0])

  function toggleSubject(id) {
    setSubjects((current) => current.map((subject) => subject.id === id ? { ...subject, active: !subject.active } : subject))
  }

  function addChapter() {
    setSubjects((current) => current.map((subject) => subject.id === selected.id ? { ...subject, chapters: [...subject.chapters, `Chapter ${subject.chapters.length + 1}`] } : subject))
  }

  return (
    <CrudPage description="Create and edit subjects, manage chapters, toggle availability, and assign documents." icon={BookOpen} title="Subjects / Chapters">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="grid gap-4 md:grid-cols-2">
          {subjects.map((subject) => (
            <BentoCard className={cn('p-4', selected.id === subject.id ? 'ring-2 ring-teal-400' : '')} key={subject.id}>
              <button className="w-full text-left" onClick={() => setSelected(subject)}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-black">{subject.name}</p>
                    <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">{subject.code}</p>
                  </div>
                  <StatusBadge status={subject.active ? 'Indexed' : 'Uploaded'} />
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-600">{subject.description}</p>
                <p className="mt-3 text-xs font-black text-slate-500">{subject.chapters.length} chapters / {subject.documents} documents</p>
              </button>
              <Button className="mt-4" onClick={() => toggleSubject(subject.id)} size="sm" variant="secondary">
                {subject.active ? 'Disable subject' : 'Enable subject'}
              </Button>
            </BentoCard>
          ))}
        </div>
        <Panel className="p-5">
          <SectionTitle icon={ListIcon} title={selected.name} subtitle="Mock editor for subjects and chapters" />
          <div className="mt-4 space-y-2">
            {selected.chapters.map((chapter) => (
              <div className="flex items-center justify-between rounded-lg bg-white/72 px-3 py-2 text-sm font-black" key={chapter}>
                {chapter}
                <PencilLine size={14} className="text-slate-400" />
              </div>
            ))}
          </div>
          <Button className="mt-4" onClick={addChapter} variant="accent"><Plus size={16} />Add chapter</Button>
        </Panel>
      </div>
    </CrudPage>
  )
}

function ListIcon(props) {
  return <BookOpen {...props} />
}

export function AdminIndexingPage() {
  const [jobs, setJobs] = useState(indexingJobs)
  const [selectedError, setSelectedError] = useState(null)

  function retryJob(id) {
    setJobs((current) => current.map((job) => job.id === id ? { ...job, status: 'Running', step: 'Embedding', progress: 62, error: '' } : job))
    window.setTimeout(() => {
      setJobs((current) => current.map((job) => job.id === id ? { ...job, status: 'Completed', step: 'Indexed', progress: 100 } : job))
    }, 900)
  }

  function bulkReindex() {
    setJobs((current) => current.map((job) => ({ ...job, status: 'Running', progress: Math.max(job.progress, 40) })))
  }

  return (
    <CrudPage actions={<Button onClick={bulkReindex}><RefreshCcw size={16} />Bulk re-index</Button>} description="Monitor the processing queue, retry failures, and inspect each pipeline step." icon={Database} title="Indexing Pipeline">
      <div className="grid gap-4 xl:grid-cols-2">
        {jobs.map((job) => (
          <BentoCard className="p-4" key={job.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-black">{job.file}</p>
                <p className="mt-1 text-xs font-semibold text-slate-500">{job.owner} / {job.step}</p>
              </div>
              <StatusBadge status={statusForBadge(job.status)} />
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
              <motion.div className="shimmer-line h-full rounded-full bg-gradient-to-r from-[#14b8a6] to-teal-300" animate={{ width: `${job.progress}%` }} />
            </div>
            <Pipeline steps={['Extracting', 'Chunking', 'Embedding', 'Storing']} active={job.step} />
            <RowActions className="mt-4">
              <Button onClick={() => retryJob(job.id)} size="sm" variant="secondary"><RefreshCcw size={14} />Retry</Button>
              {job.error ? <Button onClick={() => setSelectedError(job)} size="sm" variant="danger"><AlertTriangle size={14} />Error</Button> : null}
            </RowActions>
          </BentoCard>
        ))}
      </div>
      {selectedError ? (
        <DrawerModal onClose={() => setSelectedError(null)} title={`Error / ${selectedError.file}`}>
          <p className="rounded-lg bg-red-50 p-3 text-sm font-semibold leading-6 text-red-700">{selectedError.error}</p>
        </DrawerModal>
      ) : null}
    </CrudPage>
  )
}

export function AdminModelSettingsPage() {
  const [settings, setSettings] = useState(defaultModelSettings)
  const [saved, setSaved] = useState(false)

  function saveSettings() {
    setSaved(true)
    window.setTimeout(() => setSaved(false), 1400)
  }

  return (
    <CrudPage actions={<Button onClick={saveSettings}><Save size={16} />Save settings</Button>} description="Configure answer models, embeddings, chunking, and retrieval for upcoming indexing and chat requests." icon={Settings} title="Model / Retrieval Settings">
      {saved ? <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm font-black text-emerald-700">Mock settings saved.</div> : null}
      <Panel className="p-5">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <SettingSelect label="Answer LLM" value={settings.llm} onChange={(value) => setSettings({ ...settings, llm: value })} options={['gpt-4.1-mini', 'gpt-4.1', 'gemini-1.5-pro', 'llama-3.1']} />
          <SettingSelect label="Embedding model" value={settings.embedding} onChange={(value) => setSettings({ ...settings, embedding: value })} options={['text-embedding-3-small', 'bge-m3', 'PhoBERT-base', 'multilingual-e5-base']} />
          <NumberSetting label="Chunk size" value={settings.chunkSize} onChange={(value) => setSettings({ ...settings, chunkSize: value })} />
          <NumberSetting label="Overlap" value={settings.overlap} onChange={(value) => setSettings({ ...settings, overlap: value })} />
          <NumberSetting label="Top-k" value={settings.topK} onChange={(value) => setSettings({ ...settings, topK: value })} />
          <NumberSetting label="Temperature" step="0.1" value={settings.temperature} onChange={(value) => setSettings({ ...settings, temperature: value })} />
        </div>
        <label className="mt-5 flex items-center justify-between rounded-lg bg-white/72 p-4">
          <span>
            <span className="block text-sm font-black">Strict source grounding</span>
            <span className="block text-xs font-semibold text-slate-500">The assistant answers only from indexed sources.</span>
          </span>
          <input checked={settings.strictSources} onChange={(event) => setSettings({ ...settings, strictSources: event.target.checked })} type="checkbox" />
        </label>
      </Panel>
    </CrudPage>
  )
}

export function AdminTestSetPage() {
  const [questions, setQuestions] = useState(testSet)
  const [difficulty, setDifficulty] = useState(allOption)
  const [type, setType] = useState(allOption)
  const [showAdd, setShowAdd] = useState(false)
  const [running, setRunning] = useState(false)

  const filtered = questions.filter((item) =>
    (difficulty === allOption || item.difficulty === difficulty) &&
    (type === allOption || item.type === type)
  )

  function addQuestion() {
    setQuestions((current) => [
      {
        id: `q-${current.length + 1}`,
        question: 'New question about the source material?',
        groundTruth: 'Ground truth entered by the admin.',
        chapter: 'Chapter 1',
        difficulty: 'Medium',
        type: 'Explanation',
      },
      ...current,
    ])
    setShowAdd(false)
  }

  function runEvaluation() {
    setRunning(true)
    window.setTimeout(() => setRunning(false), 1200)
  }

  return (
    <CrudPage actions={<><Button onClick={() => setShowAdd(true)}><Plus size={16} />Add question</Button><Button onClick={runEvaluation} variant="accent"><Play size={16} />{running ? 'Running...' : 'Run evaluation'}</Button><Button variant="secondary"><Upload size={16} />Import</Button><Button variant="secondary"><Download size={16} />Export</Button></>} description="A 50-question ground-truth set for chatbot benchmarking." icon={ClipboardList} title="Test Set / Ground Truth">
      <Toolbar>
        <SelectField label="Difficulty" onChange={(event) => setDifficulty(event.target.value)} value={difficulty}>
          {[allOption, 'Easy', 'Medium', 'Hard'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
        <SelectField label="Type" onChange={(event) => setType(event.target.value)} value={type}>
          {[allOption, 'Definition', 'Comparison', 'Explanation', 'Application'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
      </Toolbar>
      <DataTable
        columns={['ID', 'Question', 'Chapter', 'Difficulty', 'Type', 'Ground truth']}
        rows={filtered.slice(0, 18).map((item) => [item.id, item.question, item.chapter, item.difficulty, item.type, <span className="line-clamp-2" key="gt">{item.groundTruth}</span>])}
      />
      {showAdd ? (
        <DrawerModal onClose={() => setShowAdd(false)} title="Add question">
          <p className="text-sm font-semibold text-slate-600">Mock form for a new question and ground truth.</p>
          <Button className="mt-4" onClick={addQuestion}>Create sample question</Button>
        </DrawerModal>
      ) : null}
    </CrudPage>
  )
}

export function AdminExperimentsPage() {
  const [experiments, setExperiments] = useState(seedExperiments)
  const [showCreate, setShowCreate] = useState(false)
  const [compare, setCompare] = useState([])

  function runExperiment() {
    setExperiments((current) => [
      {
        id: `exp-${current.length + 1}`,
        name: 'New RAG benchmark',
        method: 'RAG',
        embedding: 'bge-m3',
        chunking: 'Sliding window',
        status: 'Running',
        ragas: 0.78,
        latency: 2.2,
        accuracy: 0.76,
        cost: 2.7,
      },
      ...current,
    ])
    setShowCreate(false)
  }

  function toggleCompare(id) {
    setCompare((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id].slice(-3))
  }

  return (
    <CrudPage actions={<Button onClick={() => setShowCreate(true)}><Plus size={16} />Create experiment</Button>} description="Create benchmarks, run experiments, inspect status, and compare results." icon={FlaskConical} title="Experiment RBL">
      <div className="grid gap-4 xl:grid-cols-3">
        {experiments.map((experiment) => (
          <BentoCard className={cn('p-4', compare.includes(experiment.id) ? 'ring-2 ring-teal-400' : '')} key={experiment.id}>
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-black">{experiment.name}</p>
                <p className="mt-1 text-xs font-semibold text-slate-500">{experiment.method} / {experiment.chunking}</p>
              </div>
              <StatusBadge status={statusForBadge(experiment.status)} />
            </div>
            <div className="mt-4 grid grid-cols-2 gap-2 text-xs font-black">
              <span className="rounded-lg bg-white/72 p-2">RAGAS {experiment.ragas}</span>
              <span className="rounded-lg bg-white/72 p-2">Latency {experiment.latency}s</span>
              <span className="rounded-lg bg-white/72 p-2">Accuracy {experiment.accuracy}</span>
              <span className="rounded-lg bg-white/72 p-2">Cost ${experiment.cost}</span>
            </div>
            <Button className="mt-4 w-full" onClick={() => toggleCompare(experiment.id)} variant="secondary">Compare</Button>
          </BentoCard>
        ))}
      </div>
      {compare.length ? (
        <Panel className="mt-4 p-5">
          <SectionTitle icon={BarChart3} title="Comparison" subtitle={`${compare.length} experiments selected`} />
          <NativeBarChart data={experiments.filter((item) => compare.includes(item.id)).map((item) => ({ label: item.name.slice(0, 16), rag: Math.round(item.ragas * 100), fineTune: Math.round(item.accuracy * 100) }))} />
        </Panel>
      ) : null}
      {showCreate ? (
        <DrawerModal onClose={() => setShowCreate(false)} title="Create experiment">
          <DetailGrid items={[
            ['Method', 'RAG'],
            ['Embedding', 'bge-m3'],
            ['Chunking', 'Sliding window'],
            ['Test set', '50 questions'],
          ]} />
          <Button className="mt-4" onClick={runExperiment}><Play size={16} />Run benchmark</Button>
        </DrawerModal>
      ) : null}
    </CrudPage>
  )
}

export function AdminResearchDashboardPage() {
  return (
    <CrudPage actions={<Button variant="secondary"><Download size={16} />Export report</Button>} description="Compare RAG and fine-tuning, embedding models, and chunking strategies with RAGAS." icon={BarChart3} title="Research Dashboard">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Panel className="p-5">
          <SectionTitle icon={BarChart3} title="RAGAS benchmark" subtitle="Faithfulness, relevancy, precision, recall" />
          <NativeBarChart data={ragasMetrics} />
        </Panel>
        <Panel className="p-5">
          <SectionTitle icon={ShieldCheck} title="Recommendation" subtitle="Recommendation from the current mock results" />
          <p className="mt-4 text-3xl font-black">bge-m3 + semantic chunk</p>
          <p className="mt-3 text-sm font-semibold leading-6 text-slate-600">This setup provides the best balance of faithfulness, context precision, and latency across the 50-question set.</p>
        </Panel>
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-3">
        {['Embedding model', 'Chunking strategy', 'RAG vs fine-tuning'].map((item, index) => (
          <BentoCard className="p-4" key={item}>
            <p className="text-sm font-black">{item}</p>
            <div className="mt-4 h-28">
              <MiniLineChart offset={index * 12} />
            </div>
          </BentoCard>
        ))}
      </div>
    </CrudPage>
  )
}

export function AdminLogsPage() {
  const [query, setQuery] = useState('')
  const [type, setType] = useState(allOption)
  const [selected, setSelected] = useState(null)

  const filtered = adminLogs.filter((log) => {
    const q = query.toLowerCase().trim()
    return (!q || log.message.toLowerCase().includes(q) || log.user.toLowerCase().includes(q)) && (type === allOption || log.type === type)
  })

  return (
    <CrudPage description="Inspect upload, indexing, chat, benchmark, and API error logs." icon={AlertTriangle} title="System Logs">
      <Toolbar>
        <Field icon={Search} label="Search logs" onChange={(event) => setQuery(event.target.value)} placeholder="Message or user..." value={query} />
        <SelectField label="Type" onChange={(event) => setType(event.target.value)} value={type}>
          {[allOption, 'Index', 'Embedding', 'Chat', 'Benchmark'].map((item) => <option key={item}>{item}</option>)}
        </SelectField>
      </Toolbar>
      <DataTable
        columns={['Time', 'Type', 'Level', 'User', 'Message', 'Actions']}
        rows={filtered.map((log) => [
          log.time,
          log.type,
          <span className={cn('rounded-full px-2 py-1 text-xs font-black', log.level === 'Error' ? 'bg-red-50 text-red-700' : log.level === 'Warn' ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700')} key="level">{log.level}</span>,
          log.user,
          log.message,
          <IconButton key="action" label="View details" onClick={() => setSelected(log)}><Eye size={15} /></IconButton>,
        ])}
      />
      {selected ? (
        <DrawerModal onClose={() => setSelected(null)} title={selected.message}>
          <DetailGrid items={[
            ['Type', selected.type],
            ['Level', selected.level],
            ['User', selected.user],
            ['Time', selected.time],
          ]} />
          <pre className="mt-4 overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-white">{selected.detail}</pre>
        </DrawerModal>
      ) : null}
    </CrudPage>
  )
}

function CrudPage({ actions, children, description, icon, title }) {
  return (
    <div>
      <AdminPageHeader actions={actions} description={description} icon={icon} title={title} />
      {children}
    </div>
  )
}

function Toolbar({ children }) {
  return (
    <Panel className="mb-4 p-3">
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">{children}</div>
    </Panel>
  )
}

function MetricCard({ icon: Icon, label, tone, value }) {
  const tones = {
    amber: 'bg-teal-50 text-primary',
    sky: 'bg-slate-100 text-slate-700',
    mint: 'bg-emerald-50 text-emerald-700',
    rose: 'bg-red-50 text-red-700',
  }
  return (
    <BentoCard className="p-4">
      <div className="flex items-center justify-between">
        <div className={cn('grid size-11 place-items-center rounded-lg', tones[tone])}>
          <Icon size={19} />
        </div>
        <p className="text-4xl font-black">{value}</p>
      </div>
      <p className="mt-4 text-xs font-black uppercase tracking-[0.14em] text-slate-500">{label}</p>
    </BentoCard>
  )
}

function formatCompact(value) {
  return new Intl.NumberFormat('en', { maximumFractionDigits: 1, notation: 'compact' }).format(value)
}

function formatMoney(value) {
  return Number(value).toLocaleString('en-US', { maximumFractionDigits: 1 })
}

function RangeSelector({ onChange, value }) {
  return (
    <div className="flex max-w-full overflow-x-auto rounded-lg bg-white/70 p-1 shadow-inner shadow-slate-200/70">
      {Object.keys(tokenUsageByRange).map((range) => (
        <motion.button
          className={cn(
            'min-h-9 min-w-max rounded-lg px-3 text-xs font-black transition outline-none focus-visible:ring-2 focus-visible:ring-teal-400',
            value === range ? 'bg-[#0f766e] text-white shadow-sm' : 'text-slate-500 hover:bg-white hover:text-slate-950',
          )}
          key={range}
          onClick={() => onChange(range)}
          type="button"
          whileTap={{ scale: 0.96 }}
        >
          {range}
        </motion.button>
      ))}
    </div>
  )
}

function TokenMiniCard({ label, tone, value }) {
  const tones = {
    amber: 'from-teal-50 to-white text-primary',
    sky: 'from-slate-100 to-white text-slate-700',
    mint: 'from-emerald-50 to-white text-emerald-700',
  }
  return (
    <motion.div
      className={cn('rounded-lg border border-white/80 bg-gradient-to-br p-4 shadow-sm', tones[tone])}
      whileHover={{ y: -3 }}
      whileTap={{ scale: 0.98 }}
    >
      <p className="text-xs font-black uppercase tracking-[0.12em] text-slate-500">{label}</p>
      <p className="mt-2 text-2xl font-black text-slate-950">{value}</p>
    </motion.div>
  )
}

function TokenTrafficChart({ data }) {
  const width = 760
  const height = 260
  const padX = 36
  const padY = 30
  const maxValue = Math.max(...data.map((point) => point.input + point.output), 1)
  const pointFor = (value, index) => {
    const x = padX + (index * (width - padX * 2)) / Math.max(data.length - 1, 1)
    const y = height - padY - (value / maxValue) * (height - padY * 2)
    return { x, y }
  }
  const totalPoints = data.map((point, index) => pointFor(point.input + point.output, index))
  const inputPoints = data.map((point, index) => pointFor(point.input, index))
  const outputPoints = data.map((point, index) => pointFor(point.output, index))
  const toPolyline = (points) => points.map((point) => `${point.x},${point.y}`).join(' ')
  const areaPath = [
    `M ${totalPoints[0]?.x ?? padX} ${height - padY}`,
    ...totalPoints.map((point) => `L ${point.x} ${point.y}`),
    `L ${totalPoints.at(-1)?.x ?? width - padX} ${height - padY}`,
    'Z',
  ].join(' ')

  return (
    <motion.div className="rounded-lg bg-white/72 p-4 shadow-inner shadow-slate-200/60" whileHover={{ y: -2 }}>
      <svg aria-label="Token traffic chart" className="h-[260px] w-full overflow-visible" role="img" viewBox={`0 0 ${width} ${height}`}>
        <defs>
          <linearGradient id="tokenAreaGradient" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#14b8a6" stopOpacity="0.42" />
            <stop offset="100%" stopColor="#64748b" stopOpacity="0.04" />
          </linearGradient>
        </defs>
        {[0.25, 0.5, 0.75].map((line) => (
          <line
            key={line}
            stroke="#e2e8f0"
            strokeDasharray="5 7"
            strokeWidth="1"
            x1={padX}
            x2={width - padX}
            y1={padY + line * (height - padY * 2)}
            y2={padY + line * (height - padY * 2)}
          />
        ))}
        <motion.path d={areaPath} fill="url(#tokenAreaGradient)" initial={{ opacity: 0 }} animate={{ opacity: 1 }} />
        <motion.polyline
          animate={{ pathLength: 1 }}
          fill="none"
          initial={{ pathLength: 0 }}
          points={toPolyline(totalPoints)}
          stroke="#0f766e"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="5"
        />
        <motion.polyline
          animate={{ pathLength: 1 }}
          fill="none"
          initial={{ pathLength: 0 }}
          points={toPolyline(inputPoints)}
          stroke="#64748b"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="3"
        />
        <motion.polyline
          animate={{ pathLength: 1 }}
          fill="none"
          initial={{ pathLength: 0 }}
          points={toPolyline(outputPoints)}
          stroke="#14b8a6"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="3"
        />
        {totalPoints.map((point, index) => (
          <g key={data[index].label}>
            <motion.circle animate={{ scale: 1 }} cx={point.x} cy={point.y} fill="#0f766e" initial={{ scale: 0 }} r="4" />
            <text fill="#64748b" fontSize="11" fontWeight="800" textAnchor="middle" x={point.x} y={height - 8}>
              {data[index].label}
            </text>
          </g>
        ))}
      </svg>
      <div className="mt-3 flex flex-wrap gap-3 text-xs font-black text-slate-500">
        <ChartLegend color="#0f766e" label="Total" />
        <ChartLegend color="#64748b" label="Input" />
        <ChartLegend color="#14b8a6" label="Output" />
      </div>
    </motion.div>
  )
}

function ChartLegend({ color, label }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className="size-2 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  )
}

function ModelComparisonCard({ isBestQuality, isLowestToken, model }) {
  return (
    <motion.article
      className={cn(
        'rounded-lg border bg-white/76 p-4 shadow-sm',
        isBestQuality ? 'border-teal-200 shadow-teal-100/70' : 'border-white/80',
      )}
      whileHover={{ y: -4, rotateX: 1.2, rotateY: -1.2 }}
      whileTap={{ scale: 0.985 }}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-lg font-black tracking-tight">{model.name}</p>
          <p className="mt-1 text-xs font-bold text-slate-500">{model.recommendation}</p>
        </div>
        <div className="flex flex-col gap-1">
          {isBestQuality ? <span className="rounded-full bg-teal-50 px-2 py-1 text-[10px] font-black text-primary">Best quality</span> : null}
          {isLowestToken ? <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-black text-slate-700">Low tokens</span> : null}
        </div>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <MiniStat label="Tokens" value={formatCompact(model.totalTokens)} />
        <MiniStat label="Quality" value={`${Math.round(model.quality * 100)}%`} />
        <MiniStat label="Latency" value={`${model.latency}s`} />
        <MiniStat label="Cost" value={`$${formatMoney(model.cost)}`} />
      </div>
    </motion.article>
  )
}

function MiniStat({ label, value }) {
  return (
    <div className="rounded-lg bg-slate-50/90 p-3">
      <p className="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">{label}</p>
      <p className="mt-1 text-base font-black text-slate-950">{value}</p>
    </div>
  )
}

function ModelComparisonBars({ models }) {
  const maxTokens = Math.max(...models.map((model) => model.totalTokens), 1)
  return (
    <div className="rounded-lg bg-slate-50/80 p-4">
      <p className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">Token + Quality chart</p>
      <div className="mt-4 space-y-4">
        {models.map((model) => (
          <div key={model.id}>
            <div className="mb-2 flex items-center justify-between text-xs font-black">
              <span>{model.name}</span>
              <span className="text-slate-500">{formatCompact(model.totalTokens)} / {Math.round(model.quality * 100)}%</span>
            </div>
            <div className="grid gap-2">
              <div className="h-3 overflow-hidden rounded-full bg-white">
                <motion.div
                  className="h-full rounded-full bg-[#0f766e]"
                  initial={{ width: 0 }}
                  whileInView={{ width: `${(model.totalTokens / maxTokens) * 100}%` }}
                  viewport={{ once: true }}
                />
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-white">
                <motion.div
                  className="h-full rounded-full bg-[#14b8a6]"
                  initial={{ width: 0 }}
                  whileInView={{ width: `${model.quality * 100}%` }}
                  viewport={{ once: true }}
                />
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap gap-3 text-xs font-black text-slate-500">
        <ChartLegend color="#0f766e" label="Token usage" />
        <ChartLegend color="#14b8a6" label="Quality score" />
      </div>
    </div>
  )
}

function InsightCard({ label, note, value }) {
  return (
    <motion.div className="rounded-lg border border-white/80 bg-white/74 p-4" whileHover={{ x: 3 }}>
      <p className="text-xs font-black uppercase tracking-[0.12em] text-slate-400">{label}</p>
      <p className="mt-1 text-lg font-black text-slate-950">{value}</p>
      <p className="mt-1 text-xs font-semibold leading-5 text-slate-500">{note}</p>
    </motion.div>
  )
}

function ProgressTile({ danger = false, label, percent, value }) {
  return (
    <div className="rounded-lg bg-white/72 p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-black">{label}</p>
        <p className="text-2xl font-black">{value}</p>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
        <motion.div className={cn('h-full rounded-full', danger ? 'bg-red-400' : 'bg-[#14b8a6]')} animate={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}

function SectionTitle({ icon: Icon, subtitle, title }) {
  return (
    <div className="flex items-start gap-3">
      <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-teal-50 text-primary">
        <Icon size={18} />
      </div>
      <div>
        <h2 className="text-lg font-black tracking-tight">{title}</h2>
        <p className="text-sm font-semibold text-slate-500">{subtitle}</p>
      </div>
    </div>
  )
}

function DataTable({ columns, rows }) {
  return (
    <Panel className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[920px] border-collapse text-left text-sm">
          <thead className="bg-white/52 text-xs font-black uppercase tracking-[0.12em] text-slate-500">
            <tr>
              {columns.map((column) => <th className="border-b border-slate-200 px-4 py-3" key={column}>{column}</th>)}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row, index) => (
              <motion.tr className="bg-white/58 transition hover:bg-teal-50/70" key={index} whileHover={{ scale: 1.002 }}>
                {row.map((cell, cellIndex) => <td className="px-4 py-4 align-top text-slate-700" key={cellIndex}>{cell}</td>)}
              </motion.tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function RowActions({ children, className }) {
  return <div className={cn('flex flex-wrap items-center gap-1', className)}>{children}</div>
}

function Identity({ subtitle, title }) {
  return (
    <div className="min-w-0">
      <p className="font-black text-slate-950">{title}</p>
      <p className="text-xs font-semibold text-slate-500">{subtitle}</p>
    </div>
  )
}

function DrawerModal({ children, onClose, title }) {
  return (
    <AnimatePresence>
      <motion.div className="fixed inset-0 z-50 flex justify-end bg-slate-950/30 p-3 backdrop-blur-sm" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
        <motion.aside className="os-panel h-full w-full max-w-xl overflow-y-auto p-5" initial={{ x: 40, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: 30, opacity: 0 }}>
          <div className="mb-5 flex items-start justify-between gap-3">
            <h2 className="text-xl font-black tracking-tight">{title}</h2>
            <IconButton label="Close" onClick={onClose}><X size={16} /></IconButton>
          </div>
          {children}
        </motion.aside>
      </motion.div>
    </AnimatePresence>
  )
}

function DetailGrid({ items }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {items.map(([label, value]) => (
        <div className="rounded-lg bg-white/72 p-3" key={label}>
          <p className="text-xs font-black uppercase tracking-[0.12em] text-slate-400">{label}</p>
          <p className="mt-1 text-sm font-black text-slate-800">{value}</p>
        </div>
      ))}
    </div>
  )
}

function Pipeline({ active, steps }) {
  return (
    <div className="mt-4 grid grid-cols-4 gap-2">
      {steps.map((step) => (
        <div className="rounded-lg bg-white/72 p-2 text-center text-[10px] font-black text-slate-600" key={step}>
          <CheckCircle2 className={active.includes(step) ? 'mx-auto mb-1 text-primary' : 'mx-auto mb-1 text-slate-300'} size={13} />
          {step}
        </div>
      ))}
    </div>
  )
}

function SettingSelect({ label, onChange, options, value }) {
  return (
    <label className="rounded-lg bg-white/72 p-4">
      <span className="mb-2 block text-xs font-black uppercase tracking-[0.12em] text-slate-500">{label}</span>
      <select className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-black outline-none focus:border-teal-400" onChange={(event) => onChange(event.target.value)} value={value}>
        {options.map((option) => <option key={option}>{option}</option>)}
      </select>
    </label>
  )
}

function NumberSetting({ label, onChange, step = 1, value }) {
  return (
    <label className="rounded-lg bg-white/72 p-4">
      <span className="mb-2 block text-xs font-black uppercase tracking-[0.12em] text-slate-500">{label}</span>
      <input className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-black outline-none focus:border-teal-400" onChange={(event) => onChange(Number(event.target.value))} step={step} type="number" value={value} />
    </label>
  )
}

function NativeBarChart({ data }) {
  return (
    <div className="space-y-3">
      {data.map((item) => (
        <div key={item.label}>
          <div className="mb-1 flex justify-between text-xs font-black text-slate-500">
            <span>{item.label}</span>
            <span>{item.rag}% / {item.fineTune}%</span>
          </div>
          <div className="grid gap-1">
            <div className="h-3 overflow-hidden rounded-full bg-slate-100">
              <motion.div className="h-full rounded-full bg-[#14b8a6]" initial={{ width: 0 }} whileInView={{ width: `${item.rag}%` }} viewport={{ once: true }} />
            </div>
            <div className="h-3 overflow-hidden rounded-full bg-slate-100">
              <motion.div className="h-full rounded-full bg-slate-300" initial={{ width: 0 }} whileInView={{ width: `${item.fineTune}%` }} viewport={{ once: true }} />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

function MiniLineChart({ offset = 0 }) {
  const points = [18 + offset, 42, 34 + offset / 2, 64, 52 + offset, 84]
  return (
    <svg aria-hidden="true" className="h-full w-full" viewBox="0 0 240 100">
      <path d="M0 86 C 40 68, 44 42, 82 50 S 140 18, 174 38 S 210 78, 240 18" fill="none" stroke="#0f766e" strokeWidth="5" strokeLinecap="round" opacity=".12" />
      <polyline fill="none" points={points.map((y, index) => `${index * 48},${100 - y}`).join(' ')} stroke="#14b8a6" strokeLinecap="round" strokeLinejoin="round" strokeWidth="7" />
      {points.map((y, index) => <circle cx={index * 48} cy={100 - y} fill="#0f766e" key={index} r="4" />)}
    </svg>
  )
}

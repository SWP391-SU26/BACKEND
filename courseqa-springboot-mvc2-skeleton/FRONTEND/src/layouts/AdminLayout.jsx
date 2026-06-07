import { motion } from 'framer-motion'
import {
  Activity,
  BarChart3,
  BookOpen,
  Brain,
  ClipboardList,
  Database,
  FileText,
  FlaskConical,
  Gauge,
  Home,
  ListTree,
  Search,
  Settings,
  Users,
} from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { cn } from '../utils/cn.js'

const adminNav = [
  { href: '/admin/dashboard', label: 'Dashboard', icon: Gauge },
  { href: '/admin/users', label: 'Users', icon: Users },
  { href: '/admin/documents', label: 'Documents', icon: FileText },
  { href: '/admin/subjects', label: 'Subjects', icon: BookOpen },
  { href: '/admin/indexing', label: 'Indexing', icon: Database },
  { href: '/admin/model-settings', label: 'Model Settings', icon: Settings },
  { href: '/admin/test-set', label: 'Test Set', icon: ClipboardList },
  { href: '/admin/experiments', label: 'Experiments', icon: FlaskConical },
  { href: '/admin/research-dashboard', label: 'Research', icon: BarChart3 },
  { href: '/admin/logs', label: 'Logs', icon: Activity },
]

function AdminLayout() {
  return (
    <div className="app-ambient min-h-screen text-slate-950">
      <div className="ambient-lines" />
      <div className="noise-layer" />
      <div className="motion-field" />

      <div className="mx-auto flex min-h-screen max-w-[1680px] flex-col gap-3 p-3 lg:flex-row lg:p-4">
        <aside className="notebook-panel lg:sticky lg:top-4 lg:h-[calc(100vh-2rem)] lg:w-[292px] lg:shrink-0">
          <div className="source-glow" />
          <div className="relative border-b border-border p-4">
            <NavLink className="flex items-center gap-3" to="/workspace">
              <span className="flex h-11 w-28 items-center rounded-xl border border-border bg-white px-2 shadow-sm">
                <img
                  alt="FStu"
                  className="w-full object-contain"
                  src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
                />
              </span>
              <span>
                <span className="block text-lg font-black tracking-tight">FStu Admin</span>
                <span className="block text-[11px] font-black uppercase tracking-[0.14em] text-slate-500">
                  Knowledge operations
                </span>
              </span>
            </NavLink>
          </div>

          <nav className="relative flex gap-2 overflow-x-auto p-3 lg:block lg:space-y-1 lg:overflow-visible">
            {adminNav.map((item) => {
              const Icon = item.icon

              return (
                <NavLink
                  className={({ isActive }) =>
                    cn(
                      'flex min-w-max items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-black transition lg:min-w-0',
                      isActive
                        ? 'bg-primary text-white shadow-[0_12px_24px_rgba(15,118,110,.2)]'
                        : 'text-slate-600 hover:bg-teal-50 hover:text-primary',
                    )
                  }
                  key={item.href}
                  to={item.href}
                >
                  <Icon size={17} />
                  {item.label}
                </NavLink>
              )
            })}
          </nav>
        </aside>

        <div className="min-w-0 flex-1">
          <header className="notebook-panel mb-3 flex flex-wrap items-center justify-between gap-3 p-3">
            <motion.div
              className="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-xl border border-border bg-white/90 px-3 text-sm font-semibold text-slate-500 shadow-sm"
              whileHover={{ y: -1 }}
            >
              <Search size={16} />
              <span className="truncate">Search users, documents, jobs, experiments...</span>
            </motion.div>
            <div className="flex items-center gap-2">
              <div className="hidden rounded-lg bg-emerald-50 px-3 py-2 text-xs font-black text-emerald-700 sm:flex">
                System healthy
              </div>
              <NavLink
                className="grid size-10 place-items-center rounded-xl border border-border bg-white/90 text-slate-600 shadow-sm transition hover:bg-teal-50 hover:text-primary"
                title="Back to workspace"
                to="/workspace"
              >
                <Home size={16} />
              </NavLink>
              <div className="grid size-10 place-items-center rounded-full bg-primary text-xs font-black text-white">
                AD
              </div>
            </div>
          </header>

          <main className="pb-8">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}

export function AdminPageHeader({ actions, description, icon: Icon = Brain, title }) {
  return (
    <section className="notebook-panel mb-4 overflow-hidden p-5">
      <div className="pointer-events-none absolute inset-0 opacity-55">
        <div className="abstract-canvas" />
      </div>
      <div className="relative flex flex-wrap items-end justify-between gap-4">
        <div className="max-w-3xl">
          <div className="mb-3 grid size-11 place-items-center rounded-xl bg-teal-50 text-primary shadow-lg shadow-teal-100">
            <Icon size={20} />
          </div>
          <h1 className="text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
            {title}
          </h1>
          <p className="mt-2 text-sm font-semibold leading-6 text-slate-600">
            {description}
          </p>
        </div>
        {actions ? <div className="relative flex flex-wrap gap-2">{actions}</div> : null}
      </div>
    </section>
  )
}

export function MiniTabs({ items, value, onChange }) {
  return (
    <div className="flex overflow-x-auto rounded-lg bg-white/72 p-1 shadow-inner">
      {items.map((item) => (
        <button
          className={cn(
            'min-h-9 min-w-max rounded-lg px-3 text-xs font-black transition',
            value === item ? 'bg-primary text-white' : 'text-slate-500 hover:bg-white',
          )}
          key={item}
          onClick={() => onChange(item)}
          type="button"
        >
          {item}
        </button>
      ))}
    </div>
  )
}

export default AdminLayout

import { motion } from 'framer-motion'
import {
  Bell,
  Library,
  MessageSquareText,
  ShieldCheck,
  Settings2,
} from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { getSavedUser } from '../services/authService.js'
import { cn } from '../utils/cn.js'

const navItems = [
  { href: '/workspace', label: 'AI Chat', icon: MessageSquareText, end: true },
  { href: '/library', label: 'Library', icon: Library },
]

function MainLayout() {
  const savedUser = getSavedUser()
  const isAdmin = savedUser?.role === 'admin'

  return (
    <div className="app-ambient min-h-screen text-slate-950">
      <div className="ambient-lines" />
      <div className="noise-layer" />
      <div className="motion-field" />

      <header className="sticky top-0 z-40 px-3 py-3 sm:px-5">
        <div className="mx-auto grid max-w-[1560px] grid-cols-[1fr_auto_1fr] items-center gap-3">
          <NavLink className="group flex min-w-0 items-center gap-3" to="/workspace">
            <motion.span
              className="flex h-11 w-28 shrink-0 items-center rounded-xl border border-border bg-white px-2 shadow-sm"
              whileHover={{ y: -2, scale: 1.04 }}
              whileTap={{ scale: 0.95 }}
              transition={{ type: 'spring', stiffness: 430, damping: 28 }}
            >
              <img
                alt="FStu"
                className="w-full object-contain"
                src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
              />
            </motion.span>
            <span className="hidden min-w-0 sm:block">
              <span className="block text-lg font-black leading-none tracking-tight text-slate-950">
                FStu
              </span>
              <span className="block truncate text-[11px] font-black uppercase tracking-[0.14em] text-slate-500">
                Learning workspace
              </span>
            </span>
          </NavLink>

          <nav className="nav-pill flex max-w-[92vw] items-center gap-1 p-1">
            {navItems.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  className={({ isActive }) =>
                    cn(
                      'relative inline-flex min-h-10 items-center gap-2 rounded-full px-3 text-sm font-black transition',
                      'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500',
                      isActive
                        ? 'bg-primary text-primary-foreground shadow-[0_12px_24px_rgba(15,118,110,.2)]'
                        : 'text-slate-500 hover:bg-teal-50 hover:text-primary',
                    )
                  }
                  end={item.end}
                  key={item.href}
                  to={item.href}
                >
                  <Icon size={16} />
                  <span className="hidden md:inline">{item.label}</span>
                </NavLink>
              )
            })}
          </nav>

          <div className="flex min-w-0 items-center justify-end gap-2">
            {isAdmin ? (
              <NavLink
                className="nav-pill hidden min-h-10 items-center gap-2 px-3 text-sm font-black text-slate-600 transition hover:text-primary sm:flex"
                to="/admin"
              >
                <ShieldCheck size={15} />
                Admin
              </NavLink>
            ) : null}
            <NavLink
              aria-label="Settings"
              className="nav-pill relative grid size-10 place-items-center text-slate-600 transition hover:text-primary"
              title="Settings"
              to="/settings"
            >
              <Settings2 size={16} />
            </NavLink>
            <QuickIcon label="Notifications">
              <Bell size={16} />
              <span className="absolute right-2 top-2 size-2 rounded-full bg-amber-400" />
            </QuickIcon>
            <motion.button
              aria-label="User account"
              className="grid size-10 place-items-center rounded-full border border-teal-100 bg-primary text-xs font-black text-white shadow-lg shadow-teal-900/10"
              whileHover={{ y: -2, scale: 1.04 }}
              whileTap={{ scale: 0.96 }}
            >
              FS
            </motion.button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1560px] overflow-x-clip px-3 pb-10 pt-2 sm:px-5 lg:px-7">
        <Outlet />
      </main>
    </div>
  )
}

function QuickIcon({ children, label }) {
  return (
    <motion.button
      aria-label={label}
      className="nav-pill relative grid size-10 place-items-center text-slate-600 hover:text-primary"
      title={label}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.95 }}
    >
      {children}
    </motion.button>
  )
}

export default MainLayout

import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  FileArchive,
  Loader2,
  Search,
  XCircle,
} from 'lucide-react'
import { cn } from '../utils/cn.js'

const statusStyles = {
  Indexed: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  Processing: 'border-amber-200 bg-amber-50 text-amber-700',
  Uploaded: 'border-teal-200 bg-teal-50 text-teal-700',
  Failed: 'border-red-200 bg-red-50 text-red-700',
}

const statusIcons = {
  Indexed: CheckCircle2,
  Processing: Loader2,
  Uploaded: Clock3,
  Failed: XCircle,
}

const buttonMotion = {
  whileHover: { y: -2, scale: 1.01 },
  whileTap: { y: 0, scale: 0.97 },
  transition: { type: 'spring', stiffness: 460, damping: 30 },
}

export function Button({
  children,
  className,
  size = 'md',
  variant = 'primary',
  ...props
}) {
  const variants = {
    primary:
      'bg-primary text-primary-foreground shadow-[0_14px_28px_rgba(15,118,110,.18)] hover:bg-teal-800 disabled:bg-slate-300 disabled:shadow-none',
    secondary:
      'border border-border bg-white/90 text-slate-700 shadow-[0_10px_22px_rgba(15,118,110,.07)] backdrop-blur-xl hover:border-teal-200 hover:bg-white hover:text-primary',
    ghost:
      'text-slate-600 hover:bg-teal-50 hover:text-primary',
    danger:
      'border border-red-200 bg-white/90 text-red-600 shadow-sm backdrop-blur-xl hover:bg-red-50',
    accent:
      'border border-teal-200 bg-teal-50 text-primary shadow-[0_12px_24px_rgba(15,118,110,.12)] hover:bg-teal-100',
  }
  const sizes = {
    sm: 'min-h-8 px-3 text-xs',
    md: 'min-h-10 px-4 text-sm',
    lg: 'min-h-12 px-5 text-sm',
    icon: 'size-9 justify-center p-0',
  }

  return (
    <motion.button
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl font-black transition-colors duration-200',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500',
        'disabled:cursor-not-allowed disabled:opacity-70',
        variants[variant],
        sizes[size],
        className,
      )}
      {...buttonMotion}
      {...props}
    >
      {children}
    </motion.button>
  )
}

export function IconButton({ label, children, className, ...props }) {
  return (
    <motion.button
      aria-label={label}
      className={cn(
        'grid size-9 place-items-center rounded-xl text-slate-500 transition-colors duration-200 hover:bg-teal-50 hover:text-primary hover:shadow-sm',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500',
        className,
      )}
      title={label}
      {...buttonMotion}
      {...props}
    >
      {children}
    </motion.button>
  )
}

export function StatusBadge({ status }) {
  const Icon = statusIcons[status] ?? FileArchive

  return (
    <motion.span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-lg border px-2 py-1 text-xs font-black shadow-sm',
        statusStyles[status] ?? 'border-slate-200 bg-slate-50 text-slate-600',
      )}
      initial={{ scale: 0.94, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ duration: 0.2 }}
    >
      <Icon className={status === 'Processing' ? 'animate-spin' : ''} size={13} />
      {status}
    </motion.span>
  )
}

export function Field({ icon: Icon = Search, label = 'Search', ...props }) {
  return (
    <label className="group flex min-h-11 items-center gap-2 rounded-xl border border-border bg-white/90 px-3 text-sm text-slate-500 shadow-[0_10px_24px_rgba(15,118,110,.06)] backdrop-blur-xl transition focus-within:border-teal-400 focus-within:bg-white focus-within:ring-4 focus-within:ring-teal-100">
      <Icon className="transition group-focus-within:text-primary" size={16} />
      <input
        aria-label={label}
        className="w-full bg-transparent text-sm font-semibold text-slate-900 outline-none placeholder:text-slate-400"
        {...props}
      />
    </label>
  )
}

export function SelectField({ label, children, ...props }) {
  return (
    <label className="flex min-h-11 items-center rounded-xl border border-border bg-white/90 px-3 text-sm text-slate-600 shadow-[0_10px_24px_rgba(15,118,110,.06)] backdrop-blur-xl transition focus-within:border-teal-400 focus-within:bg-white focus-within:ring-4 focus-within:ring-teal-100">
      <span className="sr-only">{label}</span>
      <select className="w-full bg-transparent text-sm font-black outline-none" {...props}>
        {children}
      </select>
    </label>
  )
}

export function Panel({ children, className }) {
  return (
    <motion.section
      className={cn('os-panel', className)}
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.08 }}
      transition={{ duration: 0.42, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.section>
  )
}

export function BentoCard({ children, className, delay = 0, ...props }) {
  return (
    <motion.div
      className={cn('bento-card', className)}
      initial={{ opacity: 0, y: 20, scale: 0.98 }}
      whileInView={{ opacity: 1, y: 0, scale: 1 }}
      whileHover={{ y: -7, rotateX: 1.4, rotateY: -1.2 }}
      whileTap={{ scale: 0.985 }}
      viewport={{ once: true, amount: 0.12 }}
      transition={{ duration: 0.42, delay, ease: [0.22, 1, 0.36, 1] }}
      {...props}
    >
      {children}
    </motion.div>
  )
}

export function EmptyState({ title, description, action }) {
  return (
    <motion.div
      className="os-panel relative overflow-hidden p-6"
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.42 }}
    >
      <div className="pointer-events-none absolute inset-0 opacity-70">
        <div className="abstract-canvas" />
      </div>
      <div className="relative max-w-2xl">
        <div className="mb-3 grid size-12 place-items-center rounded-xl bg-teal-50 text-primary shadow-lg shadow-teal-100">
          <AlertTriangle size={21} />
        </div>
        <h2 className="text-xl font-black tracking-tight text-slate-950">{title}</h2>
        <p className="mt-2 max-w-xl text-sm font-medium leading-6 text-slate-600">
          {description}
        </p>
        {action ? <div className="mt-5">{action}</div> : null}
      </div>
    </motion.div>
  )
}

export function ConfirmModal({
  actionLabel = 'Confirm',
  children,
  onCancel,
  onConfirm,
  title,
}) {
  return (
    <AnimatePresence>
      <motion.div
        className="fixed inset-0 z-50 grid place-items-center bg-slate-950/38 p-4 backdrop-blur-md"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        <motion.div
          className="os-panel w-full max-w-md p-5 shadow-2xl"
          initial={{ opacity: 0, y: 30, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 16, scale: 0.98 }}
          transition={{ type: 'spring', stiffness: 360, damping: 30 }}
        >
          <h2 className="text-lg font-black tracking-tight text-slate-950">{title}</h2>
          <div className="mt-2 text-sm font-medium leading-6 text-slate-600">
            {children}
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <Button onClick={onCancel} variant="secondary">
              Cancel
            </Button>
            <Button onClick={onConfirm} variant="danger">
              {actionLabel}
            </Button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  )
}

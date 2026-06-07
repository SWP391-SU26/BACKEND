import { ChevronDown, Globe } from 'lucide-react'
import Button from '../common/Button.jsx'
import { cn } from '../../utils/cn.js'

const navItems = [
  { label: 'How It Works', href: '#workflow' },
  { label: 'Documents', href: '#features' },
  { label: 'Students', href: '#features' },
  { label: 'Professors', href: '#research' },
  { label: 'FAQs', href: '#research' },
]

const headerThemes = {
  hero: {
    shell:
      'border-teal-100/80 bg-white/90 shadow-[0_18px_60px_rgba(15,118,110,0.10)]',
    nav: 'text-slate-500',
    navHover: 'hover:text-teal-900',
    language: 'text-slate-500 hover:bg-teal-50 hover:text-teal-900',
  },
  features: {
    shell:
      'border-teal-200/80 bg-teal-50/95 shadow-[0_18px_60px_rgba(15,118,110,0.14)]',
    nav: 'text-teal-800/70',
    navHover: 'hover:text-teal-950',
    language: 'text-teal-800/70 hover:bg-white hover:text-teal-950',
  },
  workflow: {
    shell:
      'border-slate-200/80 bg-white/95 shadow-[0_18px_60px_rgba(15,23,42,0.08)]',
    nav: 'text-slate-500',
    navHover: 'hover:text-teal-900',
    language: 'text-slate-500 hover:bg-teal-50 hover:text-teal-900',
  },
  research: {
    shell:
      'border-emerald-200/80 bg-emerald-50/95 shadow-[0_18px_60px_rgba(15,118,110,0.14)]',
    nav: 'text-emerald-800/70',
    navHover: 'hover:text-emerald-950',
    language: 'text-emerald-800/70 hover:bg-white hover:text-emerald-950',
  },
  footer: {
    shell:
      'border-slate-200/80 bg-white/95 shadow-[0_18px_60px_rgba(15,23,42,0.08)]',
    nav: 'text-slate-500',
    navHover: 'hover:text-teal-900',
    language: 'text-slate-500 hover:bg-teal-50 hover:text-teal-900',
  },
}

function LandingHeader({ activeTheme = 'hero' }) {
  const theme = headerThemes[activeTheme] ?? headerThemes.hero

  return (
    <header className="pointer-events-none fixed left-0 right-0 top-0 z-20 px-8 pt-6">
      <div
        className={cn(
          'pointer-events-auto mx-auto flex h-[68px] max-w-6xl items-center justify-between rounded-full px-4 pl-5 backdrop-blur-xl transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]',
          theme.shell,
        )}
      >
        <a className="group flex items-center" href="/">
          <img
            alt="FStu"
            className="h-10 w-auto object-contain transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)] group-hover:scale-[0.98]"
            src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
          />
        </a>

        <nav
          className={cn(
            'hidden items-center gap-7 text-sm font-medium transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)] lg:flex',
            theme.nav,
          )}
        >
          {navItems.map((item) => (
            <a
              className={cn('transition duration-300', theme.navHover)}
              href={item.href}
              key={item.href}
            >
              {item.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <button
            className={cn(
              'hidden h-10 items-center gap-2 rounded-full px-3 text-sm font-medium transition duration-300 md:inline-flex',
              theme.language,
            )}
            type="button"
          >
            <Globe className="size-4" strokeWidth={1.8} />
            <span>English</span>
            <ChevronDown className="size-4" strokeWidth={1.8} />
          </button>
          <Button
            as="a"
            className="rounded-full px-5"
            href="/login"
            size="sm"
            variant="cta"
          >
            Let's get started
          </Button>
        </div>
      </div>
    </header>
  )
}

export default LandingHeader

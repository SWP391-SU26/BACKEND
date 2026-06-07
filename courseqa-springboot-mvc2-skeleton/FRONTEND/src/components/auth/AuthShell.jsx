import { Link } from 'react-router-dom'

function AuthShell({
  asideAlign = 'left',
  asideEyebrow,
  asideText,
  asideTitle,
  children,
  highlights,
}) {
  const aside = (
    <aside
      className={[
        'relative flex flex-col justify-between overflow-hidden rounded-[1.75rem] bg-primary p-10 text-primary-foreground',
        asideAlign === 'left'
          ? 'animate-auth-panel-left'
          : 'animate-auth-panel-right animation-delay-150',
      ].join(' ')}
    >
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_15%,rgba(255,255,255,0.24),transparent_34%),radial-gradient(circle_at_85%_80%,rgba(255,255,255,0.16),transparent_30%)]" />
      <div className="relative">
        <Link className="inline-flex rounded-2xl bg-white/95 p-2" to="/">
          <img
            alt="FStu"
            className="h-12 w-auto object-contain"
            src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
          />
        </Link>

        <div className="mt-16 max-w-lg">
          <p className="text-[10px] font-extrabold uppercase tracking-[0.22em] text-white/70">
            {asideEyebrow}
          </p>
          <h1 className="mt-5 text-5xl font-semibold leading-[1.02] tracking-tight">
            {asideTitle}
          </h1>
          <p className="mt-6 text-base leading-8 text-white/78">{asideText}</p>
        </div>
      </div>

      <div className="relative grid gap-3">
        {highlights.map((item) => (
          <div
            className="rounded-2xl border border-white/15 bg-white/10 px-4 py-3 text-sm font-semibold text-white/88"
            key={item}
          >
            {item}
          </div>
        ))}
      </div>
    </aside>
  )

  const formPanel = (
    <div
      className={[
        'flex items-center justify-center px-12 py-10',
        asideAlign === 'left'
          ? 'animate-auth-panel-right animation-delay-150'
          : 'animate-auth-panel-left',
      ].join(' ')}
    >
      <section className="w-full max-w-md">{children}</section>
    </div>
  )

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_10%_10%,hsl(var(--primary)/0.12),transparent_30%),linear-gradient(135deg,#ffffff_0%,#f6f8fb_52%,#eef3f8_100%)] px-8 py-8 font-body text-foreground">
      <section className="animate-auth-shell mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl grid-cols-[1.05fr_0.95fr] overflow-hidden rounded-[2.25rem] border border-white/80 bg-white/70 p-2 shadow-[0_34px_120px_rgba(15,23,42,0.12)]">
        {asideAlign === 'left' ? (
          <>
            {aside}
            {formPanel}
          </>
        ) : (
          <>
            {formPanel}
            {aside}
          </>
        )}
      </section>
    </main>
  )
}

export default AuthShell

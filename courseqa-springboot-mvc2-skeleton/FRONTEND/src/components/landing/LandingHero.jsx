import Button from '../common/Button.jsx'

function LandingHero() {
  return (
    <section
      className="relative flex min-h-[90vh] items-center justify-center overflow-hidden px-8 pb-16 pt-32"
      data-section-theme="hero"
    >
      <video
        aria-hidden="true"
        autoPlay
        className="absolute inset-0 h-full w-full object-cover"
        loop
        muted
        playsInline
        src="/hero-bg.mp4"
      />
      <div className="absolute inset-0 bg-background/70 backdrop-blur-sm" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_10%,hsl(var(--primary)/0.16),transparent_36%),linear-gradient(180deg,transparent_0%,hsl(var(--background))_92%)]" />

      <div className="relative z-10 flex w-full max-w-7xl flex-col items-center text-center">
        <div className="animate-fade-up rounded-full border border-border bg-background/80 px-4 py-2 text-[10px] font-extrabold uppercase tracking-[0.22em] text-primary shadow-[0_14px_40px_rgba(15,23,42,0.05)]">
          FPT HCM / SWP Project
        </div>

        <h1 className="mt-7 max-w-4xl font-display text-5xl font-medium leading-[1.1] tracking-tight text-foreground md:text-7xl">
          Master Course Documents With{' '}
          <span className="text-primary">AI-Powered QA</span>
        </h1>

        <p className="mt-6 max-w-2xl text-lg leading-relaxed text-muted-foreground">
          Upload lecture materials, ask course-aware questions, compare RAG
          with fine-tuning, and verify answers through source citations.
        </p>

        <div className="mt-10">
          <Button
            as="a"
            className="rounded-full px-10 py-6 text-base font-semibold shadow-lg shadow-primary/25"
            href="/login"
            size="lg"
            variant="cta"
          >
            Let's get started
          </Button>
        </div>

        <div className="mt-14 w-full max-w-5xl animate-fade-up animation-delay-150 rounded-[1.5rem] border border-border bg-background/80 p-2 shadow-2xl shadow-primary/10">
          <img
            alt="Dashboard preview for FStu"
            className="h-auto w-full rounded-2xl border border-border object-cover"
            src="https://placehold.co/1200x720/f6f8fb/0f766e?text=FStu+Dashboard"
          />
        </div>
      </div>
    </section>
  )
}

export default LandingHero

function LandingFooter() {
  return (
    <footer
      className="border-t border-slate-200 bg-[radial-gradient(circle_at_80%_0%,rgba(15,118,110,0.10),transparent_34%),linear-gradient(180deg,#ffffff_0%,#f6f8fb_100%)] px-8 py-10"
      data-section-theme="footer"
    >
      <div className="mx-auto max-w-7xl rounded-[2rem] border border-white/80 bg-white/70 p-2 shadow-[0_24px_90px_rgba(15,23,42,0.06)]">
        <div className="rounded-[1.5rem] border border-slate-200 bg-background px-8 py-8">
          <div className="grid grid-cols-[1.2fr_0.8fr_0.8fr_1fr] gap-10">
            <div>
              <img
                alt="FStu"
                className="h-12 w-auto object-contain"
                src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
              />
              <p className="mt-5 max-w-sm text-sm leading-6 text-muted-foreground">
                FStu helps students ask questions over course materials,
                compare RAG with fine-tuning, and verify answers through
                citations.
              </p>
            </div>

            <FooterGroup
              links={[
                ['How It Works', '#workflow'],
                ['Document QA', '#features'],
                ['Research', '#research'],
              ]}
              title="Product"
            />

            <FooterGroup
              links={[
                ['Log in', '/login'],
                ['Register', '/register'],
                ['Workspace', '/app'],
              ]}
              title="Access"
            />

            <div>
              <h2 className="text-sm font-extrabold text-foreground">
                Contact
              </h2>
              <div className="mt-4 space-y-3 text-sm leading-6 text-muted-foreground">
                <p>FPT University HCMC</p>
                <p>fstu.support@fpt.edu.vn</p>
                <p>+84 28 7300 5588</p>
                <p>District 9, Ho Chi Minh City</p>
              </div>
            </div>
          </div>

          <div className="mt-8 flex items-center justify-between border-t border-border pt-6 text-xs font-semibold text-muted-foreground">
            <p>© 2026 FStu. Built for the SWP project demo.</p>
            <p>RAG / Fine-tuning / RAGAS Evaluation</p>
          </div>
        </div>
      </div>
    </footer>
  )
}

function FooterGroup({ links, title }) {
  return (
    <div>
      <h2 className="text-sm font-extrabold text-foreground">{title}</h2>
      <div className="mt-4 grid gap-3 text-sm font-semibold text-muted-foreground">
        {links.map(([label, href]) => (
          <a
            className="transition duration-300 hover:translate-x-1 hover:text-primary"
            href={href}
            key={href}
          >
            {label}
          </a>
        ))}
      </div>
    </div>
  )
}

export default LandingFooter

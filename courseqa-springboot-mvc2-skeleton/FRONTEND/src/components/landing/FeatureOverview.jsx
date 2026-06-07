const features = [
  {
    title: 'Document upload',
    description:
      'Students upload PDFs, DOCX files, and lecture slides for chunking and indexing.',
    size: 'large',
  },
  {
    title: 'Document-grounded chat',
    description:
      'Questions are answered inside the indexed course material scope.',
  },
  {
    title: 'Source citations',
    description:
      'Each RAG answer can point back to the original file, page, and chunk.',
  },
  {
    title: 'Session history',
    description:
      'Conversation sessions preserve context so students can continue studying.',
  },
  {
    title: 'RAG vs fine-tuning benchmark',
    description:
      'The project presents a research layer for comparing both approaches.',
    size: 'wide',
  },
]

function FeatureOverview() {
  return (
    <section
      className="border-t border-teal-100 bg-[radial-gradient(circle_at_15%_0%,rgba(15,118,110,0.18),transparent_32%),linear-gradient(180deg,#f0fdfa_0%,#ecfdf5_100%)] px-8 py-24"
      data-section-theme="features"
      id="features"
    >
      <div className="mx-auto max-w-7xl">
        <div className="max-w-2xl animate-fade-up">
          <p className="text-[10px] font-black uppercase tracking-[0.22em] text-teal-700">
            Features
          </p>
          <h2 className="mt-4 text-5xl font-black leading-[1] tracking-[-0.045em] text-teal-950">
            A study workspace with traceable answers.
          </h2>
          <p className="mt-5 text-base leading-7 text-teal-900/70">
            The system combines document management, contextual chat, citations,
            and benchmarking into one clear SWP demonstration flow.
          </p>
        </div>

        <div className="mt-10 grid grid-cols-6 gap-4">
          {features.map((feature, index) => (
            <div
              className={[
                'rounded-[2rem] border border-white/80 bg-white/60 p-1.5 shadow-[0_22px_80px_rgba(15,118,110,0.10)]',
                feature.size === 'large' ? 'col-span-3 row-span-2' : '',
                feature.size === 'wide' ? 'col-span-3' : '',
                !feature.size ? 'col-span-3' : '',
              ].join(' ')}
              key={feature.title}
            >
              <article className="h-full rounded-[1.55rem] border border-teal-100 bg-white p-6 transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)] hover:-translate-y-1 hover:border-teal-600">
                <div
                  className={[
                    'mb-10 h-2 w-16 rounded-full',
                    index % 3 === 0 ? 'bg-teal-600' : '',
                    index % 3 === 1 ? 'bg-teal-600' : '',
                    index % 3 === 2 ? 'bg-emerald-500' : '',
                  ].join(' ')}
                />
                <h3 className="text-xl font-black tracking-tight text-teal-950">
                  {feature.title}
                </h3>
                <p className="mt-3 max-w-md text-sm leading-6 text-teal-900/70">
                  {feature.description}
                </p>
              </article>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default FeatureOverview

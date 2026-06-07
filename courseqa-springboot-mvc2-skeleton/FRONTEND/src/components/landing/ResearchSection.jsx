const ragItems = [
  'Works well with newly uploaded materials.',
  'Returns clear source citations.',
  'Updates knowledge by indexing documents.',
]

const fineTuningItems = [
  'Learns response style and task patterns.',
  'Requires a prepared training dataset.',
  'Does not provide citations without retrieval.',
]

const metrics = [
  'Faithfulness',
  'Answer relevancy',
  'Context precision',
  'Context recall',
]

function ResearchSection() {
  return (
    <section
      className="border-t border-emerald-100 bg-[radial-gradient(circle_at_12%_0%,rgba(16,185,129,0.18),transparent_34%),linear-gradient(180deg,#ecfdf5_0%,#f0fdfa_100%)] px-8 py-24"
      data-section-theme="research"
      id="research"
    >
      <div className="mx-auto grid max-w-7xl grid-cols-[0.8fr_1.2fr] gap-12">
        <div className="animate-fade-up">
          <p className="text-[10px] font-black uppercase tracking-[0.22em] text-emerald-700">
            RBL / Research
          </p>
          <h2 className="mt-4 text-5xl font-black leading-[1] tracking-[-0.045em] text-emerald-950">
            More than a chatbot. A research-backed comparison.
          </h2>
          <p className="mt-5 text-base leading-7 text-emerald-950/70">
            The project presents a clear research track: compare RAG and
            fine-tuning, benchmark embedding models, and evaluate answers with
            RAGAS.
          </p>
        </div>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <ComparisonCard title="RAG" items={ragItems} />
            <ComparisonCard title="Fine-tuning" items={fineTuningItems} />
          </div>

          <div className="rounded-[2rem] border border-white/80 bg-white/55 p-1.5 shadow-[0_22px_80px_rgba(15,118,110,0.10)]">
            <div className="rounded-[1.55rem] border border-emerald-100 bg-white p-5">
            <div className="flex items-start justify-between gap-6">
              <div>
                <h3 className="text-lg font-black text-emerald-950">
                  Benchmark embedding model
                </h3>
                <p className="mt-2 max-w-md text-sm leading-6 text-emerald-950/70">
                  Compare retrieval quality to choose an embedding model that
                  fits course materials.
                </p>
              </div>
              <div className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-black text-emerald-700">
                Retrieval quality
              </div>
            </div>
            </div>
          </div>

          <div className="rounded-[2rem] border border-white/80 bg-white/55 p-1.5 shadow-[0_22px_80px_rgba(15,118,110,0.10)]">
            <div className="rounded-[1.55rem] border border-emerald-100 bg-white p-5">
            <h3 className="text-lg font-black text-emerald-950">
              RAGAS evaluation
            </h3>
            <div className="mt-4 grid grid-cols-4 gap-3">
              {metrics.map((metric) => (
                <div
                  className="rounded-2xl bg-emerald-50 px-3 py-4 text-center text-xs font-black text-emerald-900/75"
                  key={metric}
                >
                  {metric}
                </div>
              ))}
            </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function ComparisonCard({ title, items }) {
  return (
    <article className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-[0_18px_60px_rgba(15,118,110,0.08)]">
      <h3 className="text-lg font-black text-emerald-950">{title}</h3>
      <ul className="mt-4 space-y-3">
        {items.map((item) => (
          <li className="flex gap-3 text-sm leading-6 text-emerald-950/70" key={item}>
            <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-emerald-500" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </article>
  )
}

export default ResearchSection

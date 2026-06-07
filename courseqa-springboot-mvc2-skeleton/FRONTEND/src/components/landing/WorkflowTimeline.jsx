const steps = [
  {
    label: 'Input',
    title: 'Upload documents',
    description: 'Users add PDF, DOCX, or PPTX course materials.',
  },
  {
    label: 'Indexing',
    title: 'Chunk & embedding',
    description: 'The backend splits content into chunks and creates vectors.',
  },
  {
    label: 'Retrieval',
    title: 'Retrieve context',
    description: 'The question retrieves the most relevant document chunks.',
  },
  {
    label: 'Answer',
    title: 'Generate cited answer',
    description: 'The model answers with citations students can verify.',
  },
]

function WorkflowTimeline() {
  return (
    <section
      className="border-t border-slate-200 bg-[radial-gradient(circle_at_85%_0%,rgba(15,118,110,0.10),transparent_34%),linear-gradient(180deg,#ffffff_0%,#f6f8fb_100%)] px-8 py-24"
      data-section-theme="workflow"
      id="workflow"
    >
      <div className="mx-auto grid max-w-7xl grid-cols-[0.75fr_1.25fr] gap-12">
        <div className="animate-fade-up">
          <p className="text-[10px] font-black uppercase tracking-[0.22em] text-teal-700">
            Workflow
          </p>
          <h2 className="mt-4 text-5xl font-black leading-[1] tracking-[-0.045em] text-slate-950">
            From lecture files to grounded answers.
          </h2>
          <p className="mt-5 text-base leading-7 text-slate-600">
            The pipeline makes the RAG role visible: materials must be indexed
            before they become reliable sources for the chatbot.
          </p>
        </div>

        <div className="rounded-[2rem] border border-white/80 bg-white/70 p-1.5 shadow-[0_22px_80px_rgba(15,23,42,0.07)]">
          <div className="rounded-[1.55rem] border border-slate-200 bg-white p-5">
          <div className="grid grid-cols-4 gap-3">
            {steps.map((step, index) => (
              <article
                className="relative min-h-[240px] rounded-2xl bg-slate-50 p-5 transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)] hover:-translate-y-1"
                key={step.title}
              >
                {index < steps.length - 1 ? (
                  <div className="absolute right-[-14px] top-8 z-10 h-px w-7 bg-teal-600" />
                ) : null}
                <span className="text-[10px] font-black uppercase tracking-[0.18em] text-teal-700">
                  {step.label}
                </span>
                <h3 className="mt-10 text-base font-black text-slate-950">
                  {step.title}
                </h3>
                <p className="mt-3 text-sm leading-6 text-slate-600">
                  {step.description}
                </p>
              </article>
            ))}
          </div>
          </div>
        </div>
      </div>
    </section>
  )
}

export default WorkflowTimeline

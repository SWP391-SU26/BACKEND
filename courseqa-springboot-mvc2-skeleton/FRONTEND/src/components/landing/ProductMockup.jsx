const citations = [
  {
    source: 'SWP391_Chapter_2.pdf',
    meta: 'Page 12 / Context precision',
    text: 'RAG retrieves relevant chunks before the answer is generated.',
  },
  {
    source: 'Lecture_RAGAS.pptx',
    meta: 'Slide 18 / Faithfulness',
    text: 'Evaluation checks whether the answer is grounded in context.',
  },
]

function ProductMockup() {
  return (
    <div className="rounded-[2rem] border border-white/80 bg-white/50 p-2 shadow-[0_34px_120px_rgba(15,23,42,0.16)]">
      <div className="rounded-[1.5rem] border border-[#D8E0EA] bg-white p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.90)]">
        <div className="mb-4 flex items-center justify-between border-b border-[#E7ECF2] pb-4">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[0.22em] text-[#6B7280]">
              SWP391 workspace
            </p>
            <h2 className="mt-1 text-base font-black text-[#111827]">
              Course-grounded chat
            </h2>
          </div>
          <div className="flex rounded-full bg-[#F3F7FA] p-1 text-xs font-black">
            <span className="rounded-full bg-[#0F766E] px-3 py-1.5 text-white">
              RAG
            </span>
            <span className="px-3 py-1.5 text-[#6B7280]">Fine-tuning</span>
          </div>
        </div>

        <div className="grid grid-cols-[1fr_220px] gap-4">
          <div className="space-y-4 rounded-[1.25rem] bg-[#F6F8FB] p-4">
            <div className="ml-auto max-w-[78%] rounded-2xl rounded-tr-md bg-[#0F766E] px-4 py-3 text-sm leading-6 text-white shadow-[0_16px_34px_rgba(15,118,110,0.22)]">
              How is RAG different from fine-tuning for course document QA?
            </div>

            <div className="max-w-[86%] rounded-2xl rounded-tl-md border border-[#E7ECF2] bg-white px-4 py-3 text-sm leading-6 text-[#111827] shadow-[0_14px_40px_rgba(15,23,42,0.05)]">
              <p>
                RAG is better for newly uploaded course materials because it
                retrieves relevant chunks and returns cited answers.
                Fine-tuning is better for learning response patterns from a
                prepared dataset.
              </p>
              <div className="mt-3 flex items-center gap-2 border-t border-[#E7ECF2] pt-3 text-xs font-black text-[#0F766E]">
                <span>2 cited sources</span>
                <span className="h-1 w-1 rounded-full bg-[#0F766E]" />
                <span>RAGAS ready</span>
              </div>
            </div>

            <div className="rounded-2xl border border-[#D8E0EA] bg-white p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-bold text-[#6B7280]">
                  Ask a question
                </span>
                <span className="text-xs font-black text-[#0F766E]">
                  Indexed
                </span>
              </div>
              <div className="flex items-center gap-2">
                <div className="h-10 flex-1 rounded-full bg-[#EEF3F8]" />
                <button
                  className="h-10 rounded-full bg-[#0F766E] px-5 text-xs font-black text-white"
                  type="button"
                >
                  Send
                </button>
              </div>
            </div>
          </div>

          <aside className="rounded-[1.25rem] border border-[#E7ECF2] bg-white p-3">
            <p className="mb-3 text-[10px] font-black uppercase tracking-[0.2em] text-[#6B7280]">
              Citations
            </p>
            <div className="space-y-3">
              {citations.map((citation) => (
                <article
                  className="rounded-2xl border border-[#D8E0EA] bg-[#F6F8FB] p-3 transition duration-700 ease-[cubic-bezier(0.32,0.72,0,1)] hover:-translate-y-1 hover:border-[#0F766E]"
                  key={citation.source}
                >
                  <h3 className="text-xs font-black text-[#111827]">
                    {citation.source}
                  </h3>
                  <p className="mt-1 text-[11px] font-bold text-[#0F766E]">
                    {citation.meta}
                  </p>
                  <p className="mt-2 text-xs leading-5 text-[#4B5563]">
                    {citation.text}
                  </p>
                </article>
              ))}
            </div>
          </aside>
        </div>
      </div>
    </div>
  )
}

export default ProductMockup

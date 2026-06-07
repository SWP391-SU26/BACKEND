import { Link } from 'react-router-dom'
import Button from '../components/common/Button.jsx'

function AdminDashboardPage() {
  return (
    <main className="min-h-screen bg-secondary px-8 py-8 font-body text-foreground">
      <div className="mx-auto max-w-7xl">
        <header className="flex items-center justify-between rounded-[2rem] border border-border bg-background px-6 py-4 shadow-[0_18px_60px_rgba(15,23,42,0.06)]">
          <Link className="inline-flex items-center" to="/">
            <img
              alt="FStu"
              className="h-10 w-auto object-contain"
              src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
            />
          </Link>
          <Button as={Link} className="rounded-full" to="/" variant="secondary">
            Back to landing
          </Button>
        </header>

        <section className="mt-8 rounded-[2rem] border border-border bg-background p-10 shadow-[0_24px_90px_rgba(15,23,42,0.07)]">
          <p className="text-sm font-bold text-primary">Admin route</p>
          <h1 className="mt-3 text-4xl font-extrabold tracking-tight">
            Admin dashboard placeholder.
          </h1>
          <p className="mt-4 max-w-2xl text-base leading-7 text-muted-foreground">
            Admin users land here after login. This page can later manage
            documents, users, system settings, and evaluation results.
          </p>
        </section>
      </div>
    </main>
  )
}

export default AdminDashboardPage

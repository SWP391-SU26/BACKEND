import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthAlert from '../components/auth/AuthAlert.jsx'
import AuthInput from '../components/auth/AuthInput.jsx'
import AuthShell from '../components/auth/AuthShell.jsx'
import Button from '../components/common/Button.jsx'
import { login, saveSession } from '../services/authService.js'

const highlights = [
  'Course-grounded answers',
  'RAG and fine-tuning modes',
  'Source citations for every claim',
]

const initialForm = {
  email: '',
  password: '',
}

function LoginPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  function handleChange(event) {
    const { name, value } = event.target
    setForm((currentForm) => ({ ...currentForm, [name]: value }))
    setErrors((currentErrors) => ({ ...currentErrors, [name]: '' }))
    setFormError('')
  }

  function validateForm() {
    const nextErrors = {}
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.'
    } else if (!emailPattern.test(form.email.trim())) {
      nextErrors.email = 'Enter a valid email address.'
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.'
    }

    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()

    if (!validateForm()) {
      return
    }

    setIsLoading(true)
    setFormError('')

    try {
      const session = await login(form)
      saveSession(session)
      navigate(session.user.role === 'admin' ? '/admin' : '/app')
    } catch (error) {
      setFormError(error.message)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      asideEyebrow="Learning workspace"
      asideText="Access document upload, contextual chat, RAG benchmarks, and source-grounded answers from one focused workspace."
      asideTitle="Continue your cited study session."
      highlights={highlights}
    >
      <div className="animate-auth-field animation-delay-225">
        <p className="text-sm font-extrabold text-primary">Welcome back</p>
        <h2 className="mt-2 text-4xl font-extrabold tracking-tight">
          Log in to FStu
        </h2>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          Continue to your document QA workspace.
        </p>
      </div>

      <form className="mt-9 space-y-5" onSubmit={handleSubmit}>
        {formError ? <AuthAlert>{formError}</AuthAlert> : null}

        <div className="animate-auth-field animation-delay-300">
          <AuthInput
            error={errors.email}
            label="Email"
            name="email"
            onChange={handleChange}
            placeholder="user@fpt.edu.vn"
            type="email"
            value={form.email}
          />
        </div>

        <div className="animate-auth-field animation-delay-375">
          <AuthInput
            error={errors.password}
            label="Password"
            name="password"
            onChange={handleChange}
            placeholder="Enter your password"
            type="password"
            value={form.password}
          />
        </div>

        <div className="animate-auth-field animation-delay-450 flex items-center justify-between text-sm">
          <label className="flex items-center gap-2 font-medium text-muted-foreground">
            <input
              className="size-4 rounded border-border accent-primary"
              type="checkbox"
            />
            Remember me
          </label>
          <a className="font-bold text-primary" href="/login">
            Forgot password?
          </a>
        </div>

        <div className="animate-auth-field animation-delay-450">
          <Button
            className="h-13 w-full rounded-full text-base"
            disabled={isLoading}
            type="submit"
            variant="cta"
          >
            {isLoading ? 'Logging in...' : 'Log in'}
          </Button>
        </div>
      </form>

      <p className="animate-auth-field animation-delay-450 mt-8 text-center text-sm text-muted-foreground">
        New to FStu?{' '}
        <Link className="font-extrabold text-primary" to="/register">
          Create an account
        </Link>
      </p>
    </AuthShell>
  )
}

export default LoginPage

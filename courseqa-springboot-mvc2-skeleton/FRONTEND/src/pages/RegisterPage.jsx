import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthAlert from '../components/auth/AuthAlert.jsx'
import AuthInput from '../components/auth/AuthInput.jsx'
import AuthShell from '../components/auth/AuthShell.jsx'
import Button from '../components/common/Button.jsx'
import { register, saveSession } from '../services/authService.js'

const benefits = [
  'Upload and index course files',
  'Ask with RAG or fine-tuning mode',
  'Review citations and RAGAS metrics',
]

const initialForm = {
  fullName: '',
  email: '',
  userCode: '',
  password: '',
  confirmPassword: '',
}

function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  function handleChange(event) {
    const { name, value } = event.target
    setForm((currentForm) => ({ ...currentForm, [name]: value }))
    setErrors((currentErrors) => ({ ...currentErrors, [name]: '' }))
    setFormError('')
    setSuccessMessage('')
  }

  function validateForm() {
    const nextErrors = {}
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    const hasUppercase = /[A-Z]/.test(form.password)
    const hasLowercase = /[a-z]/.test(form.password)
    const hasNumber = /\d/.test(form.password)

    if (!form.fullName.trim()) {
      nextErrors.fullName = 'Full name is required.'
    }

    if (!form.email.trim()) {
      nextErrors.email = 'Email is required.'
    } else if (!emailPattern.test(form.email.trim())) {
      nextErrors.email = 'Enter a valid email address.'
    }

    if (!form.password) {
      nextErrors.password = 'Password is required.'
    } else if (
      form.password.length < 8 ||
      !hasUppercase ||
      !hasLowercase ||
      !hasNumber
    ) {
      nextErrors.password =
        'Use at least 8 characters with uppercase, lowercase, and a number.'
    }

    if (!form.confirmPassword) {
      nextErrors.confirmPassword = 'Confirm your password.'
    } else if (form.confirmPassword !== form.password) {
      nextErrors.confirmPassword = 'Passwords do not match.'
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
    setSuccessMessage('')

    try {
      const session = await register(form)
      saveSession(session)
      setSuccessMessage('Account created successfully. Redirecting...')
      window.setTimeout(() => {
        navigate(session.user.role === 'admin' ? '/admin' : '/app')
      }, 500)
    } catch (error) {
      setFormError(error.message)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      asideAlign="right"
      asideEyebrow="FStu onboarding"
      asideText="Create a backend account, then continue into your study workspace."
      asideTitle="Build a study workspace around your course materials."
      highlights={benefits}
    >
      <div className="animate-auth-field animation-delay-225">
        <p className="text-sm font-extrabold text-primary">Start learning</p>
        <h1 className="mt-2 text-4xl font-extrabold tracking-tight">
          Create your FStu account
        </h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          Set up access to document upload, RAG chat, and research tools.
        </p>
      </div>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        {formError ? <AuthAlert>{formError}</AuthAlert> : null}
        {successMessage ? (
          <AuthAlert tone="success">{successMessage}</AuthAlert>
        ) : null}

        <div className="animate-auth-field animation-delay-300">
          <AuthInput
            error={errors.fullName}
            label="Full name"
            name="fullName"
            onChange={handleChange}
            placeholder="Your name"
            value={form.fullName}
          />
        </div>

        <div className="animate-auth-field animation-delay-375">
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

        <div className="animate-auth-field animation-delay-450">
          <AuthInput
            label="User code"
            name="userCode"
            onChange={handleChange}
            placeholder="USR001"
            value={form.userCode}
          />
        </div>

        <div className="animate-auth-field animation-delay-450">
          <AuthInput
            error={errors.password}
            label="Password"
            name="password"
            onChange={handleChange}
            placeholder="Create a password"
            type="password"
            value={form.password}
          />
        </div>

        <div className="animate-auth-field animation-delay-450">
          <AuthInput
            error={errors.confirmPassword}
            label="Confirm password"
            name="confirmPassword"
            onChange={handleChange}
            placeholder="Confirm your password"
            type="password"
            value={form.confirmPassword}
          />
        </div>

        <div className="animate-auth-field animation-delay-450">
          <Button
            className="h-13 w-full rounded-full text-base"
            disabled={isLoading}
            type="submit"
            variant="cta"
          >
            {isLoading ? 'Creating account...' : 'Create account'}
          </Button>
        </div>
      </form>

      <p className="animate-auth-field animation-delay-450 mt-8 text-center text-sm text-muted-foreground">
        Already have an account?{' '}
        <Link className="font-extrabold text-primary" to="/login">
          Log in
        </Link>
      </p>
    </AuthShell>
  )
}

export default RegisterPage

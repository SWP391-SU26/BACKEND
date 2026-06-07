const styles = {
  error: 'border-red-200 bg-red-50 text-red-700',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
}

function AuthAlert({ children, tone = 'error' }) {
  return (
    <div
      className={`${styles[tone]} rounded-2xl border px-4 py-3 text-sm font-semibold leading-6`}
    >
      {children}
    </div>
  )
}

export default AuthAlert

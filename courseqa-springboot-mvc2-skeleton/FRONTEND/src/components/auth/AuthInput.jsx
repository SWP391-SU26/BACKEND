function AuthInput({
  error,
  label,
  name,
  onChange,
  placeholder,
  type = 'text',
  value,
}) {
  return (
    <label className="block">
      <span className="text-sm font-bold text-foreground">{label}</span>
      <input
        aria-invalid={Boolean(error)}
        className={[
          'mt-2 h-13 w-full rounded-2xl border bg-secondary px-4 text-sm font-medium outline-none transition duration-300',
          'placeholder:text-muted-foreground/65 focus:border-primary focus:bg-background focus:shadow-[0_0_0_4px_hsl(var(--primary)/0.10)]',
          error ? 'border-red-300 bg-red-50/40' : 'border-border',
        ].join(' ')}
        name={name}
        onChange={onChange}
        placeholder={placeholder}
        type={type}
        value={value}
      />
      {error ? (
        <p className="mt-2 text-sm font-semibold text-red-600">{error}</p>
      ) : null}
    </label>
  )
}

export default AuthInput

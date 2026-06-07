import { cn } from '../../utils/cn'

const variants = {
  cta:
    'bg-primary text-primary-foreground shadow-md shadow-primary/20 hover:bg-primary/85',
  secondary:
    'border border-border bg-background text-foreground hover:bg-secondary',
  ghost: 'text-muted-foreground hover:bg-secondary hover:text-foreground',
}

const sizes = {
  sm: 'h-10 px-4 text-sm',
  md: 'h-11 px-5 text-sm',
  lg: 'h-14 px-10 text-base',
}

function Button({
  as: Component = 'button',
  children,
  className,
  size = 'md',
  type = 'button',
  variant = 'secondary',
  ...props
}) {
  return (
    <Component
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] active:scale-[0.98]',
        'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60',
        variants[variant],
        sizes[size],
        className,
      )}
      type={Component === 'button' ? type : undefined}
      {...props}
    >
      {children}
    </Component>
  )
}

export default Button

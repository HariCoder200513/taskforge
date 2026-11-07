// Small presentational building blocks shared across pages.

export function Button({ className = '', variant = 'primary', ...props }) {
  const variants = {
    primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50',
    ghost: 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 disabled:opacity-50',
    danger: 'bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50',
  }
  return (
    <button
      className={`inline-flex items-center justify-center rounded-md px-3.5 py-2 text-sm font-medium transition ${variants[variant]} ${className}`}
      {...props}
    />
  )
}

export function Input({ label, className = '', ...props }) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>}
      <input
        className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 ${className}`}
        {...props}
      />
    </label>
  )
}

export function Textarea({ label, className = '', ...props }) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>}
      <textarea
        className={`w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 ${className}`}
        {...props}
      />
    </label>
  )
}

export function Select({ label, children, className = '', ...props }) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>}
      <select
        className={`w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 ${className}`}
        {...props}
      >
        {children}
      </select>
    </label>
  )
}

export function Card({ title, actions, children, className = '' }) {
  return (
    <section className={`rounded-xl border border-slate-200 bg-white shadow-sm ${className}`}>
      {(title || actions) && (
        <header className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
          {actions}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

const STATUS_STYLES = {
  PENDING: 'bg-amber-100 text-amber-800',
  RUNNING: 'bg-blue-100 text-blue-800',
  SUCCEEDED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-rose-100 text-rose-800',
  DEAD: 'bg-slate-800 text-slate-100',
}

export function StatusBadge({ status }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[status] || 'bg-slate-100 text-slate-700'}`}>
      {status}
    </span>
  )
}

export function Banner({ kind = 'error', children }) {
  if (!children) return null
  const styles = {
    error: 'bg-rose-50 text-rose-700 ring-rose-200',
    success: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    info: 'bg-slate-50 text-slate-600 ring-slate-200',
  }
  return <div className={`rounded-md px-3 py-2 text-sm ring-1 ${styles[kind]}`}>{children}</div>
}

export function Empty({ children }) {
  return <p className="py-6 text-center text-sm text-slate-400">{children}</p>
}

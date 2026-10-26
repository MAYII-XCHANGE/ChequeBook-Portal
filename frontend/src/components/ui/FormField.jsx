export function FormField({ label, error, hint, required, children, htmlFor }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-semibold text-slate-700">
        {label}{required && <span className="ml-1 text-red-600" aria-hidden="true">*</span>}
      </label>
      {children}
      {error ? <p className="mt-1.5 text-sm text-red-600" role="alert">{error}</p> : hint ? <p className="mt-1.5 text-xs text-slate-500">{hint}</p> : null}
    </div>
  );
}

export const inputClass = 'min-h-11 w-full rounded-md border border-slate-300 bg-white px-3.5 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-brand-600 focus:ring-4 focus:ring-brand-100 disabled:bg-slate-100 disabled:text-slate-500';

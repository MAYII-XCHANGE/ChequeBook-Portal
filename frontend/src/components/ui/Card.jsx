export function Card({ children, className = '' }) {
  return <section className={`rounded-lg border border-slate-200 bg-white shadow-panel ${className}`}>{children}</section>;
}

export function CardHeader({ title, description, action }) {
  return (
    <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div><h2 className="font-semibold text-ink">{title}</h2>{description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}</div>
      {action}
    </div>
  );
}

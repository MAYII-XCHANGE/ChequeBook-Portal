export function PageHeader({ title, description, eyebrow, action }) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>{eyebrow && <p className="mb-1 text-xs font-bold uppercase tracking-[.14em] text-brand-700">{eyebrow}</p>}<h1 className="text-2xl font-bold tracking-tight text-ink sm:text-[1.75rem]">{title}</h1>{description && <p className="mt-1.5 max-w-2xl text-sm leading-6 text-slate-500">{description}</p>}</div>
      {action}
    </div>
  );
}

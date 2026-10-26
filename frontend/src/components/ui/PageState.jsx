import { Inbox, LoaderCircle, TriangleAlert } from 'lucide-react';
import { Button } from './Button';

export function LoadingState({ label = 'Loading…' }) {
  return <div className="flex min-h-48 items-center justify-center gap-3 text-sm text-slate-500"><LoaderCircle className="h-5 w-5 animate-spin text-brand-600" /><span>{label}</span></div>;
}

export function EmptyState({ title = 'No results found', description = 'Try adjusting your search or filters.', action }) {
  return <div className="flex min-h-52 flex-col items-center justify-center p-8 text-center"><span className="mb-4 grid h-12 w-12 place-items-center rounded-full bg-slate-100"><Inbox className="h-6 w-6 text-slate-500" /></span><h3 className="font-semibold text-ink">{title}</h3><p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>{action && <div className="mt-5">{action}</div>}</div>;
}

export function ErrorState({ message, onRetry }) {
  return <div className="flex min-h-52 flex-col items-center justify-center p-8 text-center"><TriangleAlert className="h-8 w-8 text-red-500" /><h3 className="mt-3 font-semibold text-ink">Unable to load this information</h3><p className="mt-1 text-sm text-slate-500">{message}</p>{onRetry && <Button variant="secondary" className="mt-5" onClick={onRetry}>Try again</Button>}</div>;
}

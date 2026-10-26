import { FileClock, Search, ShieldCheck } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Card } from '../../components/ui/Card';
import { inputClass } from '../../components/ui/FormField';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/PageState';
import { useAsync } from '../../hooks/useAsync';
import { auditApi } from '../../services/api/auditApi';
import { formatDate } from '../../utils/formatters';

export function AuditPage() {
  const { data, loading, error, retry } = useAsync(() => auditApi.getAuditEvents(), []); const [search, setSearch] = useState('');
  const events = useMemo(() => (data?.data || []).filter((event) => Object.values(event).some((value) => String(value).toLowerCase().includes(search.toLowerCase()))), [data, search]);
  return <><PageHeader eyebrow="Oversight" title="Audit trail" description="Read-only history of user and system activity supplied by the authoritative audit service." action={<span className="inline-flex items-center gap-2 rounded-md bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700"><ShieldCheck className="h-4 w-4" />Read only</span>} /><Card><div className="border-b border-slate-200 p-4"><div className="relative max-w-xl"><Search className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" /><input className={`${inputClass} pl-10`} value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search user, action or request ID" aria-label="Search audit events" /></div></div>{loading ? <LoadingState label="Loading audit events…" /> : error ? <ErrorState message={error.message} onRetry={retry} /> : !events.length ? <EmptyState title="No audit events found" /> : <ol className="divide-y divide-slate-100">{events.map((event) => <li key={event.id} className="grid gap-4 p-5 sm:grid-cols-[42px_180px_1fr]"><span className="grid h-10 w-10 place-items-center rounded-md bg-slate-100 text-slate-500"><FileClock className="h-5 w-5" /></span><div><p className="text-sm font-semibold text-ink">{formatDate(event.at, true)}</p><p className="mt-1 text-xs text-slate-500">{event.user} · {event.role}</p></div><div><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-semibold text-ink">{event.action}</p><span className="rounded bg-brand-50 px-2 py-1 font-mono text-[11px] font-semibold text-brand-700">{event.requestId}</span></div><p className="mt-1 text-sm leading-6 text-slate-500">{event.detail}</p></div></li>)}</ol>}</Card></>;
}

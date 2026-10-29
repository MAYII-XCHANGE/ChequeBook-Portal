import { CheckCircle2, Download, FileSpreadsheet, Search, Timer, Truck, XCircle } from 'lucide-react';
import { useState } from 'react';
import toast from 'react-hot-toast';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { RequestTable } from '../../components/ui/DataTable';
import { inputClass } from '../../components/ui/FormField';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/PageState';
import { useAuth } from '../../app/providers/AuthProvider';
import { getRequestScope } from '../../constants/access';
import { useAsync } from '../../hooks/useAsync';
import { reportApi } from '../../services/api/reportApi';

const metrics = [
  ['Total Requests', 'total', FileSpreadsheet, 'bg-blue-50 text-blue-700'], ['Approved', 'approved', CheckCircle2, 'bg-emerald-50 text-emerald-700'], ['Rejected', 'rejected', XCircle, 'bg-red-50 text-red-700'], ['Processing', 'processing', Timer, 'bg-violet-50 text-violet-700'], ['Completed', 'delivered', Truck, 'bg-teal-50 text-teal-700'],
];

export function ReportsPage() {
  const { user } = useAuth();
  const [branch, setBranch] = useState(''); const [from, setFrom] = useState('2026-08-01'); const [to, setTo] = useState('2026-12-31'); const [filters, setFilters] = useState({ branch: '', from: '2026-08-01', to: '2026-12-31' }); const [exporting, setExporting] = useState(false);
  const scope = getRequestScope(user); const applied = { ...filters, ...scope };
  const { data, loading, error, retry } = useAsync(() => reportApi.getRequestReport(applied), [user.role, user.branchCode, filters.branch, filters.from, filters.to]);
  const rows = data?.data || [];
  const branches = [...new Map(rows.filter((item) => item.branchCode).map((item) => [item.branchCode, item.branch])).entries()];
  const exportReport = async () => { setExporting(true); try { const filename = await reportApi.downloadRequestReport(applied); toast.success(`${filename} downloaded.`); } catch (e) { toast.error(e.message || 'The report could not be exported.'); } finally { setExporting(false); } };
  return <><PageHeader eyebrow="Management information" title="Request reports" description="Monitor request volumes and operational outcomes across your authorized branch scope." action={<Button variant="secondary" loading={exporting} disabled={loading || Boolean(error) || rows.length === 0} onClick={exportReport}><Download className="h-4 w-4" />Export CSV</Button>} />
    {loading ? <Card><LoadingState label="Generating report view…" /></Card> : error ? <Card><ErrorState message={error.message} onRetry={retry} /></Card> : <><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">{metrics.map(([label, key, Icon, tone]) => <Card key={key} className="p-5"><div className="flex items-center justify-between"><div><p className="text-sm font-medium text-slate-500">{label}</p><p className="mt-2 text-2xl font-bold text-ink">{data.summary[key]}</p></div><span className={`grid h-10 w-10 place-items-center rounded-md ${tone}`}><Icon className="h-5 w-5" /></span></div></Card>)}</div>
      <Card className="mt-6"><CardHeader title="Detailed report" description="Filters apply to the records below" /><div className="grid gap-3 border-b border-slate-200 p-4 sm:grid-cols-3 lg:grid-cols-[1fr_1fr_1.4fr_auto]"><label className="text-xs font-semibold text-slate-500">From<input type="date" className={`${inputClass} mt-1`} value={from} onChange={(e) => setFrom(e.target.value)} /></label><label className="text-xs font-semibold text-slate-500">To<input type="date" className={`${inputClass} mt-1`} value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label><label className="text-xs font-semibold text-slate-500">Branch<select className={`${inputClass} mt-1`} value={scope.branch || branch} disabled={Boolean(scope.branch)} onChange={(e) => setBranch(e.target.value)}><option value="">All authorized branches</option>{scope.branch && <option value={scope.branch}>{user.branch}</option>}{!scope.branch && branches.map(([code, name]) => <option key={code} value={code}>{name} - {code}</option>)}</select></label><Button variant="secondary" className="self-end" disabled={!from || !to || from > to} onClick={() => setFilters({ branch, from, to })}><Search className="h-4 w-4" />Apply</Button></div>{rows.length ? <RequestTable rows={rows} /> : <EmptyState title="No report records found" description="Adjust the date range or branch filter to view matching records." />}</Card>
    </>}
  </>;
}

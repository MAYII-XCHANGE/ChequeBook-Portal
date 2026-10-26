import { STATUS_CONFIG } from '../../constants/status';

const tones = {
  blue: 'bg-blue-50 text-blue-700 ring-blue-600/15', amber: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15', red: 'bg-red-50 text-red-700 ring-red-600/15',
  violet: 'bg-violet-50 text-violet-700 ring-violet-600/15', cyan: 'bg-cyan-50 text-cyan-700 ring-cyan-600/15',
  indigo: 'bg-indigo-50 text-indigo-700 ring-indigo-600/15', orange: 'bg-orange-50 text-orange-700 ring-orange-600/15',
  teal: 'bg-teal-50 text-teal-700 ring-teal-600/15', slate: 'bg-slate-100 text-slate-700 ring-slate-600/10',
};

export function StatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || { label: status || 'Unknown', tone: 'slate' };
  return <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${tones[config.tone]}`}>{config.label}</span>;
}

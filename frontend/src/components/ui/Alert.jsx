import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';

const variants = {
  success: { icon: CheckCircle2, box: 'border-emerald-200 bg-emerald-50 text-emerald-900', iconStyle: 'text-emerald-600' },
  error: { icon: AlertCircle, box: 'border-red-200 bg-red-50 text-red-900', iconStyle: 'text-red-600' },
  warning: { icon: TriangleAlert, box: 'border-amber-200 bg-amber-50 text-amber-950', iconStyle: 'text-amber-600' },
  info: { icon: Info, box: 'border-blue-200 bg-blue-50 text-blue-950', iconStyle: 'text-blue-600' },
};

export function Alert({ variant = 'info', title, children }) {
  const item = variants[variant];
  const Icon = item.icon;
  return (
    <div className={`flex gap-3 rounded-md border p-4 text-sm ${item.box}`} role={variant === 'error' ? 'alert' : 'status'}>
      <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${item.iconStyle}`} aria-hidden="true" />
      <div>{title && <p className="font-semibold">{title}</p>}<div className={title ? 'mt-1 leading-6' : 'leading-6'}>{children}</div></div>
    </div>
  );
}

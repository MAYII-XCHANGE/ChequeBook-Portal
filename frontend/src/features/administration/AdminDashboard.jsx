import {
  Activity,
  Building2,
  CheckCircle2,
  MapPinned,
  UserCog,
  UsersRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardHeader } from "../../components/ui/Card";
import { PageHeader } from "../../components/ui/PageHeader";
import { ErrorState, LoadingState } from "../../components/ui/PageState";
import { useAsync } from "../../hooks/useAsync";
import { adminApi } from "../../services/api/adminApi";

const metricStyles = [
  { icon: UsersRound, tone: "bg-blue-50 text-blue-700" },
  { icon: Building2, tone: "bg-violet-50 text-violet-700" },
  { icon: CheckCircle2, tone: "bg-emerald-50 text-emerald-700" },
  { icon: MapPinned, tone: "bg-cyan-50 text-cyan-700" },
];

export function AdminDashboard({ user, greeting }) {
  const { data, loading, error, retry } = useAsync(async () => {
    const [users, branches] = await Promise.all([
      adminApi.users(),
      adminApi.branches(),
    ]);
    return { users, branches };
  }, []);

  const users = data?.users || [];
  const branches = data?.branches || [];
  const metrics = [
    { label: "Total Users", value: users.length, detail: "Portal profiles" },
    { label: "Total Branches", value: branches.length, detail: "Configured locations" },
    { label: "Active Users", value: users.filter((item) => item.active).length, detail: "Enabled for access" },
    { label: "Active Branches", value: branches.filter((item) => item.active).length, detail: "Available for assignment" },
  ];

  return (
    <>
      <PageHeader
        title={`${greeting}, ${user.name.split(" ")[0]}`}
        description="Overview of portal access and branch availability."
      />

      {loading ? (
        <Card><LoadingState label="Preparing the administration dashboard…" /></Card>
      ) : error ? (
        <Card><ErrorState message={error.message} onRetry={retry} /></Card>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Administration statistics">
            {metrics.map((metric, index) => {
              const { icon: Icon, tone } = metricStyles[index];
              return (
                <Card key={metric.label} className="p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-sm font-medium text-slate-500">{metric.label}</p>
                      <p className="mt-3 text-3xl font-bold tracking-tight text-ink">{metric.value}</p>
                      <p className="mt-1 text-xs text-slate-400">{metric.detail}</p>
                    </div>
                    <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-md ${tone}`}>
                      <Icon className="h-5 w-5" />
                    </span>
                  </div>
                </Card>
              );
            })}
          </section>

          <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(300px,1fr)]">
            <Card>
              <CardHeader
                title="Administrative growth"
                description="New users and branches created during the last six months"
              />
              <div className="p-5">
                <TrendChart users={users} branches={branches} />
              </div>
            </Card>

            <Card>
              <CardHeader
                title="Access status"
                description="Current enabled and disabled records"
              />
              <div className="grid gap-6 p-5 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                <StatusDonut
                  label="Users"
                  active={users.filter((item) => item.active).length}
                  total={users.length}
                />
                <StatusDonut
                  label="Branches"
                  active={branches.filter((item) => item.active).length}
                  total={branches.length}
                />
              </div>
            </Card>
          </section>

          <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(300px,.65fr)]">
            <Card>
              <CardHeader
                title="Users by role"
                description="Distribution of assigned portal responsibilities"
              />
              <div className="p-5">
                <RoleDistribution users={users} />
              </div>
            </Card>
          </section>
        </>
      )}
    </>
  );
}

function TrendChart({ users, branches }) {
  const points = monthlyTrend(users, branches);
  const maximum = Math.max(1, ...points.flatMap((point) => [point.users, point.branches]));
  const chart = { left: 42, right: 580, top: 18, bottom: 176 };
  const xFor = (index) => chart.left + (index * (chart.right - chart.left)) / Math.max(1, points.length - 1);
  const yFor = (value) => chart.bottom - (value / maximum) * (chart.bottom - chart.top);
  const line = (key) => points.map((point, index) => `${index ? "L" : "M"} ${xFor(index)} ${yFor(point[key])}`).join(" ");

  return (
    <div>
      <div className="mb-4 flex flex-wrap gap-5 text-xs font-semibold text-slate-600">
        <span className="inline-flex items-center gap-2"><i className="h-2.5 w-2.5 rounded-full bg-brand-600" />Users</span>
        <span className="inline-flex items-center gap-2"><i className="h-2.5 w-2.5 rounded-full bg-violet-500" />Branches</span>
      </div>
      <svg
        viewBox="0 0 620 220"
        className="h-auto min-h-[220px] w-full"
        role="img"
        aria-label="Users and branches created over the last six months"
      >
        {[0, 0.5, 1].map((ratio) => {
          const y = chart.bottom - ratio * (chart.bottom - chart.top);
          return (
            <g key={ratio}>
              <line x1={chart.left} x2={chart.right} y1={y} y2={y} stroke="#e2e8f0" strokeDasharray="4 5" />
              <text x="32" y={y + 4} textAnchor="end" fontSize="11" fill="#94a3b8">{Math.round(maximum * ratio)}</text>
            </g>
          );
        })}
        <path d={line("users")} fill="none" stroke="#008AC6" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        <path d={line("branches")} fill="none" stroke="#8b5cf6" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point, index) => (
          <g key={point.key}>
            <circle cx={xFor(index)} cy={yFor(point.users)} r="4" fill="#008AC6"><title>{`${point.label}: ${point.users} users`}</title></circle>
            <circle cx={xFor(index)} cy={yFor(point.branches)} r="4" fill="#8b5cf6"><title>{`${point.label}: ${point.branches} branches`}</title></circle>
            <text x={xFor(index)} y="205" textAnchor="middle" fontSize="11" fill="#64748b">{point.label}</text>
          </g>
        ))}
      </svg>
    </div>
  );
}

function StatusDonut({ label, active, total }) {
  const percentage = total ? Math.round((active / total) * 100) : 0;
  return (
    <div className="flex items-center gap-4">
      <div
        className="relative grid h-24 w-24 shrink-0 place-items-center rounded-full"
        style={{ background: `conic-gradient(#059669 ${percentage}%, #e2e8f0 ${percentage}% 100%)` }}
        role="img"
        aria-label={`${percentage}% of ${label.toLowerCase()} are active`}
      >
        <span className="absolute inset-3 rounded-full bg-white" />
        <span className="relative text-lg font-bold text-ink">{percentage}%</span>
      </div>
      <div>
        <p className="font-semibold text-ink">{label}</p>
        <p className="mt-1 text-sm text-slate-500">{active} active</p>
        <p className="text-xs text-slate-400">{Math.max(0, total - active)} inactive</p>
      </div>
    </div>
  );
}

function RoleDistribution({ users }) {
  const counts = users.reduce((result, user) => {
    (user.roles || []).forEach((role) => { result[role] = (result[role] || 0) + 1; });
    return result;
  }, {});
  const entries = Object.entries(counts).sort((left, right) => right[1] - left[1]);
  const maximum = Math.max(1, ...entries.map(([, count]) => count));

  if (!entries.length) {
    return <p className="py-10 text-center text-sm text-slate-500">No role assignments are available yet.</p>;
  }

  return (
    <div className="space-y-4" role="img" aria-label="Number of users assigned to each role">
      {entries.map(([role, count]) => (
        <div key={role} className="grid grid-cols-[100px_minmax(0,1fr)_28px] items-center gap-3">
          <span className="truncate text-xs font-bold text-slate-600">{role}</span>
          <span className="h-2.5 overflow-hidden rounded-full bg-slate-100">
            <span className="block h-full rounded-full bg-brand-600" style={{ width: `${(count / maximum) * 100}%` }} />
          </span>
          <span className="text-right text-xs font-bold text-ink">{count}</span>
        </div>
      ))}
    </div>
  );
}

function DashboardLink({ to, icon: Icon, title, description }) {
  return (
    <Link to={to} className="group flex items-center gap-3 rounded-md border border-slate-200 p-3.5 hover:border-brand-100 hover:bg-brand-50">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded bg-slate-100 text-slate-600 group-hover:bg-brand-600 group-hover:text-white">
        <Icon className="h-5 w-5" />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-ink">{title}</span>
        <span className="block text-xs text-slate-500">{description}</span>
      </span>
      <Activity className="ml-auto h-4 w-4 shrink-0 text-slate-300 group-hover:text-brand-600" />
    </Link>
  );
}

function monthlyTrend(users, branches) {
  const formatter = new Intl.DateTimeFormat("en", { month: "short" });
  const now = new Date();
  const months = Array.from({ length: 6 }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() - (5 - index), 1);
    return {
      key: `${date.getFullYear()}-${date.getMonth()}`,
      label: formatter.format(date),
      users: 0,
      branches: 0,
    };
  });
  const lookup = new Map(months.map((month) => [month.key, month]));
  const record = (items, field) => items.forEach((item) => {
    if (!item.createdAt) return;
    const date = new Date(item.createdAt);
    if (Number.isNaN(date.getTime())) return;
    const month = lookup.get(`${date.getFullYear()}-${date.getMonth()}`);
    if (month) month[field] += 1;
  });
  record(users, "users");
  record(branches, "branches");
  return months;
}

import { Building2, KeyRound, ShieldCheck, UserRound } from "lucide-react";
import { useAuth } from "../../app/providers/AuthProvider";
import { Card, CardHeader } from "../../components/ui/Card";
import { PageHeader } from "../../components/ui/PageHeader";
import { ROLE_LABELS, ROLE_PERMISSIONS } from "../../constants/access";

export function ProfilePage() {
  const { user } = useAuth();
  const facts = [
    ["Full name", user.name, UserRound],
    ["User ID", user.id, KeyRound],
    ["Branch / office", user.branch, Building2],
    ["Assigned role", ROLE_LABELS[user.role], ShieldCheck],
  ];
  const permissions = user.permissions || ROLE_PERMISSIONS[user.role] || [];
  return (
    <>
      <PageHeader
        eyebrow="Account"
        title="Your profile"
        description="Identity and access details for the current portal session."
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Card>
          <CardHeader title="Profile information" />
          <div className="grid gap-5 p-5 sm:grid-cols-2">
            {facts.map(([label, value, Icon]) => (
              <div
                key={label}
                className="flex gap-3 rounded-md border border-slate-100 bg-slate-50 p-4"
              >
                <Icon className="h-5 w-5 text-brand-600" />
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {label}
                  </p>
                  <p className="mt-1 text-sm font-semibold text-ink">{value}</p>
                </div>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <CardHeader
            title="Effective permissions"
            description="Enforced by the backend"
          />
          <ul className="space-y-3 p-5">
            {[...permissions].map((permission) => (
              <li
                key={permission}
                className="flex items-center gap-2 text-sm text-slate-700"
              >
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                {permission.toLowerCase().replaceAll("_", " ")}
              </li>
            ))}
          </ul>
          <p className="border-t border-slate-100 p-5 text-xs leading-5 text-slate-500">
            Branch scope and maker-checker controls are evaluated for every
            backend operation.
          </p>
        </Card>
      </div>
    </>
  );
}

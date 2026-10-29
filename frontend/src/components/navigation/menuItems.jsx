import {
  BarChart3,
  Building2,
  CheckSquare2,
  ClipboardList,
  FileClock,
  Gauge,
  PlusCircle,
  Settings2,
  //ShieldCheck,
  UserCog,
} from "lucide-react";
import { hasPermission, PERMISSIONS } from "../../constants/access";

const menuItems = [
  { label: "Dashboard", to: "/dashboard", icon: Gauge },
  {
    label: "New request",
    to: "/cheque-requests/new",
    icon: PlusCircle,
    permission: PERMISSIONS.CREATE_CHEQUE_REQUEST,
  },
  {
    label: "Cheque requests",
    to: "/cheque-requests",
    icon: ClipboardList,
    permission: PERMISSIONS.VIEW_CHEQUE_REQUEST,
  },
  {
    label: "Pending Approvals",
    to: "/approvals",
    icon: CheckSquare2,
    permission: PERMISSIONS.APPROVE_CHEQUE_REQUEST,
  },
  {
    label: "Processing",
    to: "/processing",
    icon: Settings2,
    permission: PERMISSIONS.PROCESS_CHEQUE_REQUEST,
  },
  {
    label: "Vendor exports",
    to: "/vendor-exports",
    icon: ClipboardList,
    permission: PERMISSIONS.EXPORT_VENDOR,
  },
  {
    label: "Reports",
    to: "/reports",
    icon: BarChart3,
    permission: PERMISSIONS.VIEW_REPORTS,
  },
  // {
  //   label: "Audit trail",
  //   to: "/audit-trail",
  //   icon: FileClock,
  //   permission: PERMISSIONS.VIEW_AUDIT_TRAIL,
  // },
  {
    label: "User Management",
    to: "/administration/users",
    icon: UserCog,
    permission: PERMISSIONS.USER_MANAGEMENT,
  },
  {
    label: "Branch Management",
    to: "/administration/branches",
    icon: Building2,
    permission: PERMISSIONS.BRANCH_MANAGEMENT,
  },
  // {
  //   label: "Role Management",
  //   to: "/administration/roles",
  //   icon: ShieldCheck,
  //   permission: PERMISSIONS.ROLE_MANAGEMENT,
  // },
  
];

export function getMenuItemsForUser(user) {
  return menuItems.filter(
    (item) => !item.permission || hasPermission(user, item.permission),
  );
}

import { Pencil, Power, UserPlus } from "lucide-react";
import { useState } from "react";
import toast from "react-hot-toast";
import { Button } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { FormField, inputClass } from "../../components/ui/FormField";
import { Modal } from "../../components/ui/Modal";
import { PageHeader } from "../../components/ui/PageHeader";
import { ErrorState, LoadingState } from "../../components/ui/PageState";
import { useAsync } from "../../hooks/useAsync";
import { adminApi } from "../../services/api/adminApi";
import {
  ManagementTable,
  PaginationControls,
  StatusPill,
} from "./AdministrationComponents";
import { usePagination } from "./usePagination";

const emptyUser = {
  employeeId: "",
  email: "",
  fullName: "",
  branchCode: "",
  role: "",
};

export function UserManagementPage() {
  const { data, loading, error, retry } = useAsync(async () => {
    const [users, roles, branches] = await Promise.all([
      adminApi.users(),
      adminApi.roles(),
      adminApi.branches(),
    ]);
    return { users, roles, branches };
  }, []);
  const users = data?.users || [];
  const pagination = usePagination(users);
  const [modalMode, setModalMode] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState(emptyUser);
  const [errors, setErrors] = useState({});
  const [working, setWorking] = useState(false);
  const [pendingStatusUser, setPendingStatusUser] = useState(null);

  const openCreate = () => {
    setEditingId(null);
    setDraft({ ...emptyUser, branchCode: data?.branches[0]?.code || "" });
    setErrors({});
    setModalMode("create");
  };

  const openEdit = (user) => {
    setEditingId(user.id);
    setDraft({
      employeeId: user.employeeId || "",
      email: user.email || "",
      fullName: user.fullName || "",
      branchCode: user.branchCode || "",
      role: user.roles?.[0] || "",
    });
    setErrors({});
    setModalMode("edit");
  };

  const closeModal = () => {
    if (working) return;
    setModalMode("");
    setEditingId(null);
    setDraft(emptyUser);
    setErrors({});
  };

  const updateDraft = (field, value) => {
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: "" }));
  };

  const validate = () => {
    const next = {};
    if (!draft.fullName.trim()) next.fullName = "Enter the staff member's full name.";
    if (!draft.employeeId.trim()) next.employeeId = "Enter an employee ID.";
    if (!/^\S+@\S+\.\S+$/.test(draft.email.trim())) next.email = "Enter a valid email address.";
    if (!draft.branchCode) next.branchCode = "Select a branch.";
    if (!draft.role) next.role = "Select a role.";
    return next;
  };

  const saveUser = async (event) => {
    event.preventDefault();
    const nextErrors = validate();
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      toast.error(Object.values(nextErrors)[0]);
      return;
    }

    const body = {
      employeeId: draft.employeeId.trim(),
      email: draft.email.trim().toLowerCase(),
      fullName: draft.fullName.trim(),
      branchCode: draft.branchCode,
      roles: [draft.role],
    };

    setWorking(true);
    try {
      if (editingId) await adminApi.updateUser(editingId, body);
      else await adminApi.createUser(body);
      toast.success(`User ${editingId ? "updated" : "created"} successfully.`);
      setModalMode("");
      setEditingId(null);
      setDraft(emptyUser);
      setErrors({});
      retry();
    } catch (requestError) {
      setErrors(requestError.fieldErrors || {});
      toast.error(requestError.message || "Unable to save the user.");
    } finally {
      setWorking(false);
    }
  };

  const changeStatus = async () => {
    if (!pendingStatusUser) return;
    setWorking(true);
    try {
      await adminApi.setUserActive(pendingStatusUser.id, !pendingStatusUser.active);
      toast.success(
        `User ${pendingStatusUser.active ? "deactivated" : "activated"} successfully.`,
      );
      setPendingStatusUser(null);
      retry();
    } catch (requestError) {
      toast.error(requestError.message || "Unable to change the user status.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <PageHeader
        title="User Management"
        description="Manage portal users, branch assignments, and role ownership."
      />
      <Card>
        <CardHeader
          title="Application users"
          description="Create, add, and edit user access across NOVA Bank branches."
          action={
            <Button onClick={openCreate} disabled={loading}>
              <UserPlus className="h-4 w-4" />
              Add New User
            </Button>
          }
        />
        {loading ? (
          <LoadingState label="Loading users…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : (
          <>
            <ManagementTable
              headers={["Name", "Staff ID", "Branch", "Role", "Status", "Action"]}
              minWidth="900px"
              rows={pagination.paginatedItems.map((user) => {
                const protectedAdmin = user.roles?.includes("ADMIN");
                return {
                  id: user.id,
                  cells: [
                    <div key="identity">
                      <p className="font-semibold text-ink">{user.fullName}</p>
                      <p className="mt-0.5 text-xs text-slate-500">{user.email}</p>
                    </div>,
                    user.employeeId,
                    user.branchCode || "Head Office",
                    user.roles?.join(", "),
                    <StatusPill key="status" active={user.active} />,
                    protectedAdmin ? (
                      <span key="protected" className="text-xs font-semibold text-slate-400">
                        Protected
                      </span>
                    ) : (
                      <div key="actions" className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => openEdit(user)}
                          className="rounded-md border border-slate-200 p-2 text-slate-600 hover:bg-slate-50"
                          aria-label={`Edit ${user.fullName}`}
                          title="Edit"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setPendingStatusUser(user)}
                          className={`rounded-md border p-2 ${
                            user.active
                              ? "border-red-200 text-red-600 hover:bg-red-50"
                              : "border-emerald-200 text-emerald-700 hover:bg-emerald-50"
                          }`}
                          aria-label={`${user.active ? "Deactivate" : "Activate"} ${user.fullName}`}
                          title={user.active ? "Deactivate" : "Activate"}
                        >
                          <Power className="h-4 w-4" />
                        </button>
                      </div>
                    ),
                  ],
                };
              })}
            />
            <PaginationControls {...pagination} onPageChange={pagination.setCurrentPage} />
          </>
        )}
      </Card>

      <Modal
        open={Boolean(modalMode)}
        onClose={closeModal}
        title={modalMode === "edit" ? "Edit user" : "Add new user"}
        description="Link an existing bank directory identity to portal access."
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={closeModal}>Cancel</Button>
            <Button loading={working} onClick={saveUser}>
              {editingId ? "Save changes" : "Add user"}
            </Button>
          </>
        }
      >
        <form onSubmit={saveUser} className="grid gap-4 sm:grid-cols-2">
          <UserField label="Full name" name="fullName" draft={draft} errors={errors} onChange={updateDraft} />
          <UserField label="Employee ID" name="employeeId" draft={draft} errors={errors} onChange={updateDraft} />
          <UserField label="Email" name="email" type="email" draft={draft} errors={errors} onChange={updateDraft} disabled={Boolean(editingId)} />
          <FormField label="Branch Name" htmlFor="admin-branchCode" error={errors.branchCode} required>
            <select id="admin-branchCode" className={inputClass} value={draft.branchCode} required onChange={(event) => updateDraft("branchCode", event.target.value)}>
              <option value="">Select branch</option>
              {data?.branches.filter((branch) => branch.active).map((branch) => (
                <option key={branch.id} value={branch.code}>{branch.name} - {branch.code}</option>
              ))}
            </select>
          </FormField>
          <FormField label="Role" htmlFor="admin-role" error={errors.role} required>
            <select id="admin-role" className={inputClass} value={draft.role} required onChange={(event) => updateDraft("role", event.target.value)}>
              <option value="">Select role</option>
              {data?.roles.filter((role) => role.code !== "ADMIN").map((role) => (
                <option key={role.id} value={role.code}>{role.name} - {role.code}</option>
              ))}
            </select>
          </FormField>
          <button type="submit" className="hidden" aria-hidden="true" />
        </form>
      </Modal>

      <Modal
        open={Boolean(pendingStatusUser)}
        onClose={() => !working && setPendingStatusUser(null)}
        title={`${pendingStatusUser?.active ? "Deactivate" : "Activate"} user`}
        description={`Are you sure you want to ${pendingStatusUser?.active ? "deactivate" : "activate"} ${pendingStatusUser?.fullName || "this user"}?`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPendingStatusUser(null)}>Cancel</Button>
            <Button variant={pendingStatusUser?.active ? "danger" : "primary"} loading={working} onClick={changeStatus}>
              {pendingStatusUser?.active ? "Deactivate user" : "Activate user"}
            </Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-slate-600">
          {pendingStatusUser?.active
            ? "The user will immediately lose access to the portal."
            : "The user will regain access using their bank directory credentials."}
        </p>
      </Modal>
    </>
  );
}

function UserField({ label, name, type = "text", draft, errors, onChange, disabled = false }) {
  return (
    <FormField label={label} htmlFor={`admin-${name}`} error={errors[name]} required>
      <input
        id={`admin-${name}`}
        type={type}
        className={inputClass}
        value={draft[name]}
        disabled={disabled}
        required
        onChange={(event) => onChange(name, event.target.value)}
      />
    </FormField>
  );
}

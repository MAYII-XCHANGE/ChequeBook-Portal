import { Pencil, Plus, ShieldCheck } from "lucide-react";
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
import { ManagementTable, PaginationControls } from "./AdministrationComponents";
import { usePagination } from "./usePagination";

const emptyRole = { code: "", name: "", permissions: "" };

export function RoleManagementPage() {
  const { data: roles = [], loading, error, retry } = useAsync(adminApi.roles, []);
  const pagination = usePagination(roles || []);
  const [modalMode, setModalMode] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState(emptyRole);
  const [errors, setErrors] = useState({});
  const [working, setWorking] = useState(false);

  const openCreate = () => {
    setEditingId(null);
    setDraft(emptyRole);
    setErrors({});
    setModalMode("create");
  };

  const openEdit = (role) => {
    setEditingId(role.id);
    setDraft({
      code: role.code,
      name: role.name,
      permissions: [...(role.permissions || [])].join(", "),
    });
    setErrors({});
    setModalMode("edit");
  };

  const closeModal = () => {
    if (working) return;
    setModalMode("");
    setEditingId(null);
    setDraft(emptyRole);
    setErrors({});
  };

  const saveRole = async (event) => {
    event.preventDefault();
    const nextErrors = {};
    if (!draft.code.trim()) nextErrors.code = "Enter a role code.";
    if (!draft.name.trim()) nextErrors.name = "Enter a display name.";
    if (!draft.permissions.trim()) nextErrors.permissions = "Enter at least one permission.";
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      toast.error(Object.values(nextErrors)[0]);
      return;
    }

    const body = {
      code: draft.code.trim().toUpperCase(),
      name: draft.name.trim(),
      permissions: draft.permissions
        .split(",")
        .map((permission) => permission.trim().toUpperCase())
        .filter(Boolean),
    };

    setWorking(true);
    try {
      if (editingId) await adminApi.updateRole(editingId, body);
      else await adminApi.createRole(body);
      toast.success(`Role ${editingId ? "updated" : "created"} successfully.`);
      setModalMode("");
      setEditingId(null);
      setDraft(emptyRole);
      retry();
    } catch (requestError) {
      setErrors(requestError.fieldErrors || {});
      toast.error(requestError.message || "Unable to save the role.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Role Management"
        description="Maintain portal roles and the permissions assigned to each role."
      />
      <Card>
        <CardHeader
          title="Roles and permissions"
          description="System roles are identified and the ADMIN role remains protected."
          action={
            <Button onClick={openCreate} disabled={loading}>
              <Plus className="h-4 w-4" />
              Add Role
            </Button>
          }
        />
        {loading ? (
          <LoadingState label="Loading roles…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : (
          <>
            <ManagementTable
              headers={["Code", "Name", "Permissions", "Type", "Action"]}
              minWidth="850px"
              rows={pagination.paginatedItems.map((role) => ({
                id: role.id,
                cells: [
                  <span key="code" className="font-semibold text-ink">{role.code}</span>,
                  role.name,
                  <p key="permissions" className="max-w-xl text-xs leading-5 text-slate-600">
                    {[...(role.permissions || [])].join(", ")}
                  </p>,
                  <span key="type" className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${role.systemRole ? "bg-blue-50 text-blue-700" : "bg-slate-100 text-slate-600"}`}>
                    {role.systemRole ? "System" : "Custom"}
                  </span>,
                  role.code === "ADMIN" ? (
                    <span key="protected" className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400">
                      <ShieldCheck className="h-4 w-4" />Protected
                    </span>
                  ) : (
                    <button
                      key="edit"
                      type="button"
                      onClick={() => openEdit(role)}
                      className="inline-flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                      aria-label={`Edit ${role.name}`}
                    >
                      <Pencil className="h-4 w-4" />Edit
                    </button>
                  ),
                ],
              }))}
            />
            <PaginationControls {...pagination} onPageChange={pagination.setCurrentPage} />
          </>
        )}
      </Card>

      <Modal
        open={Boolean(modalMode)}
        onClose={closeModal}
        title={modalMode === "edit" ? "Edit role" : "Add new role"}
        description="Permission codes must match permissions enforced by the backend."
        footer={
          <>
            <Button variant="secondary" onClick={closeModal}>Cancel</Button>
            <Button loading={working} onClick={saveRole}>
              {editingId ? "Save changes" : "Add role"}
            </Button>
          </>
        }
      >
        <form onSubmit={saveRole} className="space-y-4">
          <RoleField label="Role code" name="code" draft={draft} errors={errors} setDraft={setDraft} disabled={Boolean(editingId)} />
          <RoleField label="Display name" name="name" draft={draft} errors={errors} setDraft={setDraft} />
          <FormField label="Permissions" htmlFor="role-permissions" error={errors.permissions} hint="Separate permission codes with commas." required>
            <textarea
              id="role-permissions"
              rows="5"
              className={`${inputClass} py-3`}
              value={draft.permissions}
              onChange={(event) => setDraft({ ...draft, permissions: event.target.value })}
            />
          </FormField>
          <button type="submit" className="hidden" aria-hidden="true" />
        </form>
      </Modal>
    </>
  );
}

function RoleField({ label, name, draft, errors, setDraft, disabled = false }) {
  return (
    <FormField label={label} htmlFor={`role-${name}`} error={errors[name]} required>
      <input
        id={`role-${name}`}
        className={inputClass}
        value={draft[name]}
        disabled={disabled}
        onChange={(event) => setDraft({ ...draft, [name]: event.target.value })}
      />
    </FormField>
  );
}

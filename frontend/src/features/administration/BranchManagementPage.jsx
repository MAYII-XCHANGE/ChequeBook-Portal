import { Building2, Pencil, Plus, Power } from "lucide-react";
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
import { maskAccount } from "../../utils/formatters";
import {
  ManagementTable,
  PaginationControls,
  StatusPill,
} from "./AdministrationComponents";
import { usePagination } from "./usePagination";

const emptyBranch = {
  code: "",
  name: "",
  address: "",
  sortCode: "",
  chargeGlAccount: "",
  vatGlAccount: "",
  managersChequeAccount: "",
};

export function BranchManagementPage() {
  const { data: branches = [], loading, error, retry } = useAsync(adminApi.branches, []);
  const pagination = usePagination(branches || []);
  const [modalMode, setModalMode] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState(emptyBranch);
  const [errors, setErrors] = useState({});
  const [working, setWorking] = useState(false);
  const [pendingStatusBranch, setPendingStatusBranch] = useState(null);

  const openCreate = () => {
    setEditingId(null);
    setDraft(emptyBranch);
    setErrors({});
    setModalMode("create");
  };

  const openEdit = (branch) => {
    setEditingId(branch.id);
    setDraft({
      code: branch.code || "",
      name: branch.name || "",
      address: branch.address || "",
      sortCode: branch.sortCode || "",
      chargeGlAccount: branch.chargeGlAccount || "",
      vatGlAccount: branch.vatGlAccount || "",
      managersChequeAccount: branch.managersChequeAccount || "",
    });
    setErrors({});
    setModalMode("edit");
  };

  const closeModal = () => {
    if (working) return;
    setModalMode("");
    setEditingId(null);
    setDraft(emptyBranch);
    setErrors({});
  };

  const saveBranch = async (event) => {
    event.preventDefault();
    const nextErrors = {};
    if (!draft.code.trim()) nextErrors.code = "Enter a branch code.";
    if (!draft.name.trim()) nextErrors.name = "Enter a branch name.";
    if (!draft.address.trim()) nextErrors.address = "Enter the branch address.";
    if (!draft.sortCode.trim()) nextErrors.sortCode = "Enter the branch sort code.";
    if (!draft.chargeGlAccount.trim()) nextErrors.chargeGlAccount = "Enter the branch charge GL account.";
    if (!draft.vatGlAccount.trim()) nextErrors.vatGlAccount = "Enter the branch VAT GL account.";
    if (!/^\d{10}$/.test(draft.managersChequeAccount.trim())) {
      nextErrors.managersChequeAccount = "Enter the 10-digit Manager's Cheque account.";
    }
    if (draft.chargeGlAccount.trim().toLowerCase() === draft.vatGlAccount.trim().toLowerCase()) {
      nextErrors.vatGlAccount = "The VAT GL must be different from the charge GL.";
    }
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      toast.error(Object.values(nextErrors)[0]);
      return;
    }

    const duplicate = (branches || []).find(
      (branch) =>
        branch.id !== editingId &&
        branch.code.toLowerCase() === draft.code.trim().toLowerCase(),
    );
    if (duplicate) {
      setErrors({ code: "A branch with this code already exists." });
      toast.error("A branch with this code already exists.");
      return;
    }

    const body = {
      code: draft.code.trim().toUpperCase(),
      name: draft.name.trim(),
      address: draft.address.trim(),
      sortCode: draft.sortCode.trim(),
      chargeGlAccount: draft.chargeGlAccount.trim().toUpperCase(),
      vatGlAccount: draft.vatGlAccount.trim().toUpperCase(),
      managersChequeAccount: draft.managersChequeAccount.trim(),
    };

    setWorking(true);
    try {
      if (editingId) await adminApi.updateBranch(editingId, body);
      else await adminApi.createBranch(body);
      toast.success(`Branch ${editingId ? "updated" : "created"} successfully.`);
      setModalMode("");
      setEditingId(null);
      setDraft(emptyBranch);
      retry();
    } catch (requestError) {
      setErrors(requestError.fieldErrors || {});
      toast.error(requestError.message || "Unable to save the branch.");
    } finally {
      setWorking(false);
    }
  };

  const changeStatus = async () => {
    if (!pendingStatusBranch) return;
    setWorking(true);
    try {
      await adminApi.setBranchActive(pendingStatusBranch.id, !pendingStatusBranch.active);
      toast.success(
        `Branch ${pendingStatusBranch.active ? "deactivated" : "activated"} successfully.`,
      );
      setPendingStatusBranch(null);
      retry();
    } catch (requestError) {
      toast.error(requestError.message || "Unable to change the branch status.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Branch Management"
        description="Create, edit, and maintain NOVA Bank branch records."
      />
      <Card>
        <CardHeader
          title="Bank branches"
          description="Maintain branch identifiers, locations, and availability."
          action={
            <Button onClick={openCreate} disabled={loading}>
              <Plus className="h-4 w-4" />
              Create Branch
            </Button>
          }
        />
        {loading ? (
          <LoadingState label="Loading branches…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : (
          <>
            <ManagementTable
              headers={["Branch code", "Branch name", "Manager's Cheque account", "Charge GL", "VAT GL", "Sort code", "Address", "Status", "Action"]}
              minWidth="1320px"
              rows={pagination.paginatedItems.map((branch) => ({
                id: branch.id,
                cells: [
                  <span key="code" className="font-semibold text-ink">{branch.code}</span>,
                  branch.name,
                  branch.managersChequeAccount ? maskAccount(branch.managersChequeAccount) : "Not configured",
                  branch.chargeGlAccount || "Not configured",
                  branch.vatGlAccount || "Not configured",
                  branch.sortCode || "—",
                  branch.address || "—",
                  <StatusPill key="status" active={branch.active} />,
                  branch.systemDefined ? (
                    <span key="protected" className="text-xs font-semibold text-slate-400">
                      System defined
                    </span>
                  ) : (
                    <div key="actions" className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => openEdit(branch)}
                        className="inline-flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                        aria-label={`Edit ${branch.name}`}
                      >
                        <Pencil className="h-4 w-4" />Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => setPendingStatusBranch(branch)}
                        className={`rounded-md border p-2 ${
                          branch.active
                            ? "border-red-200 text-red-600 hover:bg-red-50"
                            : "border-emerald-200 text-emerald-700 hover:bg-emerald-50"
                        }`}
                        aria-label={`${branch.active ? "Deactivate" : "Activate"} ${branch.name}`}
                        title={branch.active ? "Deactivate" : "Activate"}
                      >
                        <Power className="h-4 w-4" />
                      </button>
                    </div>
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
        title={modalMode === "edit" ? "Edit branch" : "Create new branch"}
        description="Branch setup"
        footer={
          <>
            <Button variant="secondary" onClick={closeModal}>Cancel</Button>
            <Button loading={working} onClick={saveBranch}>
              {editingId ? "Save changes" : "Create branch"}
            </Button>
          </>
        }
      >
        <form onSubmit={saveBranch} className="space-y-4">
          <div className="mb-1 flex items-center gap-2 text-xs font-bold text-brand-700">
            <Building2 className="h-4 w-4" />Branch details
          </div>
          <BranchField label="Branch code" name="code" draft={draft} errors={errors} setDraft={setDraft} disabled={Boolean(editingId)} />
          <BranchField label="Branch name" name="name" draft={draft} errors={errors} setDraft={setDraft} />
          <BranchField label="Sort code" name="sortCode" draft={draft} errors={errors} setDraft={setDraft} />
          <BranchField label="Cheque charge GL account" name="chargeGlAccount" draft={draft} errors={errors} setDraft={setDraft} />
          <BranchField label="VAT GL account" name="vatGlAccount" draft={draft} errors={errors} setDraft={setDraft} />
          <BranchField label="Manager's Cheque account" name="managersChequeAccount" draft={draft} errors={errors} setDraft={setDraft} numeric maxLength={10} />
          <BranchField label="Address" name="address" draft={draft} errors={errors} setDraft={setDraft} />
          <button type="submit" className="hidden" aria-hidden="true" />
        </form>
      </Modal>

      <Modal
        open={Boolean(pendingStatusBranch)}
        onClose={() => !working && setPendingStatusBranch(null)}
        title={`${pendingStatusBranch?.active ? "Deactivate" : "Activate"} branch`}
        description={`Are you sure you want to ${pendingStatusBranch?.active ? "deactivate" : "activate"} ${pendingStatusBranch?.name || "this branch"}?`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPendingStatusBranch(null)}>Cancel</Button>
            <Button variant={pendingStatusBranch?.active ? "danger" : "primary"} loading={working} onClick={changeStatus}>
              {pendingStatusBranch?.active ? "Deactivate branch" : "Activate branch"}
            </Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-slate-600">
          Existing historical records remain available. New assignments should use an active branch.
        </p>
      </Modal>
    </>
  );
}

function BranchField({
  label,
  name,
  draft,
  errors,
  setDraft,
  disabled = false,
  required = true,
  numeric = false,
  maxLength,
}) {
  return (
    <FormField label={label} htmlFor={`branch-${name}`} error={errors[name]} required={required}>
      <input
        id={`branch-${name}`}
        className={inputClass}
        value={draft[name]}
        disabled={disabled}
        required={required}
        inputMode={numeric ? "numeric" : undefined}
        maxLength={maxLength}
        onChange={(event) => setDraft({
          ...draft,
          [name]: numeric ? event.target.value.replace(/\D/g, "") : event.target.value,
        })}
      />
    </FormField>
  );
}

import { CheckCircle2, RefreshCw, XCircle } from "lucide-react";
import { useState } from "react";
import toast from "react-hot-toast";
import { useAuth } from "../../app/providers/AuthProvider";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { RequestTable } from "../../components/ui/DataTable";
import { FormField, inputClass } from "../../components/ui/FormField";
import { Modal } from "../../components/ui/Modal";
import { PageHeader } from "../../components/ui/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../components/ui/PageState";
import { REQUEST_STATUS } from "../../constants/status";
import { useAsync } from "../../hooks/useAsync";
import { requestNotificationRefresh } from "../../hooks/useNotificationSummary";
import { approvalApi } from "../../services/api/operationsApi";
import { maskAccount } from "../../utils/formatters";
import { formatChequeType } from "../../constants/workflow";

export function ApprovalsPage() {
  const { user } = useAuth();
  const { data, loading, error, retry } = useAsync(
    async () => {
      const [pending, failedDebits] = await Promise.all([
        approvalApi.getPending(),
        approvalApi.getFailedDebits(),
      ]);
      return { pending, failedDebits };
    },
    [user?.id, user?.branchCode],
  );
  const [selected, setSelected] = useState(null);
  const [mode, setMode] = useState("");
  const [reason, setReason] = useState("");
  const [working, setWorking] = useState(false);
  const act = async () => {
    if (mode === "reject" && reason.trim().length < 5) return;
    setWorking(true);
    try {
      let response;
      if (mode === "approve") {
        response = await approvalApi.approveRequest(selected.id);
      } else if (mode === "retry") {
        response = await approvalApi.retryDebit(selected.id);
      } else {
        response = await approvalApi.rejectRequest(selected.id, {
          reason: reason.trim(),
        });
      }
      if (mode === "retry") {
        const nextStatus = response?.request?.status;
        if (nextStatus === REQUEST_STATUS.APPROVED) {
          toast.success(`${selected.id}: debit and cheque-book request completed successfully.`);
        } else if (nextStatus === REQUEST_STATUS.DEBIT_FAILED) {
          toast.error(`${selected.id}: the debit failed again.`);
        } else {
          toast(`${selected.id}: debit retry completed with status ${nextStatus || "unknown"}.`);
        }
      } else {
        toast.success(
          `${selected.id} was ${mode === "approve" ? "approved" : "rejected"} successfully.`,
        );
      }
      requestNotificationRefresh();
      setSelected(null);
      setMode("");
      setReason("");
      retry();
    } catch (error) {
      toast.error(error.message || "The approval action could not be completed.");
    } finally {
      setWorking(false);
    }
  };
  const open = (row) => {
    setSelected(row);
    setMode("");
    setReason("");
  };
  const openRetry = (row) => {
    setSelected(row);
    setMode("retry");
    setReason("");
  };
  const pendingRequests = data?.pending?.data || [];
  const failedDebits = data?.failedDebits?.data || [];
  return (
    <>
      <PageHeader
        title="Pending approvals"
        description="Review eligible branch requests before they are routed to Head Office."
      />
      <Card>
        {loading ? (
          <LoadingState label="Loading approval queue…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : !pendingRequests.length ? (
          <EmptyState
            title="No requests awaiting approval"
            description="New requests will appear here when approval is required."
          />
        ) : (
          <RequestTable rows={pendingRequests} onAction={open} actionLabel="Review" />
        )}
      </Card>
      <Card className="mt-6">
        <div className="border-b border-slate-100 px-5 py-4">
          <h2 className="font-semibold text-ink">Failed debits</h2>
          <p className="mt-1 text-sm text-slate-500">
            Retry only after confirming the customer has funded the account or resolved the rejection reason.
          </p>
        </div>
        {loading ? (
          <LoadingState label="Loading failed debits…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : !failedDebits.length ? (
          <EmptyState title="No failed debits awaiting retry" />
        ) : (
          <RequestTable rows={failedDebits} onAction={openRetry} actionLabel="Retry debit" />
        )}
      </Card>
      <Modal
        open={Boolean(selected)}
        onClose={() => !working && setSelected(null)}
        title={selected ? `${mode === "retry" ? "Retry debit for" : "Review"} ${selected.id}` : ""}
        description={mode === "retry"
          ? "Retry a previously confirmed failed debit."
          : "Confirm customer and request information before taking action."}
        size="lg"
        footer={
          mode === "retry" ? (
            <>
              <Button variant="secondary" onClick={() => setSelected(null)} disabled={working}>
                Cancel
              </Button>
              <Button loading={working} onClick={act}>
                <RefreshCw className="h-4 w-4" />
                Retry customer debit
              </Button>
            </>
          ) : mode ? (
            <>
              <Button
                variant="secondary"
                onClick={() => setMode("")}
                disabled={working}
              >
                Back
              </Button>
              <Button
                variant={mode === "reject" ? "danger" : "primary"}
                loading={working}
                disabled={mode === "reject" && reason.trim().length < 5}
                onClick={act}
              >
                {mode === "reject" ? (
                  <XCircle className="h-4 w-4" />
                ) : (
                  <CheckCircle2 className="h-4 w-4" />
                )}
                {working
                  ? "Saving…"
                  : mode === "reject"
                    ? "Reject request"
                    : "Proceed with approval"}
              </Button>
            </>
          ) : (
            <>
              <Button variant="danger" onClick={() => setMode("reject")}>
                <XCircle className="h-4 w-4" />
                Reject
              </Button>
              <Button onClick={() => setMode("approve")}>
                <CheckCircle2 className="h-4 w-4" />
                Approve
              </Button>
            </>
          )
        }
      >
        {selected && (
          <>
            {!mode && (
              <div className="grid gap-5 sm:grid-cols-2">
                {[
                  ["Customer Name", selected.customer],
                  ["Account Number", maskAccount(selected.accountNumber)],
                  ["Cheque Type", formatChequeType(selected.chequeTypeCode, selected.chequeTypeLabel)],
                  ["Domicile Branch", selected.accountHomeBranch || "—"],
                  ["Initiating Branch", selected.servicingBranch || selected.branch],
                  [
                    "Number of leaves",
                    selected.leafCount
                      ? `${selected.leafCount} leaves`
                      : "Not specified",
                  ],
                  ["Initiated by", selected.createdBy],
                ].map(([label, value]) => (
                  <div key={label}>
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                      {label}
                    </p>
                    <p className="mt-1 text-sm font-semibold text-ink">
                      {value}
                    </p>
                  </div>
                ))}
              </div>
            )}
            {mode === "approve" && (
              <Alert variant="warning" title="Confirm approval">
                <p>
                  {selected.debitRequired === false || selected.chequeTypeCode === "03"
                    ? "Approval will not debit this account. The Manager's Cheque request will become ready for vendor export immediately after approval."
                    : "Approval will immediately instruct Finacle to debit the customer's account. The request becomes exportable only after Finacle confirms the debit."}
                </p>
              </Alert>
            )}
            {mode === "retry" && (
              <Alert variant="warning" title="Confirm debit retry">
                <p>
                  This immediately attempts to debit the customer again. Continue only when the previous debit is confirmed failed and the customer has resolved the failure reason.
                </p>
              </Alert>
            )}
            {mode === "reject" && (
              <FormField
                label="Reason for rejection"
                htmlFor="rejection-reason"
                required
                error={
                  reason && reason.trim().length < 5
                    ? "Provide a clear reason of at least 5 characters."
                    : ""
                }
              >
                <textarea
                  id="rejection-reason"
                  autoFocus
                  className={`${inputClass} min-h-28 py-3`}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Explain what the branch must correct"
                />
              </FormField>
            )}
          </>
        )}
      </Modal>
    </>
  );
}

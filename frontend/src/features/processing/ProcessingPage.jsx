import { ArrowRight, Building2, RotateCcw, ShieldCheck } from "lucide-react";
import { useState } from "react";
import toast from "react-hot-toast";
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
import { StatusBadge } from "../../components/ui/StatusBadge";
import { PROCESSING_TRANSITIONS, REQUEST_STATUS } from "../../constants/status";
import { useAsync } from "../../hooks/useAsync";
import { requestNotificationRefresh } from "../../hooks/useNotificationSummary";
import { processingApi } from "../../services/api/operationsApi";
import { maskAccount } from "../../utils/formatters";
import { formatChequeType } from "../../constants/workflow";

const REVERSAL_UNKNOWN = "REVERSAL_UNKNOWN";
const REVERSAL_FAILED = "REVERSAL_FAILED";

export function ProcessingPage() {
  const { data, loading, error, retry } = useAsync(
    () => processingApi.getQueue(),
    [],
  );
  const [selected, setSelected] = useState(null);
  const [working, setWorking] = useState(false);
  const [reviewOutcome, setReviewOutcome] = useState("REVERSED");
  const [evidenceReference, setEvidenceReference] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [reviewError, setReviewError] = useState("");

  const isReversalReview = selected?.status === REQUEST_STATUS.REVERSAL_REVIEW_REQUIRED;

  const selectRequest = (request) => {
    setSelected(request);
    setReviewOutcome("REVERSED");
    setEvidenceReference("");
    setReviewNote("");
    setReviewError("");
  };

  const close = () => {
    if (!working) setSelected(null);
  };

  const finish = () => {
    requestNotificationRefresh();
    setSelected(null);
    retry();
  };

  const advance = async () => {
    const next = PROCESSING_TRANSITIONS[selected.status];
    if (!next) return;
    setWorking(true);
    try {
      await processingApi.advance(selected.id, selected.status);
      toast.success(`${selected.id}: ${next.label} completed successfully.`);
      finish();
    } catch (actionError) {
      toast.error(actionError.message || "The processing action could not be completed.");
    } finally {
      setWorking(false);
    }
  };

  const retryReversal = async () => {
    setWorking(true);
    try {
      const result = await processingApi.retryReversal(selected.id);
      if (result.request?.status === REQUEST_STATUS.CANCELLED) {
        toast.success(`${selected.id}: reversal completed and request cancelled.`);
      } else {
        toast.success(`${selected.id}: reversal retry completed.`);
      }
      finish();
    } catch (actionError) {
      toast.error(actionError.message || "The reversal could not be retried.");
    } finally {
      setWorking(false);
    }
  };

  const reconcileReversal = async () => {
    if (!evidenceReference.trim() || reviewNote.trim().length < 10) {
      setReviewError("Provide an evidence reference and a review note of at least 10 characters.");
      return;
    }
    setWorking(true);
    setReviewError("");
    try {
      await processingApi.reconcileReversal(selected.id, {
        outcome: reviewOutcome,
        evidenceReference: evidenceReference.trim(),
        note: reviewNote.trim(),
      });
      toast.success(reviewOutcome === "REVERSED"
        ? `${selected.id}: reversal confirmed; request cancelled and released for reinitiation.`
        : `${selected.id}: reversal marked unsuccessful and is now available for retry.`);
      finish();
    } catch (actionError) {
      setReviewError(actionError.message || "The reviewed reversal outcome could not be recorded.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Head Office"
        title="Processing queue"
        description="Manage vendor lifecycle updates and resolve customer charge reversals requiring Operations review."
        action={(
          <span className="inline-flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600">
            <Building2 className="h-4 w-4 text-brand-600" />
            All authorized branches
          </span>
        )}
      />
      <Card>
        {loading ? (
          <LoadingState label="Loading processing queue…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : !data?.data.length ? (
          <EmptyState title="Processing queue is clear" />
        ) : (
          <RequestTable
            rows={data.data}
            onAction={selectRequest}
            actionLabel="Review"
          />
        )}
      </Card>
      <Modal
        open={Boolean(selected)}
        onClose={close}
        title={selected ? `Process ${selected.id}` : ""}
        description={isReversalReview
          ? "Resolve the reversal using independently verified core-banking evidence."
          : "Only configured, sequential lifecycle transitions are presented."}
        footer={(
          <>
            <Button variant="secondary" onClick={close} disabled={working}>
              Close
            </Button>
            {selected && PROCESSING_TRANSITIONS[selected.status] && (
              <Button loading={working} onClick={advance}>
                {PROCESSING_TRANSITIONS[selected.status].label}
                <ArrowRight className="h-4 w-4" />
              </Button>
            )}
            {isReversalReview && selected.debitStatus === REVERSAL_FAILED && (
              <Button loading={working} onClick={retryReversal}>
                Retry reversal
                <RotateCcw className="h-4 w-4" />
              </Button>
            )}
            {isReversalReview && selected.debitStatus === REVERSAL_UNKNOWN && (
              <Button loading={working} onClick={reconcileReversal}>
                Record reviewed outcome
                <ShieldCheck className="h-4 w-4" />
              </Button>
            )}
          </>
        )}
      >
        {selected && (
          <div className="space-y-5">
            <div className="flex items-center justify-between rounded-md bg-slate-50 p-4">
              <div>
                <p className="font-semibold text-ink">{selected.customer}</p>
                <p className="mt-1 font-mono text-xs text-slate-500">
                  {maskAccount(selected.accountNumber)} · {selected.branch}
                </p>
                <p className="mt-1 text-xs font-semibold text-slate-600">
                  {formatChequeType(selected.chequeTypeCode, selected.chequeTypeLabel)}
                </p>
                {selected.startChequeNumber && (
                  <p className="mt-1 font-mono text-xs text-slate-600">
                    Serial range: {selected.startChequeNumber}–{selected.endChequeNumber}
                  </p>
                )}
              </div>
              <StatusBadge status={selected.status} />
            </div>

            {isReversalReview ? (
              <>
                {selected.debitStatus === REVERSAL_FAILED && (
                  <Alert variant="warning" title="Confirmed reversal failure">
                    Core banking confirmed that the previous reversal failed. Retry sends a new reversal request using the existing transaction record.
                  </Alert>
                )}
                {selected.debitStatus === REVERSAL_UNKNOWN && (
                  <>
                    <Alert variant="warning" title="Independent verification required">
                      Check the core-banking transaction before recording an outcome. Never select reversed based only on the original timeout or error.
                    </Alert>
                    <FormField label="Reviewed outcome" htmlFor="reversal-outcome" required>
                      <select
                        id="reversal-outcome"
                        className={inputClass}
                        value={reviewOutcome}
                        onChange={(event) => setReviewOutcome(event.target.value)}
                        disabled={working}
                      >
                        <option value="REVERSED">Customer charge is reversed</option>
                        <option value="NOT_REVERSED">Customer charge is not reversed</option>
                      </select>
                    </FormField>
                    <FormField label="Evidence reference" htmlFor="evidence-reference" required>
                      <input
                        id="evidence-reference"
                        className={inputClass}
                        value={evidenceReference}
                        onChange={(event) => setEvidenceReference(event.target.value)}
                        maxLength={100}
                        placeholder="Core-banking enquiry or ticket reference"
                        disabled={working}
                      />
                    </FormField>
                    <FormField label="Review note" htmlFor="review-note" required>
                      <textarea
                        id="review-note"
                        className={`${inputClass} min-h-28 py-3`}
                        value={reviewNote}
                        onChange={(event) => setReviewNote(event.target.value)}
                        minLength={10}
                        maxLength={1000}
                        placeholder="Describe how the outcome was independently confirmed"
                        disabled={working}
                      />
                    </FormField>
                    {reviewError && <Alert variant="error">{reviewError}</Alert>}
                  </>
                )}
              </>
            ) : (
              <>
                {selected.finacleRegistrationStatus && (
                  <Alert
                    variant={selected.finacleRegistrationStatus === 'SUCCESSFUL' ? 'success'
                      : selected.finacleRegistrationStatus === 'FAILED' ? 'error' : 'warning'}
                    title={`Finacle registration: ${selected.finacleRegistrationStatus}`}
                  >
                    {selected.finacleRegistrationFailureReason || 'Vendor serial allocation has been recorded.'}
                  </Alert>
                )}
                <Alert variant="info" title="External processing boundary">
                  The portal records vendor, dispatch and acknowledgement statuses supplied or confirmed by authorized users. Branch receipt is confirmed separately by the servicing branch.
                </Alert>
                {!PROCESSING_TRANSITIONS[selected.status] && (
                  <Alert variant="success">
                    This request has completed all available processing actions.
                  </Alert>
                )}
              </>
            )}
          </div>
        )}
      </Modal>
    </>
  );
}

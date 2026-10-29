import {
  ArrowLeft,
  Check,
  Clock3,
  PackageCheck,
  UserRound,
} from "lucide-react";
import { useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import toast from "react-hot-toast";
import { useAuth } from "../../app/providers/AuthProvider";
import { Button } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { PageHeader } from "../../components/ui/PageHeader";
import { ErrorState, LoadingState } from "../../components/ui/PageState";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { Modal } from "../../components/ui/Modal";
import { useAsync } from "../../hooks/useAsync";
import { chequeRequestApi } from "../../services/api/chequeRequestApi";
import { REQUEST_STATUS } from "../../constants/status";
import { hasPermission, PERMISSIONS } from "../../constants/access";
import { requestNotificationRefresh } from "../../hooks/useNotificationSummary";
import { formatBranch, formatDate, maskAccount } from "../../utils/formatters";
import { formatChequeType } from "../../constants/workflow";

export function RequestDetailPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [receiving, setReceiving] = useState(false);
  const [delivering, setDelivering] = useState(false);
  const [deliveryModalOpen, setDeliveryModalOpen] = useState(false);
  const [identityVerified, setIdentityVerified] = useState(false);
  const [handoverConfirmed, setHandoverConfirmed] = useState(false);
  const [accountLastFour, setAccountLastFour] = useState("");
  const [deliveryError, setDeliveryError] = useState("");
  const receivingRef = useRef(false);
  const deliveringRef = useRef(false);
  const accountLastFourRef = useRef(null);
  const { data, loading, error, retry } = useAsync(
    () => chequeRequestApi.getRequestById(requestId),
    [requestId],
  );
  if (loading)
    return (
      <Card>
        <LoadingState label="Loading request details…" />
      </Card>
    );
  if (error)
    return (
      <Card>
        <ErrorState message={error.message} onRetry={retry} />
      </Card>
    );
  const request = data.request;
  const canConfirmReceipt =
    hasPermission(user, PERMISSIONS.DELIVER_CHEQUE_REQUEST) &&
    request.status === REQUEST_STATUS.DISPATCHED &&
    user.branchCode === request.branchCode;
  const canDeliver =
    hasPermission(user, PERMISSIONS.DELIVER_CHEQUE_REQUEST) &&
    request.status === REQUEST_STATUS.RECEIVED_BY_BRANCH &&
    user.branchCode === request.branchCode;
  const confirmBranchReceipt = async () => {
    if (receivingRef.current) return;
    receivingRef.current = true;
    setReceiving(true);
    try {
      await chequeRequestApi.confirmBranchReceipt(request.id);
      toast.success(`${request.id}: branch receipt confirmed.`);
      requestNotificationRefresh();
      navigate("/cheque-requests");
    } catch (requestError) {
      toast.error(
        requestError.message || "The branch receipt could not be confirmed.",
      );
    } finally {
      receivingRef.current = false;
      setReceiving(false);
    }
  };

  const closeDeliveryModal = () => {
    if (deliveringRef.current) return;
    setDeliveryModalOpen(false);
    setIdentityVerified(false);
    setHandoverConfirmed(false);
    setAccountLastFour("");
    setDeliveryError("");
  };

  const finishDelivery = () => {
    setDeliveryModalOpen(false);
    toast.success(`${request.id} marked as delivered.`);
    requestNotificationRefresh();
    navigate("/cheque-requests");
  };

  const reconcileUncertainDelivery = async (requestError) => {
    try {
      const latest = await chequeRequestApi.getRequestById(request.id);
      if (latest.request?.status === REQUEST_STATUS.DELIVERED) {
        finishDelivery();
        return;
      }
      if (requestError.code === "STALE_REQUEST_VERSION") {
        setDeliveryModalOpen(false);
        toast.error("This request changed while you were confirming it. Review the latest status and try again.");
        await retry();
        return;
      }
    } catch {
      // Preserve the original delivery error when the follow-up status check also fails.
    }
    setDeliveryError(requestError.message || "The delivery could not be confirmed.");
  };

  const markDelivered = async (event) => {
    event.preventDefault();
    if (deliveringRef.current) return;
    deliveringRef.current = true;
    setDelivering(true);
    setDeliveryError("");
    try {
      await chequeRequestApi.markDelivered(request.id, {
        accountLastFour,
        identityVerified,
        handoverConfirmed,
        requestVersion: request.version,
      });
      finishDelivery();
    } catch (requestError) {
      if (requestError.code === "ACCOUNT_SUFFIX_MISMATCH") {
        setDeliveryError("The account digits do not match this request. Check the account and try again.");
        setAccountLastFour("");
        window.requestAnimationFrame(() => accountLastFourRef.current?.focus());
      } else if (
        requestError.code === "STALE_REQUEST_VERSION"
        || requestError.code === "NETWORK_ERROR"
        || requestError.retryable
      ) {
        await reconcileUncertainDelivery(requestError);
      } else {
        setDeliveryError(requestError.message || "The delivery could not be confirmed.");
      }
    } finally {
      deliveringRef.current = false;
      setDelivering(false);
    }
  };
  const deliveryConfirmationComplete =
    identityVerified && handoverConfirmed && /^\d{4}$/.test(accountLastFour);
  const facts = [
    ["Request ID", request.id],
    ["Request date", formatDate(request.createdAt, true)],
    ["Customer Name", request.customer],
    ["Account number", maskAccount(request.accountNumber)],
    ["Cheque type", formatChequeType(request.chequeTypeCode, request.chequeTypeLabel)],
    ["Starting cheque number", request.startChequeNumber || "Not assigned"],
    ["Ending cheque number", request.endChequeNumber || "Not assigned"],
    ["Vendor reference", request.vendorReference || "Not assigned"],
    ["Debit status", request.debitStatus === "NOT_REQUIRED" ? "Not required" : request.debitStatus || "Not started"],
    ["Charge amount", request.chargeAmount === "NOT_REQUIRED" ? "Not required" : request.chargeAmount || "Not available"],
    ["VAT amount", request.vatAmount === "NOT_REQUIRED" ? "Not required" : request.vatAmount || "Not available"],
    ["Total debit", request.totalDebit === "NOT_REQUIRED" ? "Not required" : request.totalDebit || "Not available"],
    ...(request.finacleRegistrationEnabled
      ? [["Finacle registration", request.finacleRegistrationStatus || "Not started"]]
      : []),
    [
      "Domicile Branch",
      formatBranch(
        request.accountHomeBranchName,
        request.accountHomeBranchCode,
        request.accountHomeBranch,
      ),
    ],
    [
      "Initiating Branch",
      formatBranch(
        request.servicingBranchName,
        request.servicingBranchCode,
        request.servicingBranch ?? request.branch,
      ),
    ],
    ["Initiated by", request.createdBy],
  ];
  return (
    <>
      <Link
        to="/cheque-requests"
        className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-ink"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to requests
      </Link>
      <PageHeader
        eyebrow="Request details"
        title={request.id}
        description="Complete request information and backend-provided lifecycle history."
        action={
          <div className="flex items-center gap-3">
            <StatusBadge status={request.status} />
            {canConfirmReceipt && (
              <Button loading={receiving} onClick={confirmBranchReceipt}>
                <PackageCheck className="h-4 w-4" />
                Confirm branch receipt
              </Button>
            )}
            {canDeliver && (
              <Button onClick={() => setDeliveryModalOpen(true)}>
                <PackageCheck className="h-4 w-4" />
                Mark as delivered
              </Button>
            )}
          </div>
        }
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_390px]">
        <div className="space-y-6">
          <Card>
            <CardHeader title="Request information" />
            <div className="grid gap-x-8 gap-y-6 p-5 sm:grid-cols-2 lg:grid-cols-3">
              {facts.map(([label, value]) => (
                <div key={label}>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {label}
                  </p>
                  <p
                    className={`mt-1 text-sm font-semibold text-slate-800 ${label.includes("Account") || label.includes("ID") ? "font-mono" : ""}`}
                  >
                    {value}
                  </p>
                </div>
              ))}
            </div>
          </Card>
          <Card>
            <CardHeader title="Cheque book specification" />
            <div className="p-5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                Number of leaves
              </p>
              <p className="mt-1 text-lg font-semibold text-ink">
                {request.leafCount
                  ? `${request.leafCount} leaves`
                  : "Not specified"}
              </p>
            </div>
          </Card>
          <Card className="border-blue-200 bg-blue-50 p-5">
            <div className="flex gap-3">
              <Clock3 className="h-5 w-5 shrink-0 text-blue-600" />
              <div>
                <p className="text-sm font-semibold text-blue-950">
                  External processing boundary
                </p>
                <p className="mt-1 text-sm leading-6 text-blue-800">
                  Cheque printing and logistics occur outside the portal. This
                  page only reflects status responses provided by authorized
                  backend integrations.
                </p>
              </div>
            </div>
          </Card>
        </div>
        <Card>
          <CardHeader
            title="Lifecycle Timeline"
            description="End to end request events recorded including status changes, timestamps and responsible actors."
          />
          <ol className="p-5">
            {request.timeline.map((event, index) => {
              const isLast = index === request.timeline.length - 1;
              const isCompleted =
                !isLast || request.status === REQUEST_STATUS.DELIVERED;
              return (
                <li
                  key={`${event.status}-${event.at}`}
                  className="relative flex gap-4 pb-7 last:pb-0"
                >
                  <div
                    aria-label={`${event.status} ${isCompleted ? "completed" : "current"}`}
                    className={`relative z-10 grid h-7 w-7 shrink-0 place-items-center rounded-full ${isCompleted ? "bg-emerald-600 text-white" : "bg-brand-600 text-white ring-4 ring-brand-100"}`}
                  >
                    {isCompleted ? (
                      <Check className="h-4 w-4" />
                    ) : (
                      <span className="h-2 w-2 rounded-full bg-white" />
                    )}
                  </div>
                  {!isLast && (
                    <span className="absolute left-[13px] top-7 h-[calc(100%-28px)] w-px bg-slate-200" />
                  )}
                  <div className="min-w-0 pt-0.5">
                    <p className="text-sm font-semibold text-ink">
                      {event.status}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      {formatDate(event.at, true)}
                    </p>
                    <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-400">
                      <UserRound className="h-3.5 w-3.5" />
                      {event.actor}
                    </p>
                    {event.note && (
                      <p className="mt-2 rounded bg-slate-50 p-2 text-xs leading-5 text-slate-600">
                        {event.note}
                      </p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        </Card>
      </div>
      <Modal
        open={deliveryModalOpen}
        onClose={closeDeliveryModal}
        closeDisabled={delivering}
        initialFocusRef={accountLastFourRef}
        title="Confirm customer collection"
        description="Confirm that the customer has collected the cheque book before marking this request as delivered."
        footer={
          <>
            <Button variant="secondary" onClick={closeDeliveryModal} disabled={delivering}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="delivery-confirmation-form"
              loading={delivering}
              disabled={!deliveryConfirmationComplete}
            >
              Confirm delivery
            </Button>
          </>
        }
      >
        <form id="delivery-confirmation-form" className="space-y-5" onSubmit={markDelivered}>
          <label className="flex items-start gap-3 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              checked={identityVerified}
              onChange={(event) => setIdentityVerified(event.target.checked)}
              disabled={delivering}
            />
            <span>{request.chequeTypeCode === "03"
              ? "The authorized receiving officer has been verified"
              : "Customer identity has been verified"}</span>
          </label>
          <label className="flex items-start gap-3 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              checked={handoverConfirmed}
              onChange={(event) => setHandoverConfirmed(event.target.checked)}
              disabled={delivering}
            />
            <span>{request.chequeTypeCode === "03"
              ? "Manager's Cheque stock has been handed into branch custody"
              : "Cheque book has been handed to the customer"}</span>
          </label>
          <div>
            <label htmlFor="account-last-four" className="block text-sm font-semibold text-slate-700">
              Last 4 digits of the customer account number
            </label>
            <input
              ref={accountLastFourRef}
              id="account-last-four"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              maxLength={4}
              pattern="[0-9]{4}"
              value={accountLastFour}
              onChange={(event) => {
                setAccountLastFour(event.target.value.replace(/\D/g, "").slice(0, 4));
                setDeliveryError("");
              }}
              disabled={delivering}
              aria-invalid={Boolean(deliveryError)}
              aria-describedby={deliveryError ? "delivery-confirmation-error" : undefined}
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm tracking-widest text-slate-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100"
              placeholder="0000"
            />
            {deliveryError && (
              <p id="delivery-confirmation-error" role="alert" className="mt-2 text-sm text-red-600">
                {deliveryError}
              </p>
            )}
          </div>
          <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm leading-6 text-amber-900">
            {request.chequeTypeCode === "03"
              ? "This action is final and cannot be undone. Confirm only after the Manager's Cheque stock has been handed into authorized branch custody."
              : "This action is final and cannot be undone. Only confirm delivery after the cheque book has been handed to the customer."}
          </p>
        </form>
      </Modal>
    </>
  );
}

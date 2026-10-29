import {
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  CircleX,
  Search,
  ShieldCheck,
} from "lucide-react";
import { useRef, useState } from "react";
import toast from "react-hot-toast";
import { Link } from "react-router-dom";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { FormField, inputClass } from "../../components/ui/FormField";
import { Modal } from "../../components/ui/Modal";
import { PageHeader } from "../../components/ui/PageHeader";
import { requestNotificationRefresh } from "../../hooks/useNotificationSummary";
import { customerApi } from "../../services/api/customerApi";
import { chequeRequestApi } from "../../services/api/chequeRequestApi";
import {
  CHEQUE_LEAF_OPTIONS,
  CHEQUE_TYPE_OPTIONS,
  formatChequeType,
} from "../../constants/workflow";
import { formatBranch, maskAccount } from "../../utils/formatters";

const steps = ["Account Selection", "Validation", "Review & Submit"];

function Progress({ current }) {
  return (
    <ol className="mb-7 grid grid-cols-3 border-b border-slate-200">
      {steps.map((label, index) => (
        <li
          key={label}
          className={`relative pb-4 text-xs font-semibold sm:text-sm ${index <= current ? "text-ink" : "text-slate-400"}`}
        >
          <span
            className={`mr-2 inline-grid h-6 w-6 place-items-center rounded-full text-xs ${index < current ? "bg-emerald-600 text-white" : index === current ? "bg-ink text-white" : "bg-slate-100 text-slate-500"}`}
          >
            {index < current ? <Check className="h-3.5 w-3.5" /> : index + 1}
          </span>
          {label}
          <span
            className={`absolute bottom-[-1px] left-0 h-0.5 w-full ${index === current ? "bg-brand-600" : "bg-transparent"}`}
          />
        </li>
      ))}
    </ol>
  );
}

function CustomerSummary({ customer, managersCheque = false }) {
  const accountHomeBranch = formatBranch(
    customer.accountHomeBranchName ?? customer.branchName,
    customer.accountHomeBranchCode ?? customer.branchCode,
    customer.accountHomeBranch ?? customer.branch,
  );
  const servicingBranch = formatBranch(
    customer.servicingBranchName,
    customer.servicingBranchCode,
    customer.servicingBranch,
  );
  const details = [
    [managersCheque ? "Account name" : "Customer name", customer.name],
    ["Account number", maskAccount(customer.accountNumber)],
    ["Account type", customer.acctOwnership],
    // ["Account status", customer.accountStatus],
    ["Domicile Branch", accountHomeBranch],
    ["Initiating Branch", servicingBranch],
    ...(managersCheque ? [
      ["Scheme code", customer.schemeCode],
      ["Currency", customer.currency],
    ] : [
      ["KYC status", customer.kycLevel],
      ["Restrictions", customer.restrictions],
    ]),
  ];
  return (
    <div className="grid gap-x-8 gap-y-5 sm:grid-cols-2 lg:grid-cols-3">
      {details.map(([label, value]) => (
        <div key={label}>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
            {label}
          </p>
          <p className="mt-1 text-sm font-semibold text-slate-800">{value}</p>
        </div>
      ))}
    </div>
  );
}

export function NewRequestPage() {
  const [accountNumber, setAccountNumber] = useState("");
  const [result, setResult] = useState(null);
  const [lookupError, setLookupError] = useState("");
  const [searching, setSearching] = useState(false);
  const [leafCount, setLeafCount] = useState(null);
  const [chequeType, setChequeType] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(null);
  const lookupSequence = useRef(0);
  const current = created
    ? 3
    : result
      ? result.eligibility.eligible
        ? 2
        : 1
      : 0;
  const lookup = async (event) => {
    event.preventDefault();
    const lookupId = ++lookupSequence.current;
    setLookupError("");
    setResult(null);
    setLeafCount(null);
    if (!chequeType) {
      setLookupError("Select a cheque type before searching for the account.");
      return;
    }
    if (!/^\d{10}$/.test(accountNumber)) {
      setLookupError("Enter a valid 10-digit account number.");
      return;
    }
    setSearching(true);
    try {
      const response = await customerApi.getCustomerByAccount(accountNumber, chequeType);
      if (lookupId === lookupSequence.current) setResult(response);
    } catch (error) {
      if (lookupId === lookupSequence.current) setLookupError(error.message);
    } finally {
      if (lookupId === lookupSequence.current) setSearching(false);
    }
  };
  const selectChequeType = async (nextType) => {
    const lookupId = ++lookupSequence.current;
    setChequeType(nextType);
    setAccountNumber("");
    setResult(null);
    setLeafCount(null);
    setLookupError("");
    setSearching(false);
    if (nextType !== "03") return;

    setSearching(true);
    try {
      const response = await customerApi.getManagersChequeAccount();
      if (lookupId === lookupSequence.current) {
        setResult(response);
        setAccountNumber(response.customer.accountNumber);
      }
    } catch (error) {
      if (lookupId === lookupSequence.current) setLookupError(error.message);
    } finally {
      if (lookupId === lookupSequence.current) setSearching(false);
    }
  };
  const submit = async () => {
    setSubmitting(true);
    try {
      const request = { leafCount, chequeType };
      if (chequeType !== "03") request.accountNumber = result.customer.accountNumber;
      const response = await chequeRequestApi.createRequest(request);
      setCreated(response.request);
      setConfirmOpen(false);
      toast.success(`${response.request.id} submitted successfully.`);
      requestNotificationRefresh();
    } catch (error) {
      toast.error(error.message || "The cheque book request could not be submitted.");
      setConfirmOpen(false);
    } finally {
      setSubmitting(false);
    }
  };
  const reset = () => {
    lookupSequence.current += 1;
    setAccountNumber("");
    setResult(null);
    setLookupError("");
    setSearching(false);
    setLeafCount(null);
    setChequeType("");
    setCreated(null);
  };

  if (created)
    return (
      <>
        <PageHeader
          eyebrow="Request submitted"
          title="Submission complete"
          description="The request has been recorded and routed according to the configured approval policy."
        />
        <Card className="mx-auto max-w-2xl p-7 text-center sm:p-10">
          <span className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-emerald-50">
            <CheckCircle2 className="h-8 w-8 text-emerald-600" />
          </span>
          <h2 className="mt-5 text-2xl font-bold text-ink">
            Cheque book request submitted successfully
          </h2>
          <p className="mt-2 text-sm text-slate-500">
            Use this request ID to track the request throughout its lifecycle.
          </p>
          <div className="mx-auto mt-6 max-w-sm rounded-md border border-dashed border-brand-300 bg-brand-50 px-5 py-4">
            <p className="text-xs font-semibold uppercase tracking-widest text-brand-700">
              Request ID
            </p>
            <p className="mt-1 font-mono text-xl font-bold text-ink">
              {created.id}
            </p>
            <p className="mt-2 text-sm font-semibold text-brand-700">
              {created.leafCount} leaves
            </p>
            <p className="mt-1 text-sm font-semibold text-brand-700">
              {formatChequeType(created.chequeTypeCode, created.chequeTypeLabel)}
            </p>
          </div>
          <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
            <Link to={`/cheque-requests/${created.id}`}>
              <Button className="w-full sm:w-auto">
                View request <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
            <Button variant="secondary" onClick={reset}>
              Create another
            </Button>
            <Link to="/dashboard">
              <Button variant="ghost" className="w-full sm:w-auto">
                Back to dashboard
              </Button>
            </Link>
          </div>
        </Card>
      </>
    );

  return (
    <>
      <PageHeader
        eyebrow="Branch request"
        title="New Chequebook Request"
        description="Retrieve the account and display backend eligibility before submitting a request."
      />
      <Card className="p-5 sm:p-6">
        <Progress current={current} />
        <form onSubmit={lookup} className="max-w-3xl space-y-5">
          <FormField
            label="Cheque type"
            htmlFor="cheque-type"
            required
            hint="Select the cheque type before retrieving the account."
          >
            <select
              id="cheque-type"
              className={inputClass}
              value={chequeType}
              onChange={(event) => selectChequeType(event.target.value)}
              required
            >
              <option value="">Select cheque type</option>
              {CHEQUE_TYPE_OPTIONS.map((type) => (
                <option key={type.code} value={type.code}>
                  {type.code} — {type.label}
                </option>
              ))}
            </select>
          </FormField>
          {chequeType !== "03" && (
            <FormField
              label="Customer Account Number"
              htmlFor="account-number"
              required
              error={lookupError}
            >
              <div className="flex flex-col gap-3 sm:flex-row">
                <input
                  id="account-number"
                  inputMode="numeric"
                  maxLength={10}
                  className={`${inputClass} font-mono tracking-wider`}
                  placeholder="Enter 10-digit account number"
                  value={accountNumber}
                  onChange={(e) =>
                    setAccountNumber(e.target.value.replace(/\D/g, ""))
                  }
                />
                <Button type="submit" loading={searching} className="sm:min-w-28">
                  <Search className="h-4 w-4" />
                  {searching ? "Searching…" : "Search"}
                </Button>
              </div>
            </FormField>
          )}
          {chequeType === "03" && searching && (
            <Alert variant="info" title="Loading configured account">
              Retrieving your branch's Managers Cheque account from the backend.
            </Alert>
          )}
          {chequeType === "03" && lookupError && (
            <Alert variant="error" title="Managers Cheque account unavailable">
              {lookupError}
            </Alert>
          )}
        </form>
        {result && (
          <div className="mt-7 space-y-6">
            <Card className="shadow-none">
              <CardHeader
                title={chequeType === "03" ? "Managers Cheque account information" : "Customer information"}
                description={chequeType === "03"
                  ? "Verified in Finacle using your assigned branch configuration"
                  : "Retrieved from the core banking service"}
              />
              <div className="p-5">
                <CustomerSummary customer={result.customer} managersCheque={chequeType === "03"} />
              </div>
            </Card>
            {result.eligibility.eligible ? (
              <Alert
                variant="success"
                title={chequeType === "03" ? "Configured account loaded" : "Customer is eligible"}
              >
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  {result.eligibility.checks.map(([label, pass]) => (
                    <span
                      key={label}
                      className="flex items-center gap-2 text-sm"
                    >
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                      {label}
                    </span>
                  ))}
                </div>
              </Alert>
            ) : (
              <Alert
                variant="error"
                title="Cheque book request cannot continue"
              >
                <p>{result.eligibility.reason}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {result.eligibility.checks.map(([label, pass]) => (
                    <span
                      key={label}
                      className="inline-flex items-center gap-1.5 rounded bg-white/60 px-2 py-1 text-xs font-medium"
                    >
                      {pass ? (
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                      ) : (
                        <CircleX className="h-3.5 w-3.5 text-red-600" />
                      )}
                      {label}
                    </span>
                  ))}
                </div>
              </Alert>
            )}
            {result.eligibility.eligible && (
              <Card className="shadow-none">
                <CardHeader
                  title="Request details"
                  description="Select the required chequebook size."
                />
                <div className="space-y-6 p-5">
                  {chequeType === "03" && (
                    <Alert variant="info" title="Managers Cheque account">
                      This account was selected automatically from your assigned branch configuration.
                      No customer debit will be performed after BOM approval.
                    </Alert>
                  )}
                  <fieldset>
                    <legend className="text-sm font-semibold text-slate-700">
                      Number of leaves{" "}
                      <span className="text-red-600" aria-hidden="true">
                        *
                      </span>
                    </legend>
                    <p
                      id="leaf-count-hint"
                      className="mt-1 text-xs text-slate-500"
                    >
                      Select one option for this chequebook request.
                    </p>
                    <div className="mt-4 grid gap-3 sm:grid-cols-3">
                      {CHEQUE_LEAF_OPTIONS.map((option) => (
                        <label
                          key={option}
                          className={`flex cursor-pointer items-center gap-3 rounded-md border p-4 transition-colors ${leafCount === option ? "border-brand-600 bg-brand-50 ring-2 ring-brand-100" : "border-slate-200 bg-white hover:border-slate-300"}`}
                        >
                          <input
                            type="radio"
                            name="leaf-count"
                            value={option}
                            checked={leafCount === option}
                            onChange={() => setLeafCount(option)}
                            aria-describedby="leaf-count-hint"
                            className="h-4 w-4 accent-brand-600"
                          />
                          <span className="text-sm font-semibold text-ink">
                            {option} leaves
                          </span>
                        </label>
                      ))}
                    </div>
                  </fieldset>
                  <div className="flex justify-end">
                    <Button
                      disabled={!leafCount || !chequeType}
                      onClick={() => setConfirmOpen(true)}
                    >
                      Review request <ArrowRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </Card>
            )}
          </div>
        )}
      </Card>
      <Modal
        open={confirmOpen}
        onClose={() => !submitting && setConfirmOpen(false)}
        title="Confirm cheque book request"
        description="Review the details before submitting to the configured workflow."
        size="lg"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setConfirmOpen(false)}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button onClick={submit} loading={submitting}>
              <ShieldCheck className="h-4 w-4" />
              {submitting ? "Submitting…" : "Submit request"}
            </Button>
          </>
        }
      >
        <CustomerSummary customer={result?.customer || {}} managersCheque={chequeType === "03"} />
        <div className="mt-5 grid gap-4 rounded-md bg-slate-50 p-4 sm:grid-cols-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              Cheque type
            </p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {formatChequeType(chequeType)}
            </p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              Number of leaves
            </p>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {leafCount} leaves
            </p>
          </div>
        </div>
        <Alert variant="info" title="Before you submit">
          <p>
            {chequeType === "03"
              ? "The backend will verify that this is your branch's configured Managers Cheque account. No customer debit will be performed after BOM approval."
              : "The backend will remain the authority for duplicate prevention, eligibility and the final request identifier."}
          </p>
        </Alert>
      </Modal>
    </>
  );
}

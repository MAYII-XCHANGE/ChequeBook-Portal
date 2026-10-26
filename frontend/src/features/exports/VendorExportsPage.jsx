import {
  CheckCircle2,
  Download,
  FileSpreadsheet,
  FileText,
  RotateCcw,
  Send,
  Upload,
  UploadCloud,
  X,
} from 'lucide-react';
import { useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { FormField, inputClass } from '../../components/ui/FormField';
import { Modal } from '../../components/ui/Modal';
import { PaginationControls } from '../../components/ui/PaginationControls';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingState } from '../../components/ui/PageState';
import { useAsync } from '../../hooks/useAsync';
import { usePagination } from '../../hooks/usePagination';
import { requestNotificationRefresh } from '../../hooks/useNotificationSummary';
import { exportApi } from '../../services/api/exportApi';
import { formatChequeType } from '../../constants/workflow';
import { formatDate, maskAccount } from '../../utils/formatters';
import { parseVendorPrintResponseCsv } from './vendorPrintResponseCsv';

const EMPTY_ROWS = [];

export function VendorExportsPage() {
  const { data, loading, error, retry } = useAsync(async () => {
    const [eligible, batches] = await Promise.all([exportApi.eligible(), exportApi.list()]);
    return { eligible: eligible.data, batches };
  }, []);
  const [selected, setSelected] = useState([]);
  const [generating, setGenerating] = useState(false);
  const [workingBatches, setWorkingBatches] = useState({});
  const [uploadCandidate, setUploadCandidate] = useState(null);
  const [uploadRows, setUploadRows] = useState([]);
  const [uploadWorkbook, setUploadWorkbook] = useState(null);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadError, setUploadError] = useState('');
  const [uploadDragging, setUploadDragging] = useState(false);
  const [allocationsBatch, setAllocationsBatch] = useState(null);
  const [allocations, setAllocations] = useState([]);
  const [loadingAllocations, setLoadingAllocations] = useState(false);
  const [reconcileCandidate, setReconcileCandidate] = useState(null);
  const [reconcileOutcome, setReconcileOutcome] = useState('REGISTERED');
  const [evidenceReference, setEvidenceReference] = useState('');
  const [reconcileNote, setReconcileNote] = useState('');
  const [reconcileError, setReconcileError] = useState('');
  const batchActionsInFlight = useRef(new Set());
  const uploadInputRef = useRef(null);
  const eligiblePagination = usePagination(data?.eligible || EMPTY_ROWS);
  const allSelected = useMemo(
    () => data?.eligible.length > 0 && selected.length === data.eligible.length,
    [data, selected],
  );

  const setBatchWorking = (batchNumber, working) => {
    setWorkingBatches((current) => {
      const next = { ...current };
      if (working) next[batchNumber] = true;
      else delete next[batchNumber];
      return next;
    });
  };

  const runBatchAction = async (batchNumber, action) => {
    if (batchActionsInFlight.current.has(batchNumber)) return false;
    batchActionsInFlight.current.add(batchNumber);
    setBatchWorking(batchNumber, true);
    try {
      await action();
      return true;
    } finally {
      batchActionsInFlight.current.delete(batchNumber);
      setBatchWorking(batchNumber, false);
    }
  };

  const generate = async () => {
    if (generating) return;
    setGenerating(true);
    try {
      const batch = await exportApi.create(selected);
      requestNotificationRefresh();
      await exportApi.download(batch.batchNumber);
      toast.success(`${batch.batchNumber} generated with ${batch.recordCount} request(s).`);
      setSelected([]);
      retry();
    } catch (actionError) {
      toast.error(actionError.message || 'The vendor export could not be generated.');
    } finally {
      setGenerating(false);
    }
  };

  const sent = async (batchNumber) => {
    try {
      const completed = await runBatchAction(batchNumber, () => exportApi.confirmSent(batchNumber));
      if (!completed) return;
      toast.success(`${batchNumber} marked as sent to the vendor.`);
      requestNotificationRefresh();
      retry();
    } catch (actionError) {
      toast.error(actionError.message || 'The batch could not be marked as sent.');
    }
  };

  const openUpload = (batch) => {
    setUploadError('');
    setUploadRows([]);
    setUploadWorkbook(null);
    setUploadFile(null);
    setUploadDragging(false);
    setUploadCandidate(batch);
  };

  const closeUpload = () => {
    if (uploadCandidate && workingBatches[uploadCandidate.batchNumber]) return;
    setUploadCandidate(null);
    setUploadRows([]);
    setUploadWorkbook(null);
    setUploadFile(null);
    setUploadDragging(false);
    setUploadError('');
  };

  const processVendorResponseFile = async (file) => {
    setUploadRows([]);
    setUploadWorkbook(null);
    setUploadError('');
    if (!file) return;
    setUploadFile(file);
    try {
      const fileName = file.name?.toLowerCase() || '';
      if (file.size > 10 * 1024 * 1024) {
        throw new Error('The vendor response file must not be larger than 10 MB.');
      }
      if (!fileName.endsWith('.csv') && !fileName.endsWith('.xlsx')) {
        throw new Error('Choose a CSV or Excel (.xlsx) vendor response file.');
      }
      if (fileName.endsWith('.xlsx')) {
        setUploadWorkbook(file);
        return;
      }
      const rows = parseVendorPrintResponseCsv(await file.text());
      if (uploadCandidate && rows.length !== uploadCandidate.recordCount) {
        throw new Error(
          `This batch expects ${uploadCandidate.recordCount} response row(s), but the file contains ${rows.length}.`,
        );
      }
      setUploadRows(rows);
    } catch (fileError) {
      setUploadError(fileError.message || 'The vendor response file could not be read.');
    }
  };

  const readVendorResponse = async (event) => {
    await processVendorResponseFile(event.target.files?.[0]);
    event.target.value = '';
  };

  const dropVendorResponse = async (event) => {
    event.preventDefault();
    if (uploadSubmitting) return;
    setUploadDragging(false);
    await processVendorResponseFile(event.dataTransfer.files?.[0]);
  };

  const removeVendorResponse = () => {
    setUploadRows([]);
    setUploadWorkbook(null);
    setUploadFile(null);
    setUploadError('');
  };

  const importVendorResponse = async () => {
    if (!uploadCandidate || (!uploadRows.length && !uploadWorkbook)) return;
    const { batchNumber } = uploadCandidate;
    setUploadError('');
    try {
      const completed = await runBatchAction(batchNumber, () => (
        uploadWorkbook
          ? exportApi.importPrintResponseWorkbook(batchNumber, uploadWorkbook)
          : exportApi.importPrintResponse(batchNumber, uploadRows)
      ));
      if (!completed) return;
      setUploadCandidate(null);
      setUploadRows([]);
      setUploadWorkbook(null);
      setUploadFile(null);
      toast.success(`${batchNumber}: vendor ranges imported and Finacle registration processed.`);
      requestNotificationRefresh();
      retry();
    } catch (actionError) {
      setUploadError(actionError.message || 'The vendor response could not be imported.');
    }
  };

  const openAllocations = async (batch) => {
    setAllocationsBatch(batch);
    setLoadingAllocations(true);
    try {
      const response = await exportApi.printResponse(batch.batchNumber);
      setAllocations(response.data || []);
    } catch (actionError) {
      toast.error(actionError.message || 'The vendor response details could not be loaded.');
      setAllocationsBatch(null);
    } finally {
      setLoadingAllocations(false);
    }
  };

  const refreshAllocations = async () => {
    if (!allocationsBatch) return;
    const response = await exportApi.printResponse(allocationsBatch.batchNumber);
    setAllocations(response.data || []);
  };

  const retryRegistration = async (row) => {
    try {
      await exportApi.retryRegistration(row.requestId);
      toast.success(`${row.requestId}: Finacle registration retry completed.`);
      requestNotificationRefresh();
      await refreshAllocations();
      retry();
    } catch (actionError) {
      toast.error(actionError.message || 'Finacle registration could not be retried.');
    }
  };

  const openReconciliation = (row) => {
    setReconcileCandidate(row);
    setReconcileOutcome('REGISTERED');
    setEvidenceReference('');
    setReconcileNote('');
    setReconcileError('');
  };

  const reconcileRegistration = async () => {
    if (!evidenceReference.trim() || reconcileNote.trim().length < 10) {
      setReconcileError('Provide an evidence reference and a note of at least 10 characters.');
      return;
    }
    try {
      await exportApi.reconcileRegistration(reconcileCandidate.requestId, {
        outcome: reconcileOutcome,
        evidenceReference: evidenceReference.trim(),
        note: reconcileNote.trim(),
      });
      toast.success(`${reconcileCandidate.requestId}: reviewed Finacle outcome recorded.`);
      setReconcileCandidate(null);
      requestNotificationRefresh();
      await refreshAllocations();
      retry();
    } catch (actionError) {
      setReconcileError(actionError.message || 'The reviewed outcome could not be recorded.');
    }
  };

  const download = async (batchNumber) => {
    try {
      const filename = await exportApi.download(batchNumber);
      toast.success(`${filename} downloaded.`);
    } catch (actionError) {
      toast.error(actionError.message || 'The export could not be downloaded.');
    }
  };

  const uploadSubmitting = Boolean(uploadCandidate && workingBatches[uploadCandidate.batchNumber]);
  const uploadReady = Boolean((uploadRows.length || uploadWorkbook) && !uploadError);
  const uploadFileSize = uploadFile?.size
    ? `${uploadFile.size < 1024 * 1024
      ? `${Math.max(1, Math.round(uploadFile.size / 1024))} KB`
      : `${(uploadFile.size / (1024 * 1024)).toFixed(1)} MB`}`
    : '';

  return (
    <>
      <PageHeader
        eyebrow="Clearing Operations"
        title="Vendor exports"
        description="Create vendor batches, import assigned cheque ranges and register printed cheque books in Finacle."
      />
      {loading ? (
        <Card><LoadingState /></Card>
      ) : error ? (
        <Card><ErrorState message={error.message} onRetry={retry} /></Card>
      ) : (
        <div className="space-y-6">
          <Card>
            <CardHeader
              title="Requests ready for export"
              description="Only approved requests with a successful Finacle debit appear here"
              action={(
                <Button disabled={!selected.length} loading={generating} onClick={generate}>
                  <FileSpreadsheet className="h-4 w-4" />
                  Generate Excel ({selected.length})
                </Button>
              )}
            />
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50">
                  <tr>
                    <th className="px-5 py-3">
                      <input
                        aria-label="Select all"
                        type="checkbox"
                        checked={allSelected}
                        onChange={() => setSelected(allSelected ? [] : data.eligible.map((item) => item.id))}
                      />
                    </th>
                    {['Request', 'Customer', 'Account', 'Cheque Type', 'Branch', 'Leaves'].map((heading) => (
                      <th key={heading} className="px-5 py-3 text-xs uppercase text-slate-500">{heading}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {eligiblePagination.paginatedItems.map((item) => (
                    <tr key={item.id}>
                      <td className="px-5 py-3">
                        <input
                          aria-label={`Select ${item.id}`}
                          type="checkbox"
                          checked={selected.includes(item.id)}
                          onChange={() => setSelected(selected.includes(item.id)
                            ? selected.filter((id) => id !== item.id)
                            : [...selected, item.id])}
                        />
                      </td>
                      <td className="px-5 py-3 font-semibold">{item.id}</td>
                      <td className="px-5 py-3">{item.customer}</td>
                      <td className="px-5 py-3 font-mono text-xs">{maskAccount(item.accountNumber)}</td>
                      <td className="px-5 py-3">
                        {formatChequeType(item.chequeTypeCode, item.chequeTypeLabel)}
                      </td>
                      <td className="px-5 py-3">{item.branch}</td>
                      <td className="px-5 py-3">{item.leafCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!data.eligible.length && (
                <p className="p-8 text-center text-sm text-slate-500">
                  No successfully debited requests are waiting for export.
                </p>
              )}
            </div>
            <PaginationControls {...eligiblePagination} onPageChange={eligiblePagination.setCurrentPage} />
          </Card>
          <Card>
            <CardHeader title="Export history" />
            <div className="divide-y divide-slate-100">
              {data.batches.map((batch) => (
                <div key={batch.batchNumber} className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink">{batch.batchNumber}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {batch.recordCount} records · {formatDate(batch.createdAt, true)} · {batch.status}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" onClick={() => download(batch.batchNumber)}>
                      <Download className="h-4 w-4" />
                      Download
                    </Button>
                    {batch.status === 'EXPORTED' && (
                      <Button
                        loading={Boolean(workingBatches[batch.batchNumber])}
                        onClick={() => sent(batch.batchNumber)}
                      >
                        <Send className="h-4 w-4" />
                        Confirm sent
                      </Button>
                    )}
                    {batch.status === 'SENT' && (
                      <Button
                        loading={Boolean(workingBatches[batch.batchNumber])}
                        onClick={() => openUpload(batch)}
                      >
                        <Upload className="h-4 w-4" />
                        Upload vendor response
                      </Button>
                    )}
                    {batch.status === 'PRINTED' && (
                      <Button variant="secondary" onClick={() => openAllocations(batch)}>
                        View serial allocations
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
      <Modal
        open={Boolean(uploadCandidate)}
        onClose={closeUpload}
        closeDisabled={uploadSubmitting}
        title="Upload vendor print response"
        description="Validate the completed print file before registering the cheque ranges in Finacle."
        size="lg"
        footer={(
          <>
            <Button variant="secondary" onClick={closeUpload} disabled={uploadSubmitting}>
              Cancel
            </Button>
            <Button loading={uploadSubmitting} disabled={!uploadReady} onClick={importVendorResponse}>
              {!uploadSubmitting && <Upload className="h-4 w-4" />}
              Import and register in Finacle
            </Button>
          </>
        )}
      >
        {uploadCandidate && (
          <div className="space-y-5">
            <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vendor batch</p>
                <p className="mt-1 font-semibold text-ink">{uploadCandidate.batchNumber}</p>
              </div>
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 font-medium text-amber-800">
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                  Awaiting response
                </span>
                <span>{uploadCandidate.recordCount} request{uploadCandidate.recordCount === 1 ? '' : 's'}</span>
              </div>
            </div>

            <div>
              <div className="mb-2 flex items-end justify-between gap-4">
                <div>
                  <h3 className="text-sm font-semibold text-ink">Vendor response file</h3>
                  <p className="mt-1 text-xs text-slate-500">CSV or Excel (.xlsx), up to 10 MB</p>
                </div>
                {uploadFile && !uploadSubmitting && (
                  <button
                    type="button"
                    className="text-xs font-semibold text-blue-700 hover:text-blue-900"
                    onClick={() => uploadInputRef.current?.click()}
                  >
                    Replace file
                  </button>
                )}
              </div>
              <input
                ref={uploadInputRef}
                aria-label="Vendor response file"
                type="file"
                accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={readVendorResponse}
                disabled={uploadSubmitting}
                className="sr-only"
              />
              {!uploadFile ? (
                <div
                  className={`rounded-lg border-2 border-dashed px-6 py-8 text-center transition-colors ${
                    uploadDragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-white hover:border-slate-400'
                  }`}
                  onDragEnter={(event) => { event.preventDefault(); if (!uploadSubmitting) setUploadDragging(true); }}
                  onDragOver={(event) => event.preventDefault()}
                  onDragLeave={(event) => {
                    if (!event.currentTarget.contains(event.relatedTarget)) setUploadDragging(false);
                  }}
                  onDrop={dropVendorResponse}
                >
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-blue-50 text-blue-700">
                    <UploadCloud className="h-6 w-6" aria-hidden="true" />
                  </div>
                  <p className="mt-3 text-sm font-semibold text-ink">Drop the completed vendor file here</p>
                  <p className="mt-1 text-xs text-slate-500">or</p>
                  <Button
                    variant="secondary"
                    className="mt-3"
                    onClick={() => uploadInputRef.current?.click()}
                    disabled={uploadSubmitting}
                  >
                    Choose file
                  </Button>
                </div>
              ) : (
                <div className={`flex items-center gap-3 rounded-lg border p-4 ${
                  uploadError ? 'border-red-200 bg-red-50/40' : 'border-emerald-200 bg-emerald-50/40'
                }`}>
                  <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${
                    uploadError ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'
                  }`}>
                    {uploadFile.name?.toLowerCase().endsWith('.xlsx')
                      ? <FileSpreadsheet className="h-5 w-5" aria-hidden="true" />
                      : <FileText className="h-5 w-5" aria-hidden="true" />}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-ink">{uploadFile.name || 'vendor-response.csv'}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {uploadFile.name?.toLowerCase().endsWith('.xlsx') ? 'Excel workbook' : 'CSV document'}
                      {uploadFileSize ? ` · ${uploadFileSize}` : ''}
                    </p>
                  </div>
                  {!uploadSubmitting && (
                    <button
                      type="button"
                      aria-label="Remove selected file"
                      onClick={removeVendorResponse}
                      className="rounded-md p-2 text-slate-500 hover:bg-white hover:text-slate-800"
                    >
                      <X className="h-4 w-4" aria-hidden="true" />
                    </button>
                  )}
                </div>
              )}
            </div>

            {uploadRows.length > 0 && (
              <Alert variant="success" title="File validated">
                {uploadRows.length} vendor response row(s) ready to import.
              </Alert>
            )}
            {uploadWorkbook && (
              <Alert variant="success" title="File ready">
                <span>{uploadWorkbook.name} is ready to import.</span>{' '}
                <span>Its contents will be validated during upload.</span>
              </Alert>
            )}
            {uploadError && <Alert variant="error">{uploadError}</Alert>}

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <h3 className="text-sm font-semibold text-ink">Before you upload</h3>
                  <ul className="mt-2 grid gap-x-6 gap-y-1.5 text-xs leading-5 text-slate-600 sm:grid-cols-2">
                    <li>Include all {uploadCandidate.recordCount} requests in this batch</li>
                    <li>Keep account numbers as text to preserve leading zeroes</li>
                    <li>Use the assigned start and stop cheque ranges</li>
                    <li>Do not rename or reorder the template columns</li>
                  </ul>
                  <details className="mt-3 text-xs text-slate-600">
                    <summary className="cursor-pointer font-semibold text-blue-700 hover:text-blue-900">
                      View all 15 required columns
                    </summary>
                    <p className="mt-2 leading-5">
                      S/N, Account Number, Account Name, Branch Name, Branch Address, Sort Code, Cheque Type,
                      No of Leaves, No of Booklets, Delivery SolID, Delivery Address, Scheme Code, Currency,
                      Range Start and Range Stop.
                    </p>
                  </details>
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>
      <Modal
        open={Boolean(allocationsBatch)}
        onClose={() => setAllocationsBatch(null)}
        title={allocationsBatch ? `Serial allocations — ${allocationsBatch.batchNumber}` : ''}
        description="Vendor ranges and their Finacle registration outcomes."
        size="xl"
        footer={<Button variant="secondary" onClick={() => setAllocationsBatch(null)}>Close</Button>}
      >
        {loadingAllocations ? <LoadingState label="Loading allocations…" /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="border-b border-slate-200">
                <tr>{['Request', 'Account', 'Type', 'Serial range', 'Vendor reference', 'Finacle status', 'Action']
                  .map((heading) => <th key={heading} className="px-3 py-2 text-xs uppercase text-slate-500">{heading}</th>)}</tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {allocations.map((row) => (
                  <tr key={row.requestId}>
                    <td className="px-3 py-3 font-semibold">{row.requestId}</td>
                    <td className="px-3 py-3 font-mono text-xs">{maskAccount(row.accountNumber)}</td>
                    <td className="px-3 py-3">{row.chequeTypeCode}</td>
                    <td className="px-3 py-3 font-mono text-xs">{row.startChequeNumber}–{row.endChequeNumber}</td>
                    <td className="px-3 py-3">{row.vendorReference}</td>
                    <td className="px-3 py-3">
                      <p className="font-semibold">{row.finacleRegistrationStatus}</p>
                      {row.failureReason && <p className="mt-1 max-w-xs text-xs text-red-600">{row.failureReason}</p>}
                    </td>
                    <td className="px-3 py-3">
                      {row.finacleRegistrationStatus === 'FAILED' && (
                        <Button variant="secondary" onClick={() => retryRegistration(row)}>
                          <RotateCcw className="h-4 w-4" /> Retry
                        </Button>
                      )}
                      {row.finacleRegistrationStatus === 'REVIEW_REQUIRED' && (
                        <Button variant="secondary" onClick={() => openReconciliation(row)}>Record review</Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
      <Modal
        open={Boolean(reconcileCandidate)}
        onClose={() => setReconcileCandidate(null)}
        title={reconcileCandidate ? `Reconcile ${reconcileCandidate.requestId}` : ''}
        description="Record an independently verified Finacle outcome."
        footer={(
          <>
            <Button variant="secondary" onClick={() => setReconcileCandidate(null)}>Cancel</Button>
            <Button onClick={reconcileRegistration}>Record reviewed outcome</Button>
          </>
        )}
      >
        <div className="space-y-4">
          <FormField label="Reviewed outcome" htmlFor="registration-outcome" required>
            <select id="registration-outcome" className={inputClass} value={reconcileOutcome}
              onChange={(event) => setReconcileOutcome(event.target.value)}>
              <option value="REGISTERED">Finacle registration succeeded</option>
              <option value="NOT_REGISTERED">Finacle registration did not occur</option>
            </select>
          </FormField>
          <FormField label="Evidence reference" htmlFor="registration-evidence" required>
            <input id="registration-evidence" className={inputClass} value={evidenceReference}
              onChange={(event) => setEvidenceReference(event.target.value)} maxLength={100} />
          </FormField>
          <FormField label="Review note" htmlFor="registration-note" required>
            <textarea id="registration-note" className={`${inputClass} min-h-28 py-3`} value={reconcileNote}
              onChange={(event) => setReconcileNote(event.target.value)} minLength={10} maxLength={1000} />
          </FormField>
          {reconcileError && <Alert variant="error">{reconcileError}</Alert>}
        </div>
      </Modal>
    </>
  );
}

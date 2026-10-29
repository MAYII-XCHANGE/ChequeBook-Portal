package com.example.chequebookrequestportal.notification;

import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.notification.dto.NotificationActionResponse;
import com.example.chequebookrequestportal.notification.dto.NotificationSummaryResponse;
import com.example.chequebookrequestportal.repository.ChequeRequestRepository;
import com.example.chequebookrequestportal.repository.VendorExportBatchRepository;
import com.example.chequebookrequestportal.domain.VendorExportBatch;
import com.example.chequebookrequestportal.export.VendorWorkflowProperties;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import com.example.chequebookrequestportal.security.SecurityService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final ChequeRequestRepository requests;
    private final VendorExportBatchRepository batches;
    private final SecurityService security;
    private final VendorWorkflowProperties vendorWorkflow;

    public NotificationService(ChequeRequestRepository requests, VendorExportBatchRepository batches,
            SecurityService security, VendorWorkflowProperties vendorWorkflow) {
        this.requests = requests;
        this.batches = batches;
        this.security = security;
        this.vendorWorkflow = vendorWorkflow;
    }

    @Transactional(readOnly = true)
    public NotificationSummaryResponse summary() {
        PortalPrincipal principal = security.principal();
        List<NotificationActionResponse> actions = new ArrayList<>();

        if (principal.roles().contains("BOM")
                && principal.permissions().contains(Permissions.APPROVE_REQUEST)
                && principal.branchCode() != null
                && !principal.branchCode().isBlank()) {
            add(actions, NotificationActionCode.APPROVAL_REQUIRED,
                    requests.countByStatusAndBranchCodeIgnoreCase(
                            RequestStatus.PENDING_APPROVAL,
                            principal.branchCode()
                    ));
            add(actions, NotificationActionCode.DEBIT_RETRY_REQUIRED,
                    requests.countByStatusAndBranchCodeIgnoreCase(
                            RequestStatus.DEBIT_FAILED,
                            principal.branchCode()
                    ));
        }

        if (principal.permissions().contains(Permissions.EXPORT_VENDOR)) {
            add(actions, NotificationActionCode.VENDOR_EXPORT_REQUIRED,
                    requests.countExportableByStatus(RequestStatus.APPROVED, DebitStatus.SUCCESSFUL));
            add(actions, NotificationActionCode.VENDOR_SEND_REQUIRED,
                    requests.countProcessingQueue(EnumSet.of(RequestStatus.EXPORTED), DebitStatus.SUCCESSFUL));
            if (principal.permissions().contains(Permissions.PROCESS_REQUEST))
                add(actions, NotificationActionCode.VENDOR_PRINT_REQUIRED,
                        batches.countByStatus(VendorExportBatch.STATUS_SENT));
        }

        if (principal.permissions().contains(Permissions.PROCESS_REQUEST)) {
            add(actions, NotificationActionCode.LIFECYCLE_UPDATE_REQUIRED,
                    requests.countProcessingQueue(lifecycleUpdateStatuses(), DebitStatus.SUCCESSFUL));
            add(actions, NotificationActionCode.REVERSAL_REVIEW_REQUIRED,
                    requests.countByStatus(RequestStatus.REVERSAL_REVIEW_REQUIRED));
        }

        if (principal.permissions().contains(Permissions.DELIVER_REQUEST)
                && principal.branchCode() != null && !principal.branchCode().isBlank()) {
            add(actions, NotificationActionCode.BRANCH_RECEIPT_REQUIRED,
                    requests.countByStatusAndBranchCodeIgnoreCase(
                            RequestStatus.DISPATCHED, principal.branchCode()));
            long completionRequired = requests.countByStatusAndBranchCodeIgnoreCase(
                    RequestStatus.RECEIVED_BY_BRANCH, principal.branchCode());
            long custodyRequired = requests.countByStatusAndBranchCodeIgnoreCaseAndChequeType(
                    RequestStatus.RECEIVED_BY_BRANCH, principal.branchCode(), ChequeType.MANAGERS_CHEQUE);
            add(actions, NotificationActionCode.DELIVERY_CONFIRMATION_REQUIRED,
                    Math.max(0, completionRequired - custodyRequired));
            add(actions, NotificationActionCode.MANAGERS_CHEQUE_CUSTODY_CONFIRMATION_REQUIRED, custodyRequired);
        }

        long totalCount = actions.stream().mapToLong(NotificationActionResponse::count).sum();
        return new NotificationSummaryResponse(totalCount, List.copyOf(actions), Instant.now());
    }

    private EnumSet<RequestStatus> lifecycleUpdateStatuses() {
        if (!vendorWorkflow.finacleRegistrationEnabled())
            return EnumSet.of(RequestStatus.PRINTED, RequestStatus.FINACLE_REGISTERED);
        return EnumSet.of(RequestStatus.FINACLE_REGISTERED, RequestStatus.FINACLE_REGISTRATION_FAILED,
                RequestStatus.FINACLE_REGISTRATION_REVIEW_REQUIRED);
    }

    private void add(List<NotificationActionResponse> actions, NotificationActionCode code, long count) {
        if (count > 0)
            actions.add(new NotificationActionResponse(code, count));
    }
}

# Chequebook Request Portal — Frontend Blueprint

## Architecture

The SPA uses feature-driven React. `app` owns routing and authentication state, `components` contains reusable UI, `features` owns workflows, `services/api` is the versioned backend adapter, and `constants` centralizes permissions and statuses.

The browser never calls Active Directory, Finacle or the printing vendor. Spring Boot owns authentication, authorization, branch scope, customer data, eligibility, charges, debits, status transitions, audit records and spreadsheet generation. Signed access and refresh tokens are stored in tab-scoped Session Storage and sent as bearer tokens; `/auth/profile` restores the authenticated user after a reload.

## Routes and permissions

| Route | Permission |
| --- | --- |
| `/login` | Public |
| `/dashboard`, `/profile`, `/unauthorized` | Authenticated |
| `/cheque-requests`, `/cheque-requests/:requestId` | `VIEW_CHEQUE_REQUEST` |
| `/cheque-requests/new` | `CREATE_CHEQUE_REQUEST` |
| `/approvals` | `APPROVE_CHEQUE_REQUEST` |
| `/processing` | `PROCESS_CHEQUE_REQUEST` |
| `/vendor-exports` | `EXPORT_VENDOR` |
| `/reports` | `VIEW_REPORTS` |
| `/audit-trail` | `VIEW_AUDIT_TRAIL` |
| `/administration` | `USER_MANAGEMENT` |

## Roles

| Capability | CSO | BOM | Operations | Auditor | Admin |
| --- | :---: | :---: | :---: | :---: | :---: |
| Create/view branch requests | Yes | View | All | No | No |
| Approve/reject, initiate debit and retry confirmed debit failures | No | Yes | No | No | No |
| Process successfully debited request lifecycle | No | No | Yes | No | No |
| Generate vendor workbook | No | No | Yes | No | No |
| Operational reports | No | Yes | Yes | Yes | No |
| Audit trail | No | No | Yes | Yes | No |
| User/role/branch management | No | No | No | No | Yes |

The backend returns each user's effective permission set and enforces the same boundary at endpoint and service layers.

## API adapters

- `authApi`: login, token refresh, logout and authenticated profile.
- `customerApi`: Finacle-backed account lookup and eligibility.
- `chequeRequestApi`: create, detail and server-filtered/paginated lists.
- `approvalApi`: pending approvals, confirmed failed-debit queue, approve/debit, reject and BOM-only debit retry.
- `processingApi`: downstream lifecycle commands only; Clearing Operations cannot retry a customer debit and there is no generic status setter.
- `exportApi`: eligible requests, immutable batches, workbook download and sent confirmation.
- `reportApi`: filtered report data and server-generated CSV.
- `auditApi`: paginated audit events.
- `adminApi`: user, role and branch CRUD/state changes plus bootstrap password change.

Errors are normalized as `{ code, message, fieldErrors, retryable }`. Pages render loading, retryable error, empty, validation, confirmation and success states.

## Lifecycle

```text
PENDING_APPROVAL
  ├─ REJECTED
  └─ DEBIT_PENDING
       ├─ DEBIT_FAILED ── BOM retry
       ├─ DEBIT_REVIEW_REQUIRED ── Operations reconcile before any retry decision
       └─ APPROVED → EXPORTED → SENT_TO_VENDOR → PRINTED
                    → DISPATCHED → RECEIVED_BY_BRANCH → DELIVERED
```

Cheque range maintenance remains in Finacle. No browser screen directly edits cheque ranges; a future Finacle maintenance API can be added behind the backend without exposing core-banking credentials to the SPA.

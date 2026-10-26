# Chequebook Request Portal Backend

## Purpose

This project is the Spring Boot backend for the Chequebook Request Portal. It replaces branch email and spreadsheet requests with a controlled, auditable maker-checker workflow.

The portal must:

- Authenticate bank staff through the bank's LDAP-backed Active Directory authorization API.
- Allow a Customer Service Officer (CSO) to initiate a chequebook request.
- Retrieve customer and account information from Finacle.
- Validate account eligibility and prevent duplicate active requests.
- Route the request to a Branch Manager/Operations Officer (BOM) for approval.
- Debit the customer in Finacle only after BOM approval.
- Allow Operations to generate the existing vendor Excel format manually.
- Track printing, dispatch, branch receipt and customer delivery.
- Provide management reports and an immutable audit trail.

The application should initially be implemented as a modular monolith. It must not be split into microservices unless scale or organizational ownership later creates a clear need.

## Implemented Deliverables

This repository now contains the working modular-monolith implementation described below:

- Stateless Spring Security with signed access/refresh bearer JWTs, CORS protection, AD-gateway authentication, database-owned authorization and login throttling.
- An idempotent, environment-provisioned bootstrap administrator with a strictly administrative permission set and no in-portal password-change capability.
- Hibernate-managed schema creation and updates, Spring Data persistence, branch scoping and optimistic locking.
- Real core-banking name-enquiry and credit-transfer clients, with no runtime dummy account or transaction responses.
- CSO creation, BOM maker-checker approval/rejection, post-approval debit, Finacle cheque-book submission and compensating reversal on cheque-request failure.
- Immutable vendor batch snapshots and `.xlsx` generation with the approved 15-column order and text-formatted identifiers.
- Operational reports, CSV download, audit history and stable API error responses.
- A read-only, role-aware pending-action summary with branch-scoped approval counts, header and sidebar badges, and workflow-triggered refreshes.
- A React/Tailwind client in `../ChqBkReq_Frontend` and an importable Postman collection in `postman/`.

Live Active Directory, core-banking, Finacle cheque-book and Oracle verification requires bank network access, credentials and test data. The local profile uses PostgreSQL and the real configured integration endpoints. Automated tests use isolated H2 data and test-only mocks; dummy responses are not available in a running application.

## Run Locally

Start PostgreSQL:

```bash
docker compose up -d
```

Supply the database, bootstrap administrator, JWT signing secret and AD gateway through the shell or your IDE secret-enabled run configuration, then start Spring Boot:

```bash
export BOOTSTRAP_ADMIN_EMAIL='admin@example.bank'
export BOOTSTRAP_ADMIN_PASSWORD='replace-with-a-strong-temporary-password'
export DB_PASSWORD='your-local-postgres-password'
export JWT_SECRET='replace-with-at-least-32-random-characters'
export AD_AUTHORIZATION_URL='https://identity.example.bank/api/authorize'
export CORE_BANKING_USERNAME='provided-by-the-bank'
export CORE_BANKING_PASSWORD='provided-by-the-bank'
export HEAD_OFFICE_CHARGE_GL_ACCOUNT='provided-head-office-charge-gl-account'
export HEAD_OFFICE_VAT_GL_ACCOUNT='provided-head-office-vat-gl-account'
export FINACLE_URL='https://finacle-fetest.novabank.ng:8443/FISERVLET/fihttp'
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080/api/v1`; health is available at `http://localhost:8080/actuator/health`. Configuration keys are documented in [.env.example](./.env.example). Do not copy real secrets into that file.

Cheque book charges are VAT exclusive. `vat.rate` defaults to `0.075`. Each branch must have distinct charge and VAT GL accounts configured in Branch Management; the system-defined head-office values come from `HEAD_OFFICE_CHARGE_GL_ACCOUNT` and `HEAD_OFFICE_VAT_GL_ACCOUNT`. A successful charge posts one balanced core-banking request with a customer debit for charge plus VAT and separate credits to the initiating branch's charge GL and VAT GL. Confirm that the bank's core-banking endpoint accepts three `part_tran` entries before using this posting in a live environment.

The local profile defaults to the PostgreSQL database and credentials created by `compose.yml`: database `chequebook_portal`, user `cheque_portal`, and password `cheque_portal`. If PostgreSQL is already running directly on your machine, configure the Spring Boot run environment with that server's real values instead:

```text
DB_URL=jdbc:postgresql://localhost:5432/your_database
DB_USERNAME=your_postgres_role
DB_PASSWORD=your_postgres_password
```

`FATAL: role "cheque_portal" does not exist` means the application reached a different PostgreSQL instance where the Compose user has not been created. Either start the supplied Compose database or set the three variables above to an existing local database and role.

Import both files from `postman/` into Postman. Select the local environment, fill in the required staff/admin emails and passwords, and run the login request for the role required by the API folder. The collection stores the returned bearer tokens automatically.

## Confirmed Business Decisions

1. Authentication uses the bank's LDAP service.
2. The request workflow uses maker-checker authorization:
   - CSO is the maker.
   - BOM is the checker.
3. Finacle will expose APIs for account lookup and eligibility validation.
4. After BOM approval, the customer account must be debited for the chequebook charge.
5. A request cannot be sent to the printing vendor until the debit succeeds.
   BOM approval performs the debit only; it does not submit a Finacle cheque-book request.
6. Operations will manually generate and download an Excel file in the vendor's existing format.
7. PostgreSQL is used locally.
8. Oracle is used in test and production.
9. Vendor printing and courier logistics are outside the portal, but their statuses are tracked by the portal.
10. The vendor maintains an independent serial-number sequence per cheque type. After printing, the vendor returns the assigned range and the portal sends its starting number to Finacle. The portal does not allocate the next serial number.
11. On first startup, the system creates one local bootstrap administrator from securely supplied email and password configuration.
12. The `ADMIN` role is restricted to user management, role management and branch management. It has no cheque-request operational permissions.
13. The system maintains Head Office as a protected system-defined branch with code `01` and always assigns the System Admin to it.
14. The initial notification feature is a derived pending-action summary; it does not persist messages or introduce read/unread state.

## End-to-End Workflow

```text
Customer requests a chequebook at a branch
        |
CSO logs in through the LDAP-backed AD authorization service
        |
CSO searches the customer's account number
        |
Backend retrieves account information from Finacle
        |
Backend validates account status, KYC, restrictions,
duplicate requests and other eligibility rules
        |
CSO submits the request
        |
PENDING_APPROVAL
        |
BOM reviews the request
        |------------------------------|
        |                              |
     Reject                          Approve
        |                              |
    REJECTED                      DEBIT_PENDING
                                       |
                         Core-banking debit transfer
                                       |
                    |------------------|------------------|
                    |                  |                  |
                 Success            Declined        Unknown/timeout
                    |                  |                  |
         Submit cheque request     DEBIT_FAILED     DEBIT_REVIEW_REQUIRED
                    |
             |------|------|
             |             |
       Host SUCCESS     Host failure
             |             |
         APPROVED      Reverse charge
             |             |
             |     |-------|-------|
             |     |               |
             | Reversal success  Failed/unknown
             |     |               |
             | CANCELLED       REVERSAL_REVIEW_REQUIRED
             |     |               |
             | Account released   Operations retries or records
             | for a new request  an evidence-backed reviewed outcome
             |
           Operations creates vendor batch
                    |
                 EXPORTED
                    |
           Operations confirms file was sent
                    |
              SENT_TO_VENDOR
                    |
       Vendor response supplies serial range
                    |
                 PRINTED
                    |
       Portal registers range in Finacle
                    |
           FINACLE_REGISTERED
                    |
       Operations marks DISPATCHED
                    |
   Servicing-branch CSO confirms receipt
                    |
          RECEIVED_BY_BRANCH
                    |
 Branch CSO confirms customer collection
                    |
                DELIVERED
```

## Business Rules

### Business identifiers

- New cheque requests use `CBR-{YEAR}-{SERVICING_BRANCH}-{PADDED_DATABASE_ID}`, for example
  `CBR-2026-002-000028`.
- New vendor exports use `VEX-{YYYYMMDD}-{PADDED_DATABASE_ID}`, for example
  `VEX-20261008-000179`.
- The database-generated suffix guarantees uniqueness and is not presented as a gap-free yearly or daily counter.
- Existing request and export identifiers remain valid and are never rewritten.

### Bootstrap administrator

- Normal bank staff authenticate through LDAP; the bootstrap administrator is the only local production account.
- On startup, the application checks for an existing bootstrap administrator.
- If none exists, it creates one from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` supplied by the deployment secret store or environment.
- The email and password must never be hardcoded in Java, configuration files committed to Git, database migrations or application logs.
- The startup operation must be idempotent: restarting the application must not create additional administrators or reset the existing password.
- The bootstrap administrator's branch is always the system-defined Head Office branch with code `01`; it cannot be selected or overridden during creation.
- Store only a strong password hash using an approved adaptive password encoder; never store the original password.
- The administrator cannot change its password through the portal UI or API.
- The initial password must meet the bank's password policy; any rotation is handled through the bank's controlled deployment or credential-management process.
- The system must prevent deletion or deactivation of the final active administrator.
- Administrative changes must create immutable audit events.

The `ADMIN` role is deliberately not a business superuser. It may only:

- View, create, update, activate and deactivate portal user profiles.
- Link portal profiles to a bank email address and employee ID.
- Assign and revoke authorized portal roles.
- View and manage role definitions and permission mappings, subject to protected system-role rules.
- View all branch records and create, update, activate and deactivate user-managed branches.

The `ADMIN` role must not:

- Create, approve, reject or process chequebook requests.
- Initiate or retry customer debits.
- Generate or download vendor files.
- Change cheque-request lifecycle statuses.
- View customer account details merely because it is an administrator.
- Access operational reports or the general audit-trail screen unless a separate, explicitly approved oversight role is assigned to a different account.
- Modify the protected `ADMIN` role to grant itself operational permissions.

### System-defined branches

- `app.system-branches.head-office.code=01` and `app.system-branches.head-office.name=Head Office` are the centralized Head Office definition.
- Startup creates Head Office if it is absent and reconciles its configured name, address, sort code, active state and `systemDefined` marker when it already exists.
- Head Office cannot be edited or deactivated through the branch-management API. Attempts return `409 SYSTEM_BRANCH_PROTECTED`.
- Restarting the application does not create a duplicate Head Office branch or a duplicate System Admin.
- Branches created through the administration API remain user-managed and retain the existing edit, activation and deactivation behavior.

### Pending-action notifications

- Notifications are calculated from current workflow state whenever the summary endpoint is called. No notification table, migration, message history or read/unread flag is created in this phase.
- Only authenticated users can retrieve a summary. Counts are permission-driven so adding an unrelated role does not expose operational work.
- A user with approval permission receives `APPROVAL_REQUIRED` for `PENDING_APPROVAL` requests in that user's branch only.
- A user with approval permission receives `DEBIT_RETRY_REQUIRED` for confirmed `DEBIT_FAILED` requests in that user's branch only.
- A user with vendor-export permission receives `VENDOR_EXPORT_REQUIRED` for approved requests that have not already been placed in an export batch. The notification count and vendor eligible-request list use the same repository predicate.
- A user with vendor-export permission receives `VENDOR_SEND_REQUIRED` for exported requests whose batch transmission still requires confirmation.
- Debit failures, unknown outcomes and reversed cheque-request failures remain outside the Clearing Operations queue.
- A user with processing permission receives `LIFECYCLE_UPDATE_REQUIRED` for Finacle registration failures/reviews and requests that are registered and ready for dispatch. Clearing Operations ends its normal custody workflow at `DISPATCHED`.
- A servicing-branch user with delivery permission receives `BRANCH_RECEIPT_REQUIRED` for `DISPATCHED` requests and `DELIVERY_CONFIRMATION_REQUIRED` for `RECEIVED_BY_BRANCH` requests.
- Admin and Auditor users receive a zero summary unless an explicit matching workflow permission is assigned later.
- The frontend polls the endpoint every 60 seconds while the browser tab is visible and refreshes immediately after approval, rejection, processing and vendor-export actions.
- Summary failures are silent and non-blocking: the last successful count remains visible and the main workflow continues to function.
- Stable action codes are mapped to labels and routes by the frontend. Backend response text is not used as a navigation contract.

### Workflow email delivery

- Workflow email is optional and is enabled with `EMAIL_ENABLED=true` after the HTTP gateway settings in `.env.example` are configured.
- The gateway URL, channel, display name and subject prefix are external configuration. The request body follows the gateway's `channel`, `subject`, `message`, `displayName`, `to`, `cc` and `attachments` contract.
- Emails queued for active Operations users CC the Clearing Operations group mailbox configured with `CLEARING_OPERATIONS_CC`. Because each Operations user receives an individual message, the group mailbox receives one copy per active Operations recipient.
- The gateway `message` value is a complete responsive HTML document rendered from `templates/email/workflow-notification.html` with Thymeleaf. Dynamic values are HTML-escaped and the template uses email-client-safe tables and inline styling.
- Messages are persisted in a `cheque_email_outbox` table in the same transaction as the workflow change, then delivered by a background worker. Gateway outages therefore do not roll back approvals, debit results or lifecycle updates.
- Temporary delivery failures are retried with backoff up to `EMAIL_MAX_ATTEMPTS`; the outbox retains `SENT`, `RETRYING` and terminal `FAILED` states for operational review.
- A deduplication key prevents the same workflow occurrence from queuing the same recipient twice.
- Emails contain only the request number, branch, masked account number and the normal portal login link. They never contain login tokens, full account numbers, GL accounts or raw core-banking responses.
- The following emails are produced:
  - `APPROVAL_REQUIRED` to active BOM users in the request branch when a CSO creates a request.
  - `OPERATIONS_EXPORT_READY` to active Operations users after debit and cheque-book creation succeed.
  - `OPERATIONS_DEBIT_REVIEW_REQUIRED` to active Operations users for an unknown debit outcome.
  - `OPERATIONS_REVERSAL_REVIEW_REQUIRED` to active Operations users for a failed or unknown reversal.
  - `BRANCH_DISPATCHED` to active CSO users in the request branch.
  - `BRANCH_RECEIVED` to active CSO users in the request branch and active Operations users after the servicing branch confirms physical receipt.
  - `ADMIN_BRANCH_MODIFIED` to active BOM users in an affected branch after branch details or status change.
  - `ADMIN_USER_MODIFIED` to active BOM users in the previous and current affected branches after a user profile, role, branch assignment or status change.
- Administration emails identify the changed fields but never include GL account values, passwords or authentication tokens.
- Confirmed `DEBIT_FAILED` requests deliberately produce no email. BOM retry reminders remain available only through the existing in-app notification and failed-debit queue.

### Request creation

- Only an authenticated user with the `CSO` role can create a request.
- The frontend sends only the account number and requested leaf count.
- Customer name, branch, eligibility and creator details must not be trusted when supplied by the browser.
- The backend derives the user and branch from the authenticated principal and staff profile.
- The backend calls Finacle and performs a fresh eligibility check during submission.
- Supported leaf counts are initially `50` and `100`.
- Number of booklets defaults to `1` until the bank confirms that multiple booklets are allowed.
- Only one active request may exist for an account at a time.
- A customer is not debited when the CSO creates the request.

### Maker-checker approval

- Only a user with the `BOM` role can approve or reject a request.
- The maker cannot approve their own request.
- A BOM may only act on requests belonging to their authorized branch unless an explicitly approved override permission exists.
- Only a request in `PENDING_APPROVAL` can be approved or rejected.
- Rejection requires a meaningful reason.
- Rejected requests are not debited.
- Every decision creates request-history and audit records.
- The same branch-scoped BOM queue exposes confirmed `DEBIT_FAILED` requests for retry, regardless of the decline reason recorded by the core-banking service.

### Customer debit

- BOM approval initiates a core-banking credit transfer that debits the customer and credits the configured cheque-charge account (`1003002112` by default).
- The backend determines the fee; the frontend must never submit or override it.
- A successful debit moves the request to `APPROVED`, making it eligible for vendor export. BOM approval does not
  call the Finacle cheque-book service.
- Finacle cheque-book registration happens only after the vendor returns and the portal persists the printed serial
  range. A registration failure does not reverse the already-successful customer debit automatically.
- A completed reversal produces `CHEQUEBOOK_REQUEST_FAILED`; a failed or unknown reversal produces `REVERSAL_REVIEW_REQUIRED` for Operations.
- Each request uses a unique idempotency key for the Finacle debit.
- A timeout is not treated as a failed debit because Finacle may have completed the transaction.
- An unknown result must be reconciled by querying Finacle with the original reference before any retry.
- Only the BOM can retry a confirmed `DEBIT_FAILED` debit. Clearing Operations cannot invoke the debit-retry endpoint.
- `DEBIT_REVIEW_REQUIRED` is deliberately excluded from retry until reconciliation establishes that no debit occurred.
- A request with an unsuccessful or unknown debit cannot be exported.
- Finacle transaction references and response codes must be stored for reconciliation.
- Full sensitive Finacle request or response payloads must not be written to application logs.

### Vendor export

- Only successfully debited, approved and unexported requests are eligible.
- The CSO must select and store one supported cheque type when creating the request: `01` (Individual Current
  Account) or `02` (Corporate Account). The portal does not infer this value from Finacle account ownership.
- Manager's cheque type `03` is not supported in the current workflow.
- Legacy requests may have a null cheque type after migration. They must be reviewed and backfilled before vendor
  export; otherwise export is blocked with `CHEQUE_TYPE_REQUIRED`.
- Operations selects one or more eligible requests and generates an Excel batch.
- The generated workbook and returned vendor response must use this column order. `Range Start` and `Range Stop`
  are blank in the generated workbook and are completed by the vendor after printing:

```text
S/N
Account Number
Account Name
Branch Name
Branch Address
Sort Code
Cheque Type
No of Leaves
No of Booklets
Delivery SolID
Delivery Address
Scheme Code
Currency
Range Start
Range Stop
```

- Identifier fields must be written as Excel text so leading zeroes are preserved.
- Vendor fields are produced by a dedicated mapping component, not by directly serializing database entities.
- The exact exported values are stored as an immutable batch snapshot.
- Generating or downloading a file sets the batch to `EXPORTED`; it does not prove transmission.
- An authorized Operations user separately confirms that the file was sent, moving the requests to `SENT_TO_VENDOR`.
- The vendor maintains a separate serial-number sequence for each cheque type and returns the actual starting and
  ending numbers after printing. The portal validates and stores that range; it never calculates the next number.
- The vendor response must contain every request in the batch. `S/N` maps each response row to its stored batch
  snapshot, and all returned account, branch, cheque, delivery, scheme and currency values must match that snapshot.
- Operations may upload the completed vendor response as either `.csv` or `.xlsx`; both formats use the same exact
  15-column contract and preserve displayed leading zeroes.
- Ranges cannot overlap within the same cheque type. Identical numeric ranges may exist across different cheque types.
- Finacle `ChkbkAdd` is called only after the vendor range is persisted, using the vendor-provided starting number.
- A request cannot be dispatched until its Finacle registration is confirmed as `FINACLE_REGISTERED`.
- Each export file must have a stored checksum, filename, creator, creation time and row count.

## Suggested Package Structure

```text
src/main/java/com/example/chequebookrequestportal
├── ChequebookrequestportalApplication.java
├── config
│   ├── CacheConfig.java
│   ├── ControlUserSeeder.java
│   ├── CorsConfig.java
│   ├── JwtTokenFilter.java
│   ├── RequestResponseLoggingFilter.java
│   ├── RestClientConfig.java
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
├── auth
│   ├── AdAuthenticationClient.java
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── JwtTokenService.java
│   ├── LoginAttemptService.java
│   ├── UserDetailsServiceImpl.java
│   └── dto
├── administration
│   ├── bootstrap
│   ├── controller
│   ├── dto
│   └── service
├── branch
├── customer
│   ├── controller
│   ├── dto
│   └── service
├── request
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   └── service
├── approval
├── charge
├── processing
├── export
│   ├── controller
│   ├── dto
│   ├── excel
│   ├── model
│   ├── repository
│   └── service
├── report
├── audit
├── corebanking
│   ├── client
│   ├── dto
│   └── service
├── request
│   ├── ChequeBookRequest.java
│   └── ChequeBookResponse.java
├── service
│   └── ChequeBookService.java
└── common
    ├── exception
    ├── response
    ├── security
    └── validation
```

Controllers should contain HTTP concerns only. Business rules belong in application/domain services, while repositories only handle persistence.

## Authentication and Authorization

### Active Directory and local administrator

Normal bank users must first have an active portal profile. At login, the backend posts the normalized email and Base64-encoded password to the bank's AD authorization endpoint, following the referenced authentication implementation. This transport must use HTTPS; Base64 is encoding, not encryption. The portal never persists the directory password. The bootstrap administrator is the only locally authenticated account and uses BCrypt.

LDAP should provide, where available:

- Employee ID
- Full name
- Email address
- Group membership

Application-specific roles, permissions, active status and branch scope remain authoritative in the portal database. They are rebuilt from the database for every authenticated request rather than trusted from browser storage or stale token claims.

The public login endpoint has two rate-limit layers. The existing normalized-username limiter protects individual
accounts, while an IP limiter counts malformed, unauthorized and forbidden login responses from the direct
connection address. Three such failures within one minute block that address for 10 minutes by default. A blocked
request returns HTTP `429`, error code `LOGIN_IP_RATE_LIMITED`, and a standards-based `Retry-After` header. The
IP limiter intentionally does not trust caller-supplied forwarding headers; deployments behind a reverse proxy must
preserve the real connection address through trusted container or network configuration. The in-memory counters are
per application instance, so a multi-instance production deployment should replace them with a shared rate-limit
store or enforce an equivalent rule at the trusted gateway.

Production settings are configurable with `LOGIN_IP_RATE_LIMIT_MAX_FAILURES`, `LOGIN_IP_RATE_LIMIT_WINDOW`,
`LOGIN_IP_RATE_LIMIT_BLOCK`, and `LOGIN_IP_RATE_LIMIT_MAX_TRACKED`.

Recommended profile record:

```text
cheque_staff_users
- id
- employee_id
- email
- auth_source: LDAP or LOCAL_ADMIN
- password_hash: nullable and populated only for LOCAL_ADMIN
- password_change_required
- full_name
- branch_code
- active
- created_at
- updated_at
```

```text
cheque_roles
- id
- code: CSO, BOM, OPERATIONS, AUDITOR, ADMIN
```

```text
cheque_staff_roles
- staff_id
- role_id
```

The application does not create demo staff users and has no mock, bypass or registration endpoint. Administrators create LDAP-linked portal profiles before those staff members can sign in.

The protected `ADMIN` role has only these permission groups:

```text
USER_MANAGEMENT
ROLE_MANAGEMENT
BRANCH_MANAGEMENT
```

Operational roles and permissions are separate. Assigning `ADMIN` must not implicitly grant request, approval, debit, processing, export, reporting or audit permissions.

Access and refresh JWTs carry subject, role, token version, token type, issued-at and expiry claims and are HMAC-SHA256 signed. They are returned in the response and sent in the `Authorization: Bearer` header. The React client keeps them in tab-scoped Session Storage and removes them on logout; logout also increments the persisted token version, invalidating previously issued tokens.

## Core-Banking and Cheque-Book Integration Boundaries

Runtime account lookup and money movement use the real `corebanking` package. Name enquiry posts a generated 30-digit `tran_ref` and `acct_no` to `/nameenquiry`. Credit transfer posts the configured transaction fields and two `part_tran` entries: debit (`D`) and credit (`C`). A transfer succeeds only for status type `SUCC` and code `00`.

```java
public interface CoreBankingService {
    NameEnquiryResponse nameEnquiry(String accountNumber);
    InternalTransferResponse transfer(InternalTransferRequest request);
}
```

The account response should contain the fields required by the UI and vendor export, including:

- Account number and account name
- Account type and status
- KYC status and restrictions
- Scheme code
- Currency
- Branch/SOL ID, branch name and branch address
- Sort code
- Eligibility outcome and reason

Before every name enquiry, the backend performs a read-only cheque-eligibility lookup against Finacle using a
dedicated datasource configured under `finacle.spring.datasource.*`:

```sql
SELECT chq_alwd_flg
FROM TBAADM.GAM
WHERE foracid = :accountNumber
```

`Y` permits name enquiry, `N` returns `ACCOUNT_NOT_CHEQUE_ENABLED`, and a missing row returns
`ACCOUNT_NOT_FOUND`. An unavailable datasource fails closed with
`CHEQUE_ELIGIBILITY_UNAVAILABLE`. A null or unexpected flag returns `CHEQUE_ELIGIBILITY_REVIEW_REQUIRED` and
queues an Operations alert. The production Finacle connection can be configured with:

```text
FINACLE_DB_URL=jdbc:oracle:thin:@//finacle-host:1521/service-name
FINACLE_DB_USERNAME=read-only-portal-user
FINACLE_DB_PASSWORD=secret-store-value
FINACLE_DB_DRIVER=oracle.jdbc.OracleDriver
```

The Finacle database user must have read access to `TBAADM.GAM` (preferably limited to the required column/table
or an approved view). The portal's normal JPA operations continue to use `spring.datasource.*`; only this lookup
uses `finacle.spring.datasource.*`.
The application never substitutes the account number into SQL; it is supplied as the named bind parameter
`accountNumber`.

The cheque-book service converts the approved request into a Finacle `ChkbkAdd` FIXML message. Its request body contains:

```xml
<ChkbkAddRq>
  <LeavesPerBk>100</LeavesPerBk>
  <AckObtainedFlg>Y</AckObtainedFlg>
  <AcctId>1007000879</AcctId>
  <InvtReqd>N</InvtReqd>
  <TotNumOfLeaves>100</TotNumOfLeaves>
  <BeginChqNum>001001</BeginChqNum>
  <SolId>001</SolId>
</ChkbkAddRq>
```

`LeavesPerBk` and `TotNumOfLeaves` come from the approved request. `SolId` is the initiating branch code normalized to three digits. `BeginChqNum` is the starting number returned by the vendor for that request and retains leading zeroes. `AckObtainedFlg` is always `Y`, while the inventory flag remains configurable. The legacy shared counter is not used for new vendor-print registrations.

## Request and Transaction Statuses

```java
public enum RequestStatus {
    PENDING_APPROVAL,
    DEBIT_PENDING,
    DEBIT_FAILED,
    DEBIT_REVIEW_REQUIRED,
    CHEQUEBOOK_REQUEST_FAILED,
    REVERSAL_REVIEW_REQUIRED,
    APPROVED,
    REJECTED,
    EXPORTED,
    SENT_TO_VENDOR,
    PRINTED,
    FINACLE_REGISTRATION_PENDING,
    FINACLE_REGISTERED,
    FINACLE_REGISTRATION_FAILED,
    FINACLE_REGISTRATION_REVIEW_REQUIRED,
    DISPATCHED,
    RECEIVED_BY_BRANCH,
    DELIVERED,
    CANCELLED
}
```

```java
public enum DebitStatus {
    NOT_INITIATED,
    PENDING,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
    REVERSAL_PENDING,
    REVERSED,
    REVERSAL_FAILED,
    REVERSAL_UNKNOWN
}
```

Status changes must be implemented through explicit domain operations. Do not expose a generic endpoint that allows the frontend to assign any status.

## Core Persistence Model

### `cheque_requests`

```text
id                       UUID primary key
request_number           unique business identifier
account_number           protected customer identifier
account_name             snapshot from Finacle
account_type             snapshot from Finacle
scheme_code              snapshot from Finacle
currency                 snapshot from Finacle
branch_code              requesting branch
leaf_count
booklet_count
status
created_by               staff profile ID
approved_by              nullable staff profile ID
approved_at              nullable timestamp
rejection_reason         nullable
active_marker            signed duplicate-prevention marker (1 active; negative request ID when terminal)
version                  optimistic-lock version
created_at
updated_at
```

### `cheque_eligibility_results`

```text
id
request_id
eligible
account_status
kyc_status
restriction_result
duplicate_result
debit_eligible
reason
finacle_reference
checked_at
```

### `cheque_approval_decisions`

```text
id
request_id
decision
decided_by
reason
created_at
```

### `cheque_charge_transactions`

```text
id
request_id
account_number
charge_amount
currency
debit_status
idempotency_key
finacle_transaction_reference
finacle_response_code
failure_reason
initiated_by
initiated_at
completed_at
retry_count
version
```

### `cheque_request_history`

```text
id
request_id
from_status
to_status
performed_by
note
created_at
```

### `cheque_vendor_export_batches`

```text
id
batch_number
file_name
file_checksum
record_count
status
generated_by
generated_at
sent_by
sent_at
```

### `cheque_vendor_export_items`

```text
id
batch_id
request_id
account_number
account_name
branch_name
branch_address
sort_code
cheque_type_code
leaf_count
booklet_count
delivery_sol_id
delivery_address
scheme_code
currency
```

### `cheque_audit_events`

```text
id
chain_sequence
occurred_at
actor_id
actor_username
actor
actor_role
action
entity_type
entity_id
description
ip_address
correlation_id
previous_hash
event_hash
integrity_key_id
created_at
```

New audit records are append-only and form an ordered HMAC-SHA-256 hash chain. Each record signs its security-relevant
fields and the previous record's hash, so modification, deletion and reordering are detectable. Records created before
the integrity migration remain visible as legacy records but cannot be retroactively treated as cryptographically
protected.

The singleton `cheque_audit_chain_state` table stores the current chain sequence and head hash. The runtime database role may
insert audit events and update this chain head, but may not update or delete audit events. Apply the migration matching
the target database before starting a production deployment:

```text
docs/database/postgresql/V20261008__tamper_evident_audit.sql
docs/database/oracle/V20261008__tamper_evident_audit.sql
```

Set `AUDIT_INTEGRITY_KEY` from the production secret store to a randomly generated value of at least 32 bytes and keep
it stable. `AUDIT_INTEGRITY_KEY_ID` identifies that key. Never store the production key in source control or in the
database containing the audit records. Changing or losing the key without a controlled key-rotation migration makes
existing protected records unverifiable.

Authorized users can run `GET /api/v1/audit-events/integrity` or use **Verify integrity** on the Audit trail page. The
application also emits the latest sequence and event hash to the `AUDIT_INTEGRITY` logger after each committed audit
record. Production operations should ship that logger to a separately administered, append-only SIEM or WORM store;
the database hash chain is tamper-evident, not an absolute guarantee against an attacker controlling the application,
database and logging destination together.

## API Contract

All endpoints are versioned under `/api/v1`.

### Authentication

```text
POST /auth/login                 LDAP staff or local bootstrap administrator
POST /auth/mfa/entrust/verify    complete an AD-authenticated Entrust challenge
POST /auth/refresh-token         rotate access and refresh tokens
POST /auth/logout
GET  /auth/profile
```

### Administration

All routes below require the corresponding administrative permission. They do not grant access to cheque-request operations.

```text
GET    /admin/users
POST   /admin/users
GET    /admin/users/{userId}
PUT    /admin/users/{userId}
POST   /admin/users/{userId}/activate
POST   /admin/users/{userId}/deactivate
PUT    /admin/users/{userId}/roles

GET    /admin/roles
POST   /admin/roles
PUT    /admin/roles/{roleId}
PUT    /admin/roles/{roleId}/permissions

GET    /admin/branches
POST   /admin/branches
PUT    /admin/branches/{branchId}
POST   /admin/branches/{branchId}/activate
POST   /admin/branches/{branchId}/deactivate

POST   /admin/account/change-password
```

The password endpoint is retained for client compatibility but returns `403 PASSWORD_CHANGE_NOT_ALLOWED`; the System Admin password is not changeable through the application.

Creating a portal user does not create an LDAP account. It creates the portal profile that links an existing bank employee email address and employee ID to a branch and application roles. The email address is also the user's LDAP login identifier for this portal.

### Customer and eligibility

```text
GET /accounts/{accountNumber}/eligibility
```

### Requests

```text
POST /cheque-requests
GET  /cheque-requests
GET  /cheque-requests/{requestNumber}
```

Example creation request:

```json
{
  "accountNumber": "0078901234",
  "leafCount": 50
}
```

There are no runtime test accounts or simulated core-banking responses. Every valid 10-digit account number is sent to the configured name-enquiry endpoint. The initiating user's assigned branch supplies branch metadata only when the actual name-enquiry response does not include it; if the response supplies a different SOL, the request is rejected as a branch-scope violation.

List filtering should support `search`, `status`, `branch`, `from`, `to`, `page`, `pageSize` and `sort`. Branch scope must be imposed by the backend, not accepted from the browser as proof of authorization.

### Approval and debit

```text
GET  /approvals/pending
GET  /approvals/debit-failures
POST /cheque-requests/{requestNumber}/approve
POST /cheque-requests/{requestNumber}/reject
POST /cheque-requests/{requestNumber}/debit/reconcile
POST /cheque-requests/{requestNumber}/debit/retry
POST /cheque-requests/{requestNumber}/reversal/retry
POST /cheque-requests/{requestNumber}/reversal/reconcile
```

Confirmed debit retry is a branch-scoped BOM action requiring `APPROVE_CHEQUE_REQUEST`. Reconciliation of an unknown outcome remains a separately permissioned Operations action and must establish the original transaction result before any retry. Neither endpoint permits a blind duplicate debit.

An Operations reversal reconciliation accepts `REVERSED` or `NOT_REVERSED` together with an independently verified
evidence reference and review note. `REVERSED` cancels the old request and releases its active-account marker;
`NOT_REVERSED` makes the existing reversal retryable. A request with an unresolved cheque-host outcome cannot be
released through reversal reconciliation.

### Vendor export and processing

```text
GET  /vendor-exports/eligible-requests
POST /vendor-exports
GET  /vendor-exports
GET  /vendor-exports/{batchNumber}
GET  /vendor-exports/{batchNumber}/download
POST /vendor-exports/{batchNumber}/confirm-sent

POST /cheque-requests/{requestNumber}/mark-printed
POST /cheque-requests/{requestNumber}/dispatch
POST /cheque-requests/{requestNumber}/receive-at-branch
POST /cheque-requests/{requestNumber}/deliver
```

Printing and dispatch require `PROCESS_CHEQUE_REQUEST`. Branch receipt and customer delivery require
`DELIVER_CHEQUE_REQUEST` and are restricted to the request's servicing branch.

### Reporting and audit

```text
GET /reports/requests
GET /reports/requests/export
GET /audit-events
GET /audit-events/integrity
```

### Pending-action summary

```text
GET /notifications/summary
```

The endpoint is read-only and derives a role- and branch-aware summary from existing request and export records. Zero-count actions are omitted from `actions`; `totalCount` is always present.

```json
{
  "totalCount": 3,
  "actions": [
    {
      "code": "APPROVAL_REQUIRED",
      "count": 3
    }
  ],
  "generatedAt": "2026-09-14T08:00:00Z"
}
```

Supported action codes and frontend destinations:

| Action code | Predicate | Destination |
|---|---|---|
| `APPROVAL_REQUIRED` | Branch-scoped `PENDING_APPROVAL` requests | `/approvals` |
| `DEBIT_RETRY_REQUIRED` | Branch-scoped confirmed `DEBIT_FAILED` requests | `/approvals` |
| `VENDOR_EXPORT_REQUIRED` | `APPROVED` requests absent from an export batch | `/vendor-exports` |
| `VENDOR_SEND_REQUIRED` | `EXPORTED` requests awaiting send confirmation | `/vendor-exports` |
| `VENDOR_PRINT_REQUIRED` | Sent batches awaiting the vendor print-response upload | `/vendor-exports` |
| `LIFECYCLE_UPDATE_REQUIRED` | Finacle registration attention or registered requests ready for dispatch | `/processing` |
| `BRANCH_RECEIPT_REQUIRED` | Branch-scoped `DISPATCHED` requests | `/cheque-requests` |
| `DELIVERY_CONFIRMATION_REQUIRED` | Branch-scoped `RECEIVED_BY_BRANCH` requests | `/cheque-requests` |

### Error format

Return a stable error structure compatible with the frontend:

```json
{
  "code": "DUPLICATE_REQUEST",
  "message": "An active chequebook request already exists for this account.",
  "fieldErrors": {},
  "retryable": false,
  "correlationId": "generated-correlation-id"
}
```

## Database and Environment Profiles

```text
application.properties
application-local.properties       PostgreSQL and real configured integrations
application-test.properties        Oracle test and bank test endpoints
application-staging.properties     Oracle staging and staging integrations
application-prod.properties        Oracle production and production integrations
```

Database requirements:

- Use Spring Data JPA for persistence.
- Hibernate validates both local and production schemas (`spring.jpa.hibernate.ddl-auto=validate`); reviewed SQL
  migrations are applied manually before deployment.
- Avoid PostgreSQL-only types such as `jsonb` for core records.
- Use explicit sequences or an agreed portable identifier strategy.
- Use optimistic locking on requests, debit transactions and export batches.
- Run integration tests against both PostgreSQL and Oracle before release.

### Manual `cheque_` table-prefix migration

Flyway is not required. Stop every application instance, take a database backup, and manually execute the script for
the target database before deploying the entity-mapping update:

```text
docs/database/postgresql/V20261013__prefix_tables_with_cheque.sql
docs/database/oracle/V20261013__prefix_tables_with_cheque.sql
```

Apply any older dated schema scripts first because they intentionally reference the pre-prefix table names. The rename
script changes table names in place and preserves rows, foreign keys, indexes, constraints and grants. Tables already
starting with `cheque_`, the shared `portal_seq` sequence, and infrastructure metadata such as
`flyway_schema_history` are unchanged. Hibernate `validate` then prevents the application from starting against a
partially migrated schema.

Secrets such as bootstrap administrator credentials, JWT signing keys, database passwords and Finacle credentials must come from the deployment secret store or environment, never source control.

## Reliability and Security Requirements

- Validate every incoming DTO with Jakarta Bean Validation.
- Enforce role and branch authorization in the service layer as well as endpoint security.
- Mask account numbers in normal list and audit responses.
- Never log passwords, full account details, LDAP credentials or sensitive Finacle payloads.
- Add correlation IDs to API calls and integration logs.
- Restrict local-password verification to the single `LOCAL_ADMIN` authentication source; all LDAP-linked staff must pass the AD gateway.
- Rate-limit and monitor failed login attempts by both normalized username and direct connection address.
- Deny System Admin password changes through both the frontend and backend; credential rotation is an external controlled operation.
- Enforce the `ADMIN` permission boundary in backend authorization; hiding frontend menu items is not sufficient.
- Use timeouts and controlled retries only where the downstream contract makes the operation safe.
- Do not retry an unknown debit or reversal until the bank supplies a transaction-status enquiry and the original result is reconciled.
- Persist state changes and audit records in the same local database transaction.
- Keep audit-event tables append-only for the runtime database role and periodically verify the cryptographic chain.
- Forward `AUDIT_INTEGRITY` anchors and application security logs to a separately administered append-only log store.
- Protect Excel exports as sensitive customer data.
- Record every export generation and download.
- Limit Actuator exposure and protect operational endpoints.
- Meet the BRD targets of account lookup within 3 seconds, request submission within 5 seconds and status search within 3 seconds where dependent systems permit.

## Testing Expectations

### Unit tests

- Maker cannot approve their own request.
- BOM cannot approve a request outside their branch.
- Rejected requests are never debited.
- Approved requests do not become exportable until debit success.
- Invalid lifecycle transitions are rejected.
- Duplicate active requests are prevented.
- Vendor field mappings produce the expected values.
- The bootstrap administrator is created once and is not recreated or reset on restart.
- The bootstrap password is hashed and cannot be changed through the portal.
- `ADMIN` can manage users, roles and branches.
- `ADMIN` is denied access to request creation, approvals, debits, processing, vendor exports, operational reports and customer data.
- The final active administrator cannot be disabled or deleted.

### Integration tests

- AD-gateway authentication and portal profile/authorization mapping.
- Local bootstrap administrator authentication is isolated from LDAP staff authentication.
- Every administrative change produces an audit event.
- PostgreSQL and Oracle persistence behaviour.
- Core-banking name-enquiry and credit-transfer request/response contracts.
- Cheque-book request field casing/types and host-status response handling.
- Successful, declined, failed and timed-out debit scenarios.
- Debit-before-cheque ordering, successful compensation and failed/unknown reversal handling.
- Concurrent BOM approval attempts.
- Excel export column order, text formatting and leading zero preservation.
- Branch-level row filtering and endpoint authorization.
- Role-aware notification counts, BOM branch scoping, exclusion of exported requests and proof that summary reads create no workflow or audit records.

### End-to-end tests

- CSO creates, BOM approves, core banking debits, the host accepts the cheque request and Operations exports.
- BOM rejects and no debit occurs.
- Debit fails and request is not exported.
- Cheque request fails after debit and the customer charge is reversed.
- Vendor and delivery statuses progress sequentially.
- Header and authorized sidebar badges reflect the pending-action summary, navigate to the correct queue and tolerate summary endpoint failures.

## Definition of Done

The initial backend is complete when:

- LDAP authentication works in the test environment.
- A single bootstrap administrator is created securely and idempotently from deployment secrets.
- The administrator is restricted to user, role and branch management.
- Local-password authentication is restricted to the one bootstrap administrator; staff authentication always uses the AD gateway.
- Account lookup and transfers are integrated through the real `CoreBankingService` boundary.
- CSO/BOM maker-checker and branch restrictions are enforced server-side.
- Debit status and the overall workflow status are recorded; cheque-host and reversal references are not persisted.
- Only successfully debited requests can be exported.
- The generated Excel file matches the approved vendor format.
- Request tracking, audit history and reports are available.
- PostgreSQL and Oracle integration tests pass.
- Sensitive data is masked and secrets are externalized.

## Items Requiring Bank Confirmation

These values must be configurable until formally confirmed:

1. Charge for each leaf count and whether charges vary by account scheme.
2. Final production charge values and whether core banking or the portal calculates the fee.
3. Final core-banking transaction codes and narration format.
4. A transaction-status enquiry endpoint for safely resolving unknown debit or reversal outcomes.
5. Final Finacle validation/mapping rules for the user-selected `ChequeType`, support for Manager's cheque `03`,
   and the source/mapping rules for `SchemeCode`.
6. Vendor sort-code transformation rule.
7. Delivery SOL and delivery-address rules.
8. Whether more than one booklet may be requested.
9. Vendor filename convention and batch-size limit.
10. Vendor acknowledgement-file format and ingestion process.
11. AD authorization endpoint URL, request/response contract, availability requirements and whether its email identifier is always lowercase.
12. Required retention period for requests, audit events, debit records and exported files.

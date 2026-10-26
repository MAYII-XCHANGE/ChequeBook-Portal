# Bank configuration still required

The implemented local workflow uses safe placeholders for facts the bank must confirm before staging:

1. Charge source and amount per leaf count/account scheme.
2. Finacle transaction code, narration, authentication and exact request/response schemas.
3. Debit retry, reconciliation, reversal and cancellation rules.
4. Vendor mappings for `ChequeType`, `SchemeCode`, sort code, delivery SOL/address and filename.
5. Whether multiple booklets are permitted and the vendor batch-size limit.
6. Vendor acknowledgement ingestion and any encryption/secure-transfer requirements.
7. LDAP base/search attributes, service account, group mapping, MFA/SSO and session policy.
8. Oracle connection/schema conventions and the bank's release migration procedure.
9. Customer-delivery evidence and retention requirements.
10. Masking, audit visibility and retention periods for database rows and generated files.
11. Whether the portal should only display cheque-range status from Finacle or later invoke an approved Finacle maintenance API.

These are configuration/integration decisions, not frontend-owned behavior. The canonical business and API contract is the backend `README.md`.

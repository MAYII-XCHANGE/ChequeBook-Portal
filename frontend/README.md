# NOVA Bank Chequebook Request Portal Frontend

React, Vite and Tailwind frontend for the implemented Spring Boot chequebook workflow. The UI uses the backend as the authority for authentication, permissions, branch scope, Finacle eligibility, customer debit, lifecycle transitions and vendor exports.

## Included workflows

- LDAP-backed AD login with stateless access/refresh bearer JWTs and a separately managed local System Admin account.
- Permission-aware CSO, BOM, Operations, Auditor and restricted Admin workspaces.
- Core-banking-backed account lookup, eligibility feedback and validated 50/100-leaf requests.
- Maker-checker approval, with customer debit followed by cheque-book host submission.
- Debit, cheque-host failure, compensating reversal and review states with explicit processing actions.
- Vendor request selection, Excel generation/download and separate sent confirmation.
- Server-side request filtering/pagination, reports/CSV export and audit views.
- User, role and branch create/edit/activate/deactivate controls.
- Responsive navigation plus loading, empty, error, validation and success states.

## Configuration

The Axios client calls the same-origin API path:

```text
/api/v1
```

During local development, Vite proxies that path to `http://10.0.240.73:8083`. The deployed web server should route `/api` to the Spring Boot service.

## Run locally

Start the backend first, then:

```bash
npm install
npm run dev
```

Open `http://localhost:5173` and sign in with the email for an enabled portal profile and the corresponding bank directory password. No demo staff accounts or credentials are built into the frontend. The one bootstrap administrator uses credentials supplied to the backend at deployment. Its password cannot be changed through the portal UI; rotation belongs to the bank's controlled deployment or credential-management process.

The frontend stores access and refresh tokens in tab-scoped `sessionStorage`, sends them through the `Authorization: Bearer` header, restores the user through `GET /auth/profile`, and automatically attempts `POST /auth/refresh-token` after an expired access token. Logout removes both tokens.

The runnable frontend always calls the backend API. Mock account and workflow data are restricted to the automated test environment and cannot be enabled in development, staging or production builds.

## Verification

```bash
npm test
npm run build
```

Frontend architecture notes remain in [docs/frontend-blueprint.md](./docs/frontend-blueprint.md). The backend contract and business rules are documented in [the backend README](../chequebookrequestportal/README.md).

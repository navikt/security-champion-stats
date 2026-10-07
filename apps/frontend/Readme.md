# Frontend application for Security champions stats.

## Overview
The frontend application is built using React, TypeScript, and Next.js. Employees can enroll in the
program and view participant names and teams. AppSec administrators can manage participant status,
correct season scores, schedule or start a new season, map Slack accounts, and choose eligible Delta
categories or single owner-confirmed Delta event UUIDs. Delta registration sync is disabled until
production access and eligible public event identifiers are confirmed.
API proxies preserve backend status, response body, and content type; proxy-local errors use RFC 9457 Problem Details.
The admin program dashboard summarizes active participants and season activity, shows Slack/Delta/GitHub sync
health, and lets administrators trigger enabled syncs.
The home page shows active participants their current-season points, level progress, and rank, followed by
the full current-season leaderboard. Administrators can view the leaderboard; backend authorization
enforces score access.
The user interface is English-only.
The sidebar contains shared navigation, an admin-only Administration section, and theme controls with
the signed-in user's initials at the bottom.
The optional Hacker theme adds a Matrix-inspired overview and shell; its rain, CRT, and phosphor
preferences are available only while Hacker is selected.
Event listings include cached playbook events, with date-only ranges shown without invented times.
Delta and manual events take priority over same-day internal playbook entries; unrelated external entries remain visible.
Participants can leave and rejoin without losing credits, unless an administrator has deactivated them.
My history shows membership and score changes from rollout; administrators can search the audit trail.
Audit capture is best-effort. Operational records expire after 12 months; participant history is erased on permanent deletion.

## Getting Started
To get started with the frontend application, follow these steps: 
1. Install dependencies: `pnpm install` (make sure you have pnpm installed globally, avoid using npm)
2. Run `pnpm run dev:local` together with the backend for a full local environment.
3. Open your browser and navigate to `http://localhost:3000` to see the application in action.
4. Run tests with `pnpm run test`.

## Technologies Used
- React: A JavaScript library for building user interfaces.
- TypeScript: A typed superset of JavaScript that compiles to plain JavaScript.
- Next.js: A React framework using the App Router for server-side rendering and routing.
- Tailwind CSS: A utility-first CSS framework for styling the application.
- NAV Aksel (`@navikt/ds-react`): NAV's design system component library.
- chart.js / react-chartjs-2: For rendering statistics charts.
- Vitest: A fast unit test runner.
- Grafana Faro: Web observability and tracing.

## Folder Structure
- `app/`: Main Next.js App Router directory.
  - `api/`: Next.js API routes (proxied calls to the backend).
  - `shared/`: Shared components, hooks, utilities, and theme.
  - `style/`: Global styles.
  - `utils/`: App-level utility functions.
- `instrumentation/`: OpenTelemetry / Grafana Faro instrumentation setup.
- `app/view/appsec/scoring/`: Season score corrections and reset controls.
- `app/view/appsec/dashboard/`: Aggregate program metrics and integration status.
- `app/view/appsec/slack/`: Slack account mappings and unmapped author review.
- `app/view/appsec/delta/`: Eligible Delta categories and single Delta event mappings.

## Contributing
Contributions to the frontend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix, following the naming convention `feature/your-feature-name` or `bugfix/your-bug-fix-name`.
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.

If you have any questions or need help, feel free to reach out to the appsec team!
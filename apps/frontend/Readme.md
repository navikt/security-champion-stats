# Frontend application for Security champions stats.

## Overview
The frontend application is built using React, TypeScript, and Next.js. Employees can enroll in the
program and view participant names and teams. AppSec administrators can manage participant status,
correct season scores, schedule or start a new season, map Slack accounts, and choose eligible Delta
categories or single owner-confirmed Delta event UUIDs. Delta registration sync is disabled until
production access and eligible public event identifiers are confirmed.
API proxies preserve backend status, response body, and content type; proxy-local errors use RFC 9457 Problem Details.
The scoring dashboard (`/appsec/scoring`) lets administrators add, rename, and remove tiers, set minimum
point thresholds, and configure points for the five existing activity types. Participant progress and
leaderboards use the saved tiers, including in the Hacker theme, where the highest tier in a multi-tier
progression receives top clearance. Preview and confirm changes with a reason.
Activity values normally affect future credits; opt into current-season retroactive changes to append
adjustments without rewriting original credits, manual corrections, or closed seasons. Zero-point
activities are still recorded. If a preview becomes stale, reload configuration and preview again.
The admin program dashboard summarizes participation and points by activity, highlights items needing attention,
shows weekly points and Slack/Delta/GitHub sync health, and lets administrators trigger enabled syncs.
The Slack administration page (`/appsec/slack`) also manages group membership operations.
All operations go through authenticated frontend API proxies; no manually obtained backend token is needed.
It shows enabled/dry-run configuration, previews group additions/removals and unresolved participants,
and lets administrators save verified Slack account mappings for participants who have not posted messages.
Preview is read-only and available while sync is disabled once backend Slack destinations are configured.
Manual write-enabled sync requires a resolved preview and confirmation; queued sync outcomes appear in the audit trail.
The frontend submits the reviewed preview version. The backend rejects stale previews with HTTP 409;
refresh operations and preview again if enrollment, account mappings or Slack membership changed.
Use **Refresh operations** to reload outstanding announcements. Only uncertain deliveries have retry/suppress controls,
and both require confirmation after checking Slack. Retrying authorizes a later sync and may produce duplicate messages.
Membership sync remains disabled by default; follow the backend README cutover procedure before enabling writes.
The overview brings together membership status, season points, level, rank, recent activity, events, and the
current-season leaderboard. Administrators can view the leaderboard; backend authorization enforces score access.
The user interface is English-only.
Active participants can use **Event claims** (`/event-claims`) to submit internal or external events
they substantively organized or presented at, add co-hosts/presenters, and provide advance network
invitation evidence. Submit after the event, within its season and after contributors enrolled.
The submitter can revise pending or rejected claims; event dates and existing event identity stay fixed.
Administrators use **Review event claims** (`/appsec/event-claims`) to approve or reject each contributor
with a reason; their own credit requires another administrator. Each approval grants the full configured
contribution value in the event's season and the first approval publishes the event in Past events.
Approved claims are locked; administrators can revoke credit with a reason and confirmation without
removing the public event. Review history remains visible to contributors and administrators.
The sidebar contains shared navigation, an admin-only Administration section, and theme controls with
the signed-in user's initials at the bottom.
The favicon is a locally bundled copy of the Security Champion logo from
https://sikkerhet.nav.no/img/SecurityChampion.svg (`src/app/icon.svg`).
The optional Hacker theme adds a Matrix-inspired overview and shell; its rain, CRT, and phosphor
preferences are available only while Hacker is selected.
Event listings include cached playbook events, with date-only ranges shown without invented times.
Delta and manual events take priority over same-day internal playbook entries; unrelated external entries remain visible.
Participants can leave and rejoin without losing credits, unless an administrator has deactivated them.
My history summarizes season points and provides filters for credits, adjustments, and membership changes from
rollout. Administrators can search the audit trail by event, actor, run, or participant ID, filter by sync,
credit, or admin action, and inspect event details.
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
- `app/view/appsec/scoring/`: Scoring rules, named tiers, season score corrections, and reset controls.
- `app/view/appsec/dashboard/`: Aggregate program metrics and integration status.
- `app/view/appsec/slack/`: Slack account mappings and unmapped author review.
- `app/view/appsec/delta/`: Eligible Delta categories and single Delta event mappings.

## Contributing
Contributions to the frontend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix, following the naming convention `feature/your-feature-name` or `bugfix/your-bug-fix-name`.
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.

If you have any questions or need help, feel free to reach out to the appsec team!
# Frontend application for Security champions stats.

## Overview
The frontend application is built using React, TypeScript, and Next.js. Employees can enroll in the
program and view participant names and teams. AppSec administrators can manage participant status,
correct season scores, schedule or start a new season, map Slack accounts, and choose eligible Delta
categories or single owner-confirmed Delta event UUIDs. Delta registration sync is disabled until
production access and eligible public event identifiers are confirmed.
API proxies preserve backend status, response body, and content type; proxy-local errors use RFC 9457 Problem Details.
The scoring dashboard (`/appsec/scoring`) presents season controls and scoring rules in compact,
responsive cards, with an inline preview before administrators apply changes. Administrators can add,
rename, and remove tiers, set minimum point thresholds, and configure points for the five existing
activity types. The participant table supports name/email filtering, score history, and inline point
adjustments, including corrections linked to an activity's season. Participant progress and leaderboards
use the saved tiers, including in the Hacker theme, where the highest tier in a multi-tier progression
receives top clearance.
Use **View score history** beside a participant to open the shared score-history drawer.
Administrators can inspect season summaries and paginated entries, including source references,
recorded and activity dates in Oslo time, revoked credits, adjustment reasons and administrators,
linked activities, and scoring-rule changes. The drawer supports a `?history=<participant-id>` deep link;
**Adjust points** closes it and opens the existing inline adjustment row. Participants see a separate
privacy-safe history page with season summaries and filters; operational identifiers, source references,
administrator identities, and adjustment reasons are not returned in their response. Both views start at
the current season and can show records from before audit rollout.
Score history and the audit trail show event names/dates and specific GitHub changes with source links
where available. Event/activity dates are distinct from the date points were recorded. Technical IDs remain
in administrator details, not participant descriptions. Older entries without source context explicitly
show that event/change details are unavailable; integration syncs enrich existing credits without awarding
duplicate points.
Source rows appear only when they contain a description, usable link, date or event/change fallback;
Slack credits without source context do not add blank rows.
Activity values normally affect future credits; opt into current-season retroactive changes to append
adjustments without rewriting original credits, manual corrections, or closed seasons. Zero-point
activities are still recorded. If a preview becomes stale, reload configuration and preview again.
The admin program dashboard summarizes participation and points by activity, highlights items needing attention,
shows weekly points and Slack/Delta/GitHub sync health, and lets administrators trigger enabled syncs.
The Slack administration page (`/appsec/slack`) shows channel participation first: active participants outside the
program channel, participants deactivated after leaving it with their notice status, and unresolved Slack accounts.
Administrators can queue a channel check there. The page also manages account mappings and group membership operations.
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
removing the public event. Review history remains visible to contributors and administrators, with
subject names retained when a contributor is removed from a revised claim or becomes inactive.
Both claim pages use collapsible cards with event dates, colored approval labels, and contributor-status
counts in the header. Fully approved claims start closed; other claims start open. Expand a card to view
evidence, review history, or available actions. Publication does not imply that all contributors are approved.
The sidebar contains shared navigation, an admin-only Administration section, and theme controls with
the signed-in user's initials at the bottom.
Page content aligns left within the main layout's shared responsive gutters. The `.hubRedesign` wrapper
keeps history, dashboard, and audit pages left-aligned while retaining their maximum content width.
The favicon is a locally bundled copy of the Security Champion logo from
https://sikkerhet.nav.no/img/SecurityChampion.svg (`src/app/icon.svg`).
The optional Hacker theme adds a Matrix-inspired overview and shell; its rain, CRT, and phosphor
preferences are available only while Hacker is selected.
Event listings include cached playbook events, with date-only ranges shown without invented times.
Delta and manual events take priority over same-day internal playbook entries; unrelated external entries remain visible.
Active participants see **Signed up**, **Not signed up**, **Hosting**, or **Signup status unavailable** for
upcoming Delta events across the overview, Events page and Hacker theme. Other event types show no signup
status. The status is checked by email in the backend; the badge's tooltip shows the check time, and snapshots
are cached for at most 60 seconds. Reload the events after signing up to retrieve the latest available snapshot.
In **Manage events**, administrators select an upcoming Delta event, preview Slack reminder recipients,
and edit a pre-filled message before explicitly confirming sending. Messages must contain text and be at
most 4000 characters. Switching events clears the preview and confirmation; editing the message clears
confirmation without resetting delivery tracking. Signed-up participants and hosts are excluded; unresolved
accounts, previous deliveries and unconfirmed deliveries are listed as skipped. A queued batch is not a
completed delivery: check the audit trail, then use **Preview Slack reminders** again for current delivery
outcomes. Stale previews are rejected.
Each participant receives at most one successfully delivered reminder per Delta event; failed requests require
a fresh preview before retrying, and uncertain deliveries are not resent automatically. Reminders are manual
Slack DMs only, with no automatic scheduling or email delivery.
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
# Security Champion Stats frontend

A Next.js web application for participants and administrators in the Security Champion program.

## Run locally

**Prerequisites:** [mise](https://mise.jdx.dev/getting-started.html), activated in your shell, Node.js 24, and a
running backend. `mise install` installs pnpm as declared in [`mise.toml`](mise.toml); install Node.js separately.

From `apps/frontend`:

```bash
mise install
pnpm install
pnpm dev:local
```

Open `http://localhost:3000`. See the [backend README](../backend/Readme.md) for local API setup.

## Build and test

```bash
pnpm test
pnpm lint
pnpm build
```

## Tech stack

- TypeScript, React, and Next.js
- NAV Aksel design system
- Vitest and Testing Library
- Biome for linting

The frontend uses Next.js API routes to proxy requests to the backend.

## Feedback

Active participants can submit feedback from **Feedback** in the navigation. The app confirms submission without
exposing the admin workflow. Administrators use **Administration > Feedback inbox** at `/appsec/feedback` to search
submissions and follow up in a two-pane inbox. Filters apply immediately and are preserved in the URL; individual
submissions have shareable links at `/appsec/feedback/{id}`. The inbox refreshes every minute. With a feedback row
focused, use **j/k** or the arrow keys to move between submissions and **/** to focus search. With the ownership
button in the property strip focused, use **t** to take ownership or take over. These character shortcuts do not
operate elsewhere on the page. Use **Ctrl/Cmd+Enter** in the composer to submit a note or ticket link.

Opening a submission marks it read for that administrator without claiming it or adding handling history.
Only the owner can change its status, add internal notes, or link tickets. Administrators can take over ownership;
releasing ownership does not change the status.

Any administrator can assess feedback independently of ownership and award one qualifying credit, initially worth
1 point and configurable in the scoring dashboard. Administrators cannot award their own submissions, and recorded
scoring decisions cannot be reset in the inbox. Resolving or reopening feedback does not change its credit.
Exceptional contributions and score corrections use manual adjustments. Internal notes remain admin-only;
linking a ticket does not create a ticket or notify the submitter.

## Deployment

The [frontend deployment workflow](../../.github/workflows/deploy-frontend.yaml) builds and deploys the application
to NAIS on pushes to `main`. The deployment manifest is [`.nais/nais.yaml`](.nais/nais.yaml).

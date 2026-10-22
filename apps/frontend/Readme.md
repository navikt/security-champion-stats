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
exposing the admin workflow. Administrators use **Administration > Feedback inbox** to search submissions,
take or release ownership, record follow-up actions, and close or reopen feedback. Opening a submission does not claim it.

An owner can award one qualifying feedback credit, initially worth 1 point and configurable in the scoring dashboard.
Another administrator must assess an administrator's own submission. Scoring is separate from handling:
closing or reopening feedback does not change its credit. Exceptional contributions and score corrections use manual adjustments.
Internal notes remain admin-only; recording an external ticket or contact does not create a ticket or send a message.

## Deployment

The [frontend deployment workflow](../../.github/workflows/deploy-frontend.yaml) builds and deploys the application
to NAIS on pushes to `main`. The deployment manifest is [`.nais/nais.yaml`](.nais/nais.yaml).

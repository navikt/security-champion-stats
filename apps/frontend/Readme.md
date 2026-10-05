# Frontend application for Security champions stats.

## Overview
The frontend application is built using React, TypeScript, and Next.js. Employees can enroll in the
program and view participant names and teams. AppSec administrators can manage participant status and
permanently delete participant records.

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
- next-intl: Internationalization (i18n) support for Next.js.
- NAV Aksel (`@navikt/ds-react`): NAV's design system component library.
- chart.js / react-chartjs-2: For rendering statistics charts.
- Vitest: A fast unit test runner.
- Grafana Faro: Web observability and tracing.

## Folder Structure
- `app/`: Main Next.js App Router directory.
  - `[locale]/`: Locale-based routing (supports i18n).
  - `api/`: Next.js API routes (proxied calls to the backend).
  - `shared/`: Shared components, hooks, utilities, and theme.
  - `style/`: Global styles.
  - `utils/`: App-level utility functions.
- `i18n/`: i18n configuration and routing setup.
- `messages/`: Translation message files.
- `instrumentation/`: OpenTelemetry / Grafana Faro instrumentation setup.

## Contributing
Contributions to the frontend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix, following the naming convention `feature/your-feature-name` or `bugfix/your-bug-fix-name`.
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.

If you have any questions or need help, feel free to reach out to the appsec team!
# Frontend application for Security champions stats.

## Overview
The frontend application is built using React, TypeScript and Next, and it serves as the user interface for
displaying security champions statistics, join security champions and much more with time. It interacts with the backend API to fetch data 
and presents it to users. Frontend has also admin endpoint meant to be use for appsec team for administrating like adding points or deleting members and so on. 
The application is meant to increase engagement and motivation among security champion by gamifying the experience and providing a platform for users to track their progress and see other security champions.

## Getting Started
To get started with the frontend application, follow these steps: 
1. install dependencies: `pnpm install` (make sure you have pnpm installed globally, avoid using npm)
2. start the development server: `pnpm run dev` and expose MOCKS_ENABLED=true or run `pnpm run dev:mock` to enable mock data
    1. local server is not implemented yet, so all data is mocked

## Technologies Used
- React: A JavaScript library for building user interfaces.
- TypeScript: A typed superset of JavaScript that compiles to plain JavaScript.
- Next.js: A React framework for server-side rendering and static site generation.
- Tailwind CSS: A utility-first CSS framework for styling the application.

## Folder Structure
- `components/`: Contains reusable React components used throughout the application.
- `pages/`: Contains the main pages of the application, following Next.js conventions.
- `styles/`: Contains global styles and Tailwind CSS configuration.
- `utils/`: Contains utility functions and helpers for the application.
- `api/`: Contains API service functions for fetching data from the backend, (responses is mocked if MOCKS_ENABLED).
- `hooks/`: Contains custom React hooks for managing state and side effects.

## Contributing
Contributions to the frontend application are welcome! If you would like to contribute, please follow these steps:
1. Create a new branch for your feature or bug fix, following the naming convention `feature/your-feature-name` or `bugfix/your-bug-fix-name`.
2. Make your changes and commit them with descriptive commit messages.
3. Push your branch to the remote repository and create a pull request.

If you have any questions or need help, feel free to reach out to the appsec team!
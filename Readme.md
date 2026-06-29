# Security Champion Stats

This repository contains the code for the Security Champion Stats application, a platform for tracking and
displaying statistics related to security champions within an organization. The application is designed
to motivate and engage security champions by gamifying their experience and providing a platform for users to track
their progress and compare themselves with other security champions.

The backend includes a scheduled job that runs every two days, adds new security champions to the Slack channel,
and greets them with a welcome message. Slack welcome posts use the backend config value
`slack.new-security-champion-url`.

For internal Swagger testing set `SWAGGER_ACCESS_ENABLED=true` and
`SWAGGER_ACCESS_KEY` in backend environment, then use `X-Swagger-Auth` in Swagger Authorize.

For more information about the application, read the README files in the `apps/backend` and `apps/frontend`
subdirectories.

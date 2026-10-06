import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { AdminDashboardOverview } from "@/app/utils/Variables";
import { AdminDashboardView } from "./AdminDashboardView";

const overview: AdminDashboardOverview = {
	season: {
		id: "season-1",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	today: "2026-10-05",
	activeParticipantCount: 7,
	eventRegistrationCount: 3,
	pointsByCreditType: [
		{ creditType: "SLACK_WEEK", points: 5 },
		{ creditType: "DELTA_REGISTRATION", points: 3 },
		{ creditType: "POINT_ADJUSTMENT", points: -1 },
	],
	weeklyTotals: [
		{
			weekStarting: "2026-10-05",
			pointsByCreditType: {
				SLACK_WEEK: 2,
				DELTA_REGISTRATION: 1,
				POINT_ADJUSTMENT: 0,
			},
		},
	],
	slack: {
		enabled: true,
		lastAttemptAt: "2026-10-05T12:00:00Z",
		lastSuccessAt: "2026-10-05T12:00:00Z",
		outcome: "SUCCEEDED",
		messagesScanned: 20,
		creditsAwarded: 2,
		duplicateCredits: 0,
		unmappedAuthors: 0,
		failureSummary: null,
	},
	delta: {
		enabled: false,
		lastAttemptAt: "2026-10-05T12:00:00Z",
		lastSuccessAt: null,
		outcome: "FAILED",
		eventsScanned: 0,
		creditsAwarded: 0,
		duplicateCredits: 0,
		unmatchedRegistrations: 0,
		failedEvents: 1,
		failureSummary: "Delta service could not be reached",
	},
	github: {
		enabled: false,
		lastAttemptAt: null,
		lastSuccessAt: null,
		outcome: null,
		contributionsScanned: 0,
		creditsAwarded: 0,
		duplicateCredits: 0,
		unmappedAuthors: 0,
		failureSummary: null,
	},
};

describe("AdminDashboardView", () => {
	it("should show aggregate metrics, integration health, and admin operation links", () => {
		render(
			<AdminDashboardView
				overview={overview}
				onTriggerSync={vi.fn()}
				triggeringSync={null}
				triggerError={null}
			/>,
		);

		expect(
			screen.getByRole("heading", { name: "Program dashboard" }),
		).toBeInTheDocument();
		expect(screen.getByText("7")).toBeInTheDocument();
		expect(screen.getByText("3")).toBeInTheDocument();
		expect(screen.getByText("Slack participation")).toBeInTheDocument();
		expect(screen.getByText("Event registration")).toBeInTheDocument();
		expect(screen.getByText("Administrator adjustments")).toBeInTheDocument();
		expect(screen.getAllByText("Sync is disabled")).toHaveLength(2);
		expect(
			screen.getByText("Delta service could not be reached"),
		).toBeInTheDocument();
		expect(
			screen.getByRole("link", { name: "Manage participants" }),
		).toHaveAttribute("href", "/appsec/membership");
		expect(
			screen.getByRole("link", { name: "Manage scoring" }),
		).toHaveAttribute("href", "/appsec/scoring");
		expect(screen.getByRole("link", { name: "Manage events" })).toHaveAttribute(
			"href",
			"/appsec/events",
		);
	});

	it("should allow admins to trigger enabled integrations only", async () => {
		const onTriggerSync = vi.fn();
		const user = userEvent.setup();
		render(
			<AdminDashboardView
				overview={overview}
				onTriggerSync={onTriggerSync}
				triggeringSync={null}
				triggerError={null}
			/>,
		);

		await user.click(screen.getByRole("button", { name: "Run Slack sync now" }));

		expect(onTriggerSync).toHaveBeenCalledWith("slack");
		expect(
			screen.getByRole("button", { name: "Run Delta sync now" }),
		).toBeDisabled();
	});

	it("should show GitHub as disabled with sync unavailable", () => {
		render(
			<AdminDashboardView
				overview={overview}
				onTriggerSync={vi.fn()}
				triggeringSync={null}
				triggerError={null}
			/>,
		);

		expect(screen.getByRole("heading", { name: "GitHub" })).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Run GitHub sync now" }),
		).toBeDisabled();
	});

	it("should trigger an enabled GitHub sync and block it while running", async () => {
		const onTriggerSync = vi.fn();
		const user = userEvent.setup();
		const enabled = {
			...overview,
			github: { ...overview.github, enabled: true },
		};
		const { rerender } = render(
			<AdminDashboardView
				overview={enabled}
				onTriggerSync={onTriggerSync}
				triggeringSync={null}
				triggerError={null}
			/>,
		);

		await user.click(
			screen.getByRole("button", { name: "Run GitHub sync now" }),
		);
		expect(onTriggerSync).toHaveBeenCalledWith("github");

		rerender(
			<AdminDashboardView
				overview={{
					...enabled,
					github: { ...enabled.github, outcome: "RUNNING" },
				}}
				onTriggerSync={onTriggerSync}
				triggeringSync={null}
				triggerError={null}
			/>,
		);
		expect(screen.getByText("Sync in progress")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Run GitHub sync now" }),
		).toBeDisabled();
	});

	it("should show GitHub scan counters including unmapped authors", () => {
		render(
			<AdminDashboardView
				overview={{
					...overview,
					github: {
						...overview.github,
						enabled: true,
						contributionsScanned: 41,
						creditsAwarded: 6,
						duplicateCredits: 2,
						unmappedAuthors: 5,
					},
				}}
				onTriggerSync={vi.fn()}
				triggeringSync={null}
				triggerError={null}
			/>,
		);

		const counter = (label: string) =>
			screen.getByText(label).nextElementSibling;
		expect(counter("Contributions scanned")).toHaveTextContent("41");
		expect(counter("Credits awarded")).toHaveTextContent("6");
		expect(counter("Duplicate credits")).toHaveTextContent("2");
		expect(counter("Unmapped authors")).toHaveTextContent("5");
	});

	it("should show GitHub failure summary and trigger error", () => {
		render(
			<AdminDashboardView
				overview={{
					...overview,
					github: {
						...overview.github,
						enabled: true,
						outcome: "FAILED",
						failureSummary: "GitHub could not be reached",
					},
				}}
				onTriggerSync={vi.fn()}
				triggeringSync={null}
				triggerError={{
					integration: "github",
					message: "We couldn't start the sync. Try again later.",
				}}
			/>,
		);

		expect(screen.getByText("Last sync failed")).toBeInTheDocument();
		expect(screen.getByText("GitHub could not be reached")).toBeInTheDocument();
		expect(
			screen.getByText("We couldn't start the sync. Try again later."),
		).toBeInTheDocument();
	});
});

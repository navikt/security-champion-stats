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
		expect(screen.getByText("Sync is disabled")).toBeInTheDocument();
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
});

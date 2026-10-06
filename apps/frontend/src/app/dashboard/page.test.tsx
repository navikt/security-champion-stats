import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type { AdminDashboardOverview } from "@/app/utils/Variables";
import Page from "./page";

vi.mock("@/app/shared/hooks/UseMe", () => ({
	useMe: () => ({
		me: {
			username: "admin",
			displayName: "Admin",
			isAdmin: true,
			isParticipant: false,
			isActive: false,
		},
		loading: false,
	}),
}));

const idle = {
	enabled: false,
	lastAttemptAt: null,
	lastSuccessAt: null,
	outcome: null,
	failureSummary: null,
};

function overview(
	github: Partial<AdminDashboardOverview["github"]> = {},
): AdminDashboardOverview {
	return {
		season: {
			id: "season-1",
			startsOn: "2026-01-01",
			endsOn: null,
			nextResetDate: "2027-01-01",
		},
		today: "2026-10-05",
		activeParticipantCount: 1,
		eventRegistrationCount: 0,
		pointsByCreditType: [],
		weeklyTotals: [],
		slack: { ...idle, messagesScanned: 0, creditsAwarded: 0, duplicateCredits: 0, unmappedAuthors: 0 },
		delta: { ...idle, eventsScanned: 0, creditsAwarded: 0, duplicateCredits: 0, unmatchedRegistrations: 0, failedEvents: 0 },
		github: {
			...idle,
			enabled: true,
			contributionsScanned: 0,
			creditsAwarded: 0,
			duplicateCredits: 0,
			unmappedAuthors: 0,
			...github,
		},
	};
}

const syncButton = () =>
	screen.findByRole("button", { name: "Run GitHub sync now" });

describe("Admin dashboard page GitHub sync", () => {
	beforeEach(() => {
		vi.spyOn(console, "error").mockImplementation(() => {});
	});
	afterEach(() => {
		vi.useRealTimers();
		vi.restoreAllMocks();
	});

	it("should refresh after 202 and poll until the new run completes", async () => {
		const user = userEvent.setup();
		const trigger = vi.spyOn(Apies, "triggerGithubSync").mockResolvedValue(202);
		const get = vi
			.spyOn(Apies, "getAdminDashboard")
			.mockResolvedValueOnce(overview({ lastAttemptAt: "2026-10-05T10:00:00Z", outcome: "SUCCEEDED" }))
			.mockResolvedValueOnce(overview({ lastAttemptAt: "2026-10-05T11:00:00Z", outcome: "RUNNING" }))
			.mockResolvedValue(
				overview({
					lastAttemptAt: "2026-10-05T11:00:00Z",
					outcome: "SUCCEEDED",
					creditsAwarded: 3,
				}),
			);

		render(<Page />);
		await user.click(await syncButton());
		expect(trigger).toHaveBeenCalledTimes(1);
		expect(await screen.findByText("Sync in progress")).toBeInTheDocument();
		expect(get).toHaveBeenCalledTimes(2);
		expect(await syncButton()).toBeDisabled();

		await waitFor(() => expect(get.mock.calls.length).toBeGreaterThanOrEqual(3), {
			timeout: 5000,
		});
		expect(await screen.findByText("Last sync succeeded")).toBeInTheDocument();
		expect(screen.getByText("Credits awarded").nextElementSibling).toHaveTextContent("3");
		expect(await syncButton()).toBeEnabled();
	}, 15000);

	it("should keep polling while GitHub is RUNNING without a manual trigger", async () => {
		vi.useFakeTimers({ shouldAdvanceTime: true });
		const get = vi
			.spyOn(Apies, "getAdminDashboard")
			.mockResolvedValueOnce(overview({ outcome: "RUNNING", lastAttemptAt: "2026-10-05T11:00:00Z" }))
			.mockResolvedValue(overview({ outcome: "FAILED", lastAttemptAt: "2026-10-05T11:00:00Z", failureSummary: "GitHub could not be reached" }));

		render(<Page />);
		expect(await screen.findByText("Sync in progress")).toBeInTheDocument();
		await act(async () => {
			await vi.advanceTimersByTimeAsync(3000);
		});
		expect(get).toHaveBeenCalledTimes(2);
		expect(await screen.findByText("Last sync failed")).toBeInTheDocument();
		expect(screen.getByText("GitHub could not be reached")).toBeInTheDocument();
	});

	it("should show busy/disabled message and refresh on 409", async () => {
		const user = userEvent.setup();
		vi.spyOn(Apies, "triggerGithubSync").mockResolvedValue(409);
		const get = vi.spyOn(Apies, "getAdminDashboard").mockResolvedValue(overview());

		render(<Page />);
		await user.click(await syncButton());

		expect(
			await screen.findByText("A sync is already running or this integration is disabled."),
		).toBeInTheDocument();
		expect(get).toHaveBeenCalledTimes(2);
	});

	it("should show a retry message on 503", async () => {
		const user = userEvent.setup();
		vi.spyOn(Apies, "triggerGithubSync").mockResolvedValue(503);
		const get = vi.spyOn(Apies, "getAdminDashboard").mockResolvedValue(overview());

		render(<Page />);
		await user.click(await syncButton());

		expect(
			await screen.findByText("We couldn't start the sync. Try again later."),
		).toBeInTheDocument();
		expect(get).toHaveBeenCalledTimes(1);
		expect(await syncButton()).toBeEnabled();
	});

	it("should show a retry message on network error", async () => {
		const user = userEvent.setup();
		vi.spyOn(Apies, "triggerGithubSync").mockRejectedValue(new Error("offline"));
		vi.spyOn(Apies, "getAdminDashboard").mockResolvedValue(overview());

		render(<Page />);
		await user.click(await syncButton());

		expect(
			await screen.findByText("We couldn't start the sync. Try again later."),
		).toBeInTheDocument();
		expect(await syncButton()).toBeEnabled();
	});
});

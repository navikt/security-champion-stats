import {
	fireEvent,
	render,
	screen,
	waitFor,
	within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { defaultScoringConfiguration } from "@/app/utils/scoringFixtures.test-support";
import type {
	AdminScoringOverview,
	ScoringConfigurationPreview,
} from "@/app/utils/Variables";
import { ScoringManagementView } from "./ScoringManagementView";

const overview: AdminScoringOverview = {
	configuration: defaultScoringConfiguration,
	season: {
		id: "season-1",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	today: "2026-10-05",
	participants: [
		{
			participantId: "participant-1",
			fullName: "Example Person",
			email: "person@nav.no",
			active: true,
			points: 8,
			level: "Novice",
		},
	],
};

const configurationPreview: ScoringConfigurationPreview = {
	token: "preview-token",
	season: overview.season,
	affectedCredits: 0,
	pointsDelta: 0,
	participants: [],
};

describe("ScoringManagementView", () => {
	afterEach(() => {
		vi.restoreAllMocks();
		window.history.replaceState(null, "", "/appsec/scoring");
	});

	it("loads score history only when the participant history action is opened", async () => {
		const getSummary = vi
			.spyOn(Apies, "getScoreHistorySummary")
			.mockResolvedValue({
				points: 8,
				tier: "Novice",
				rank: 1,
				breakdown: {
					slack: 8,
					deltaRegistration: 0,
					githubCommit: 0,
					githubPullRequest: 0,
					securityEvent: 0,
					adjustments: 0,
					ruleChanges: 0,
				},
				seasons: [{ id: "season-1", startsOn: "2026-01-01", endsOn: null }],
			});
		const getHistory = vi
			.spyOn(Apies, "getScoreHistoryPage")
			.mockResolvedValue({
				entries: [],
				nextCursor: null,
			});
		const getCredits = vi.spyOn(Apies, "getParticipantCredits");
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);
		expect(getHistory).not.toHaveBeenCalled();
		fireEvent.click(
			screen.getByRole("button", {
				name: "View score history for Example Person",
			}),
		);
		expect(
			await screen.findByRole("heading", {
				name: "Score history for Example Person",
			}),
		).toBeInTheDocument();
		await waitFor(() =>
			expect(getSummary).toHaveBeenCalledWith(
				"admin",
				"participant-1",
				undefined,
			),
		);
		expect(getHistory).toHaveBeenCalledWith(
			"admin",
			"participant-1",
			expect.objectContaining({
				season: "season-1",
				type: "all",
			}),
		);
		expect(getCredits).not.toHaveBeenCalled();
		expect(window.location.search).toContain("history=participant-1");
		fireEvent.click(
			screen.getByRole("button", { name: "Close score history" }),
		);
		expect(
			screen.queryByRole("dialog", {
				name: "Score history for Example Person",
			}),
		).not.toBeInTheDocument();
		expect(window.location.search).not.toContain("history=");
	});

	it("opens from the history query and transitions to inline adjustment", async () => {
		window.history.replaceState(
			null,
			"",
			"/appsec/scoring?history=participant-1",
		);
		vi.spyOn(Apies, "getScoreHistorySummary").mockResolvedValue({
			points: 8,
			tier: "Novice",
			rank: 1,
			breakdown: {
				slack: 8,
				deltaRegistration: 0,
				githubCommit: 0,
				githubPullRequest: 0,
				securityEvent: 0,
				adjustments: 0,
				ruleChanges: 0,
			},
			seasons: [{ id: "season-1", startsOn: "2026-01-01", endsOn: null }],
		});
		vi.spyOn(Apies, "getScoreHistoryPage").mockResolvedValue({
			entries: [],
			nextCursor: null,
		});
		vi.spyOn(Apies, "getParticipantCredits").mockResolvedValue([]);
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);

		await screen.findByRole("dialog", {
			name: "Score history for Example Person",
		});
		fireEvent.click(screen.getByRole("button", { name: "Adjust points" }));
		expect(
			await screen.findByLabelText("Adjust points for Example Person"),
		).toBeInTheDocument();
		expect(
			screen.queryByRole("dialog", {
				name: "Score history for Example Person",
			}),
		).not.toBeInTheDocument();
		expect(window.location.search).not.toContain("history=");
	});

	it("expands admin entries to reveal copyable source and season details", async () => {
		vi.spyOn(Apies, "getScoreHistorySummary").mockResolvedValue({
			points: 8,
			tier: "Novice",
			rank: 1,
			breakdown: {
				slack: 0,
				deltaRegistration: 0,
				githubCommit: 8,
				githubPullRequest: 0,
				securityEvent: 0,
				adjustments: 0,
				ruleChanges: 0,
			},
			seasons: [{ id: "season-1", startsOn: "2026-01-01", endsOn: null }],
		});
		vi.spyOn(Apies, "getScoreHistoryPage").mockResolvedValue({
			entries: [
				{
					id: "credit-1",
					kind: "credit",
					recordedAt: "2026-10-06T10:00:00Z",
					activityAt: "2026-10-05T12:00:00Z",
					creditType: "GITHUB_COMMIT",
					points: 8,
					displayName: "Documentation update",
					sourceUrl:
						"https://github.com/navikt/security-playbook/commit/abcdef",
					sourceOccurredAt: "2026-10-05T12:00:00Z",
					sourceRef: "navikt/repo:commit:abcdef",
					creditId: "credit-1",
					seasonId: "season-1",
					reason: null,
					adminName: null,
					linkedCreditId: null,
					revokedAt: "2026-10-07T10:00:00Z",
					action: null,
					membershipStatusBefore: null,
					membershipStatusAfter: null,
					membershipReason: null,
					ruleChange: false,
				},
				{
					id: "adjustment-1",
					kind: "adjustment",
					recordedAt: "2026-10-07T11:00:00Z",
					activityAt: null,
					creditType: "GITHUB_COMMIT",
					points: -1,
					displayName: "Documentation update",
					sourceUrl:
						"https://github.com/navikt/security-playbook/commit/abcdef",
					sourceOccurredAt: "2026-10-05T12:00:00Z",
					sourceRef: null,
					creditId: null,
					seasonId: "season-1",
					reason: "Correct linked activity",
					adminName: "admin@nav.no",
					linkedCreditId: "credit-1",
					revokedAt: null,
					action: null,
					membershipStatusBefore: null,
					membershipStatusAfter: null,
					membershipReason: null,
					ruleChange: false,
				},
			],
			nextCursor: null,
		});
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);

		fireEvent.click(
			screen.getByRole("button", {
				name: "View score history for Example Person",
			}),
		);
		const entry = await screen.findByRole("button", {
			name: /GitHub commit/,
		});
		expect(screen.queryByText("credit-1")).not.toBeInTheDocument();
		fireEvent.click(entry);

		expect(
			screen.getByRole("link", { name: "Documentation update" }),
		).toHaveAttribute(
			"href",
			"https://github.com/navikt/security-playbook/commit/abcdef",
		);
		expect(screen.getByText("navikt/repo:commit:abcdef")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Copy source reference" }),
		).toBeInTheDocument();
		expect(screen.getByText("Activity date")).toBeInTheDocument();
		expect(screen.getByText("Revoked at")).toBeInTheDocument();
		expect(screen.getAllByText("Season")).toHaveLength(2);
		fireEvent.click(entry);
		fireEvent.click(screen.getByRole("button", { name: /Point adjustment/ }));
		expect(screen.getByText("Linked activity")).toBeInTheDocument();
		expect(
			screen.getByRole("link", { name: "Documentation update" }),
		).toBeInTheDocument();
		expect(screen.getByText("credit-1")).toBeInTheDocument();
		expect(screen.queryByText("Revokes")).not.toBeInTheDocument();
	});

	it("links a signed inline adjustment to the selected activity", async () => {
		vi.spyOn(Apies, "getParticipantCredits").mockResolvedValue([
			{
				id: "credit-1",
				creditType: "DELTA_REGISTRATION",
				sourceReference: "event-1",
				points: 1,
				seasonStartsOn: "2026-01-01",
			},
		]);
		const addAdjustment = vi
			.spyOn(Apies, "addPointAdjustment")
			.mockResolvedValue(201);
		const refresh = vi.fn().mockResolvedValue(overview);
		render(<ScoringManagementView overview={overview} onRefresh={refresh} />);

		fireEvent.click(screen.getByRole("button", { name: "Adjust" }));
		const reason = await screen.findByLabelText(
			"Reason for adjusting Example Person's points",
		);
		fireEvent.change(
			screen.getByLabelText("Adjust points for Example Person"),
			{ target: { value: "-1" } },
		);
		fireEvent.change(screen.getByLabelText("Activity to correct"), {
			target: { value: "credit-1" },
		});
		fireEvent.change(reason, {
			target: { value: "Correct duplicate registration" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Apply" }));

		await waitFor(() =>
			expect(addAdjustment).toHaveBeenCalledWith(
				"participant-1",
				-1,
				"Correct duplicate registration",
				"credit-1",
			),
		);
		await waitFor(() => expect(refresh).toHaveBeenCalled());
	});

	it("clamps current-season adjustments at zero and keeps closed-season corrections intact", async () => {
		vi.spyOn(Apies, "getParticipantCredits").mockResolvedValue([
			{
				id: "old-credit",
				creditType: "DELTA_REGISTRATION",
				sourceReference: "old-event",
				points: 1,
				seasonStartsOn: "2025-01-01",
			},
		]);
		const addAdjustment = vi
			.spyOn(Apies, "addPointAdjustment")
			.mockResolvedValue(201);
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Adjust" }));
		await screen.findByLabelText("Activity to correct");
		fireEvent.change(
			screen.getByLabelText("Adjust points for Example Person"),
			{ target: { value: "-15" } },
		);
		fireEvent.change(
			screen.getByLabelText("Reason for adjusting Example Person's points"),
			{
				target: { value: "Remove incorrect points" },
			},
		);
		fireEvent.click(screen.getByRole("button", { name: "Apply" }));
		await waitFor(() =>
			expect(addAdjustment).toHaveBeenCalledWith(
				"participant-1",
				-8,
				"Remove incorrect points",
				undefined,
			),
		);

		fireEvent.click(screen.getByRole("button", { name: "Adjust" }));
		await screen.findByLabelText("Activity to correct");
		fireEvent.change(
			screen.getByLabelText("Adjust points for Example Person"),
			{ target: { value: "-15" } },
		);
		fireEvent.change(screen.getByLabelText("Activity to correct"), {
			target: { value: "old-credit" },
		});
		fireEvent.change(
			screen.getByLabelText("Reason for adjusting Example Person's points"),
			{
				target: { value: "Correct old season" },
			},
		);
		fireEvent.click(screen.getByRole("button", { name: "Apply" }));
		await waitFor(() =>
			expect(addAdjustment).toHaveBeenLastCalledWith(
				"participant-1",
				-15,
				"Correct old season",
				"old-credit",
			),
		);
	});

	it("requires a reason in the inline confirmation before starting a new season", async () => {
		const resetSeason = vi.spyOn(Apies, "resetSeason").mockResolvedValue(200);
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);

		fireEvent.click(
			screen.getByRole("button", { name: "Start new season..." }),
		);
		expect(
			screen.getByText(
				/Ends Season 2026 now and resets everyone's season points/,
			),
		).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Start Season 2027" }));
		expect(
			await screen.findByText("Enter a reason for the season reset."),
		).toBeInTheDocument();
		expect(resetSeason).not.toHaveBeenCalled();

		fireEvent.change(
			screen.getByLabelText("Reason for starting the new season"),
			{ target: { value: "Program reset" } },
		);
		fireEvent.click(screen.getByRole("button", { name: "Start Season 2027" }));
		await waitFor(() =>
			expect(resetSeason).toHaveBeenCalledWith("Program reset"),
		);
	});

	it("filters participants without changing their dense rank numbers", () => {
		const overviewWithParticipants: AdminScoringOverview = {
			...overview,
			participants: [
				...overview.participants,
				{
					participantId: "participant-2",
					fullName: "Another Person",
					email: "another@nav.no",
					active: true,
					points: 8,
					level: "Novice",
				},
				{
					participantId: "participant-3",
					fullName: "Inactive Person",
					email: "inactive@nav.no",
					active: false,
					points: 2,
					level: "Novice",
				},
			],
		};
		render(
			<ScoringManagementView
				overview={overviewWithParticipants}
				onRefresh={vi.fn().mockResolvedValue(overviewWithParticipants)}
			/>,
		);
		expect(
			screen.getByText("2 active · ranked by season points"),
		).toBeInTheDocument();
		expect(
			within(screen.getByRole("row", { name: /Example Person/ })).getAllByRole(
				"cell",
			)[0],
		).toHaveTextContent("1");
		expect(
			within(screen.getByRole("row", { name: /Another Person/ })).getAllByRole(
				"cell",
			)[0],
		).toHaveTextContent("1");
		expect(screen.getByText("Inactive Person")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Active" }));
		expect(screen.queryByText("Inactive Person")).not.toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "All" }));
		expect(screen.getByText("Inactive Person")).toBeInTheDocument();
		fireEvent.change(screen.getByLabelText("Filter by name or email"), {
			target: { value: "another@" },
		});
		expect(screen.queryByText("Example Person")).not.toBeInTheDocument();
		expect(screen.getByText("Another Person")).toBeInTheDocument();
		expect(
			within(screen.getByRole("row", { name: /Another Person/ })).getAllByRole(
				"cell",
			)[0],
		).toHaveTextContent("1");
	});

	it("previews the existing server-provided tier and credit impacts inline", async () => {
		vi.spyOn(Apies, "previewScoringConfiguration").mockResolvedValue(
			configurationPreview,
		);
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(overview)}
			/>,
		);
		fireEvent.change(
			screen.getByLabelText("Weekly Slack participation points"),
			{ target: { value: "2" } },
		);
		fireEvent.change(screen.getByLabelText("Reason for change (required)"), {
			target: { value: "Balance points" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Preview changes" }));
		expect(
			await screen.findByRole("heading", { name: "Rule changes" }),
		).toBeInTheDocument();
		expect(screen.getByText("No one changes tier.")).toBeInTheDocument();
	});
});

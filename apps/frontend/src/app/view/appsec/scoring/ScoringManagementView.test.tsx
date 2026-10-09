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
	});

	it("loads score history only when the participant history action is opened", async () => {
		const getHistory = vi
			.spyOn(Apies, "getParticipantScoringHistory")
			.mockResolvedValue({
				currentSeasonId: "season-1",
				seasons: [],
				entries: [],
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
			expect(getHistory).toHaveBeenCalledWith("participant-1"),
		);
		expect(getCredits).not.toHaveBeenCalled();
		fireEvent.click(screen.getByRole("button", { name: "Close" }));
		expect(
			screen.queryByRole("dialog", {
				name: "Score history for Example Person",
			}),
		).not.toBeInTheDocument();
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

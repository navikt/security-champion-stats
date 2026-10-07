import { Apies } from "@/app/shared/hooks/Apies";
import { AdminScoringOverview } from "@/app/utils/Variables";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScoringManagementView } from "./ScoringManagementView";
import { defaultScoringConfiguration } from "@/app/utils/scoringFixtures.test-support";

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

describe("ScoringManagementView", () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it("should link a signed adjustment to the selected activity", async () => {
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
		const refresh = vi.fn().mockResolvedValue(undefined);
		render(<ScoringManagementView overview={overview} onRefresh={refresh} />);

		fireEvent.click(screen.getByRole("button", { name: "Adjust points" }));
		expect(await screen.findByRole("heading", { name: "Adjust points" })).toBeInTheDocument();

		fireEvent.change(screen.getByRole("textbox", { name: "Point change" }), {
			target: { value: "-1" },
		});
		fireEvent.change(screen.getByRole("combobox", { name: "Activity to correct" }), {
			target: { value: "credit-1" },
		});
		fireEvent.change(screen.getByRole("textbox", { name: "Reason" }), {
			target: { value: "Correct duplicate registration" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save adjustment" }));

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

	it("should require a reason before starting a new season", async () => {
		const resetSeason = vi.spyOn(Apies, "resetSeason").mockResolvedValue(200);
		render(
			<ScoringManagementView
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(undefined)}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Start a new season" }));
		fireEvent.click(screen.getByRole("button", { name: "Start new season" }));
		expect(await screen.findByText("Enter a reason.")).toBeInTheDocument();
		expect(resetSeason).not.toHaveBeenCalled();

		fireEvent.change(screen.getByRole("textbox", { name: "Reason" }), {
			target: { value: "Program reset" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Start new season" }));

		await waitFor(() => expect(resetSeason).toHaveBeenCalledWith("Program reset"));
	});
});

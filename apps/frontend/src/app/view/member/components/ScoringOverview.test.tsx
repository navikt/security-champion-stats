import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Apies } from "@/app/shared/hooks/Apies";
import { ScoringOverview } from "./ScoringOverview";

describe("ScoringOverview", () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it("shows current-season progress and the full ranked leaderboard", async () => {
		vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
			season: {
				id: "season-1",
				startsOn: "2026-01-01",
				endsOn: null,
				nextResetDate: "2027-01-01",
			},
			points: 249,
			level: "Apprentice",
			rank: 2,
		});
		vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([
			{ fullName: "Alex", rank: 1, points: 300, level: "Adept" },
			{ fullName: "Sam", rank: 2, points: 249, level: "Apprentice" },
		]);

		render(
			<ScoringOverview
				showPersonalProgress
				showLeaderboard
			/>,
		);

		expect(await screen.findByText("Your season")).toBeInTheDocument();
		expect(screen.getAllByText("249")).toHaveLength(2);
		expect(screen.getAllByText("Apprentice")).toHaveLength(2);
		expect(screen.getByText("#2")).toBeInTheDocument();
		expect(screen.getByText("1 point to Adept")).toBeInTheDocument();
		expect(
			screen.getByRole("heading", { name: "Season leaderboard" }),
		).toBeInTheDocument();
		expect(screen.getByText("Alex")).toBeInTheDocument();
		expect(screen.getByText("Sam")).toBeInTheDocument();
	});

	it("does not assign a rank to a zero-score participant", async () => {
		vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
			season: {
				id: "season-1",
				startsOn: "2026-01-01",
				endsOn: null,
				nextResetDate: "2027-01-01",
			},
			points: 0,
			level: "Novice",
			rank: null,
		});
		vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([]);

		render(
			<ScoringOverview
				showPersonalProgress
				showLeaderboard
			/>,
		);

		expect(await screen.findByText("Not ranked yet")).toBeInTheDocument();
		expect(screen.getByText("No participants have earned points this season yet."))
			.toBeInTheDocument();
	});

	it("does not request scores or leaderboard data when the user cannot view them", () => {
		const getScore = vi.spyOn(Apies, "getParticipantSeasonScore");
		const getLeaderboard = vi.spyOn(Apies, "getLeaderboard");

		const { container } = render(
			<ScoringOverview
				showPersonalProgress={false}
				showLeaderboard={false}
			/>,
		);

		expect(container).toBeEmptyDOMElement();
		expect(getScore).not.toHaveBeenCalled();
		expect(getLeaderboard).not.toHaveBeenCalled();
	});

	it("shows admins the leaderboard without requesting personal scores", async () => {
		const getScore = vi.spyOn(Apies, "getParticipantSeasonScore");
		const getLeaderboard = vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([
			{ fullName: "Alex", rank: 1, points: 300, level: "Adept" },
		]);

		render(
			<ScoringOverview
				showPersonalProgress={false}
				showLeaderboard
			/>,
		);

		expect(
			await screen.findByRole("heading", {
				name: "Season leaderboard",
			}),
		).toBeInTheDocument();
		expect(screen.queryByText("Your season")).not.toBeInTheDocument();
		expect(getScore).not.toHaveBeenCalled();
		expect(getLeaderboard).toHaveBeenCalledOnce();
	});
});

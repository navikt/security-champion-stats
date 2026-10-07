import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Apies } from "@/app/shared/hooks/Apies";
import { ScoringOverview } from "./ScoringOverview";
import { defaultScoringConfiguration } from "@/app/utils/scoringFixtures.test-support";

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
			tiers: defaultScoringConfiguration.tiers,
		});

		vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([
			{ fullName: "Alex", rank: 1, points: 300, level: "Adept", isCurrentUser: false },
			{ fullName: "Sam", rank: 2, points: 249, level: "Apprentice", isCurrentUser: true },
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

	it("shows administrator-defined tier progress", async () => {
		vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
			season: { id: "season-1", startsOn: "2026-01-01", endsOn: null, nextResetDate: "2027-01-01" },
			points: 4, level: "Starter", rank: 1,
			tiers: [{ name: "Starter", points: 0 }, { name: "Champion", points: 5 }],
		});
		render(<ScoringOverview showPersonalProgress showLeaderboard={false} />);
		expect(await screen.findByText("1 point to Champion")).toBeInTheDocument();
		expect(screen.getByText("Starter")).toBeInTheDocument();
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
			tiers: defaultScoringConfiguration.tiers,
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
			{ fullName: "Alex", rank: 1, points: 300, level: "Adept", isCurrentUser: false },
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

	it("marks the current participant by identity and includes them outside the top ten", async () => {
		const entries = Array.from({ length: 12 }, (_, index) => ({
			fullName: "Alex",
			rank: index + 1,
			points: 300 - index,
			level: "Adept" as const,
			isCurrentUser: index === 11,
		}));
		vi.spyOn(Apies, "getLeaderboard").mockResolvedValue(entries);

		render(<ScoringOverview showPersonalProgress={false} showLeaderboard />);

		const youTags = await screen.findAllByText("You");
		expect(youTags).toHaveLength(1);
		expect(youTags[0].closest("li")).toHaveTextContent("12");
		expect(screen.getAllByText("Alex")).toHaveLength(11);
	});
});

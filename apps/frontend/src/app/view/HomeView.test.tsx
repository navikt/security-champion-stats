import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { Me, ProgramParticipant } from "@/app/utils/Variables";
import { MainView } from "./HomeView";

vi.mock("./events/EventsView", () => ({ EventsView: () => null }));

afterEach(() => vi.restoreAllMocks());

it("removes personal scoring and leaderboard immediately after voluntary departure", async () => {
	const me: Me = {
		username: "person@nav.no",
		displayName: null,
		isAdmin: false,
		isParticipant: true,
		isActive: true,
	};
	const participant: ProgramParticipant = {
		id: "participant-1",
		email: me.username,
		fullname: "Person",
		teams: [],
		active: true,
		status: "ACTIVE",
		joinedAt: "2026-01-01T00:00:00Z",
	};
	vi.spyOn(Apies, "fetchEvents").mockResolvedValue([]);
	vi.spyOn(Apies, "fetchMembership")
		.mockResolvedValueOnce(participant)
		.mockResolvedValue({ ...participant, active: false, status: "LEFT" });
	vi.spyOn(Apies, "leaveProgram").mockResolvedValue();
	vi.spyOn(Apies, "getHistory").mockResolvedValue({ entries: [], nextCursor: null });
	vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
		season: { id: "season-1", startsOn: "2026-01-01", endsOn: null, nextResetDate: "2027-01-01" },
		points: 1,
		level: "Novice",
		rank: 1,
		tiers: [{ name: "Novice", points: 0 }],
	});
	vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([]);
	render(<MainView info={me} />);

	expect(await screen.findByText("Season points")).toBeInTheDocument();
	expect(await screen.findByRole("heading", { name: /leaderboard/i })).toBeInTheDocument();
	fireEvent.click(await screen.findByRole("button", { name: "Leave program…" }));
	fireEvent.click(await screen.findByRole("button", { name: "Leave program" }));

	expect(await screen.findByRole("button", { name: "Rejoin program" })).toBeInTheDocument();
	expect(screen.queryByText("Season points")).not.toBeInTheDocument();
	expect(screen.queryByRole("heading", { name: /leaderboard/i })).not.toBeInTheDocument();
});

it("shows an activity error instead of an empty-history message when history fails", async () => {
	const me: Me = {
		username: "person@nav.no",
		displayName: null,
		isAdmin: false,
		isParticipant: true,
		isActive: true,
	};
	const participant: ProgramParticipant = {
		id: "participant-1",
		email: me.username,
		fullname: "Person",
		teams: [],
		active: true,
		status: "ACTIVE",
		joinedAt: "2026-01-01T00:00:00Z",
	};
	vi.spyOn(Apies, "fetchEvents").mockResolvedValue([]);
	vi.spyOn(Apies, "fetchMembership").mockResolvedValue(participant);
	vi.spyOn(Apies, "getHistory").mockRejectedValue(new Error("Unavailable"));
	vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
		season: { id: "season-1", startsOn: "2026-01-01", endsOn: null, nextResetDate: "2027-01-01" },
		points: 1,
		level: "Novice",
		rank: 1,
		tiers: [{ name: "Novice", points: 0 }],
	});
	vi.spyOn(console, "error").mockImplementation(() => {});
	render(<MainView info={me} />);

	const historyError = await screen.findByText("We couldn't load recent activity. Try again later.");
	expect(historyError).toHaveAttribute("role", "alert");
	expect(screen.queryByText(/No activity yet/)).not.toBeInTheDocument();
});

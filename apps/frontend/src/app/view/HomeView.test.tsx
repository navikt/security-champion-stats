import { fireEvent, render, screen, within } from "@testing-library/react";
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
	vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
		season: { id: "season-1", startsOn: "2026-01-01", endsOn: null, nextResetDate: "2027-01-01" },
		points: 1,
		level: "Novice",
		rank: 1,
	});
	vi.spyOn(Apies, "getLeaderboard").mockResolvedValue([]);
	render(<MainView info={me} />);

	expect(await screen.findByRole("heading", { name: "Your season" })).toBeInTheDocument();
	expect(await screen.findByRole("heading", { name: /leaderboard/i })).toBeInTheDocument();
	fireEvent.click(await screen.findByRole("button", { name: "Leave program" }));
	fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Leave program" }));

	expect(await screen.findByRole("button", { name: "Rejoin program" })).toBeInTheDocument();
	expect(screen.queryByRole("heading", { name: "Your season" })).not.toBeInTheDocument();
	expect(screen.queryByRole("heading", { name: /leaderboard/i })).not.toBeInTheDocument();
});

import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type {
	LeaderboardEntry,
	Me,
	ParticipantSeasonScore,
	ProgramParticipant,
	SecurityEvent,
} from "@/app/utils/Variables";
import { HackerOverview } from "./HackerOverview";

vi.mock("@/app/shared/hooks/Apies", () => ({
	Apies: {
		fetchEvents: vi.fn(),
		fetchMembership: vi.fn(),
		getParticipantSeasonScore: vi.fn(),
		getLeaderboard: vi.fn(),
	},
}));

vi.mock("@/app/view/member/components/MembershipView", () => ({
	MembershipView: () => <div>Participation controls</div>,
}));

const participantName = "Ada Lovelace";

const me: Me = {
	username: "ada.lovelace@nav.no",
	displayName: participantName,
	isAdmin: false,
	isParticipant: true,
	isActive: true,
};

const upcomingEvent: SecurityEvent = {
	id: "event-upcoming",
	name: "Security Design Meetup",
	description: "A meetup",
	startDate: new Date(Date.now() + 86_400_000).toISOString(),
	endDate: new Date(Date.now() + 86_400_000).toISOString(),
	location: "Oslo",
	type: "meetup",
	externalEvent: false,
	deltaEvent: false,
	link: "https://example.test/events/security",
};

const archivedEvents: SecurityEvent[] = Array.from(
	{ length: 7 },
	(_, index) => {
		const date = new Date();
		date.setDate(date.getDate() - index - 1);
		const day = date.toISOString().slice(0, 10);
		return {
			id: `event-archived-${index + 1}`,
			name: `Archived ${index + 1}`,
			description: "An archived event",
			startDate: day,
			endDate: day,
			location: "Oslo",
			type: "event",
			externalEvent: false,
			deltaEvent: false,
			allDay: true,
		};
	},
);

const participant: ProgramParticipant = {
	id: "participant-1",
	email: me.username,
	fullname: participantName,
	teams: [],
	active: true,
	joinedAt: "2026-01-15T12:00:00Z",
	status: "ACTIVE",
};

const score: ParticipantSeasonScore = {
	season: {
		id: "season-2026",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	points: 9,
	level: "Novice",
	rank: 1,
};

const leaderboard: LeaderboardEntry[] = [
	{
		fullName: participantName,
		rank: 1,
		points: 9,
		level: "Novice",
	},
];

describe("HackerOverview", () => {
	beforeEach(() => {
		vi.mocked(Apies.fetchEvents).mockResolvedValue([
			upcomingEvent,
			...archivedEvents,
		]);
		vi.mocked(Apies.fetchMembership).mockResolvedValue(participant);
		vi.mocked(Apies.getParticipantSeasonScore).mockResolvedValue(score);
		vi.mocked(Apies.getLeaderboard).mockResolvedValue(leaderboard);
	});

	it("renders real participant, event, season, and leaderboard data", async () => {
		render(<HackerOverview info={me} />);

		expect(
			await screen.findByRole("heading", { name: "ACCESS GRANTED" }),
		).toBeInTheDocument();
		expect(screen.getAllByText("0x4D4")).toHaveLength(2);
		expect(screen.getByText("15.01.2026")).toBeInTheDocument();
		const eventLink = screen.getByRole("link", {
			name: /Security Design Meetup/,
		});
		expect(eventLink).toHaveAttribute("href", upcomingEvent.link);
		expect(eventLink).toHaveAttribute("title", upcomingEvent.name);
		expect(eventLink).toHaveTextContent("security_design_meetup.evt");
		for (const event of archivedEvents.slice(0, 5)) {
			expect(
				screen.getByRole("link", { name: new RegExp(event.name) }),
			).toBeInTheDocument();
		}
		for (const event of archivedEvents.slice(5)) {
			expect(
				screen.queryByRole("link", { name: new RegExp(event.name) }),
			).not.toBeInTheDocument();
		}
		expect(screen.getByRole("img", { name: "9 points" })).toHaveTextContent(
			"0x09",
		);
		expect(screen.getAllByText("SCRIPT KIDDIE")).toHaveLength(2);
		expect(screen.getAllByText(participantName)).toHaveLength(2);
		expect(
			screen.getByRole("progressbar", { name: /upgrading to PACKET SNIFFER/ }),
		).toHaveAttribute("aria-valuenow", "9");
	});
});

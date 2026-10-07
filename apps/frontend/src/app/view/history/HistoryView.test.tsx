import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type { HistoryEntry } from "@/app/utils/Variables";
import { HistoryView } from "./HistoryView";

const entry: HistoryEntry = {
	id: "entry-1",
	action: "CREDIT_AWARDED",
	outcome: "SUCCEEDED",
	recordedAt: "2026-10-06T10:00:00Z",
	occurredAt: "2026-10-06T10:00:00Z",
	details: {
		creditType: "SLACK_WEEK",
		points: 9,
		sourceReference: "channel:timestamp",
	},
};

afterEach(() => {
	vi.restoreAllMocks();
	window.history.replaceState(null, "", "/history");
});

beforeEach(() => {
	vi.spyOn(Apies, "getHistory").mockResolvedValue({
		entries: [entry],
		nextCursor: null,
	});
	vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
		season: {
			id: "season",
			startsOn: "2026-01-01",
			endsOn: null,
			nextResetDate: "2027-01-01",
		},
		points: 10,
		level: "Novice",
		rank: 1,
	});
	vi.spyOn(Apies, "fetchMembership").mockResolvedValue({
		id: "participant",
		email: "person@nav.no",
		fullname: "Example Person",
		teams: [],
		active: true,
		joinedAt: "2026-01-01",
		status: "ACTIVE",
	});
});

describe("HistoryView", () => {
	it("shows participant history with summary, source copy and no admin fields", async () => {
		const getHistory = vi.spyOn(Apies, "getHistory");
		render(<HistoryView />);

		expect(await screen.findByText("Slack participation")).toBeInTheDocument();
		expect(screen.getByText("channel:timestamp")).toBeInTheDocument();
		expect(screen.getAllByText("+9")).toHaveLength(1);
		expect(screen.getByText("Member since")).toBeInTheDocument();
		expect(screen.getByText("Points earned").nextElementSibling).toHaveTextContent("10");
		expect(screen.queryByText(/Actor:/)).not.toBeInTheDocument();
		expect(getHistory).toHaveBeenCalledWith(false, "", null);
	});

	it("describes activation and deactivation using the recorded resulting membership status", async () => {
		vi.spyOn(Apies, "getHistory").mockResolvedValue({
			entries: [
				{
					...entry,
					id: "activated",
					action: "PARTICIPATION_STATUS_CHANGED",
					details: { status: "ACTIVE" },
				},
				{
					...entry,
					id: "deactivated",
					action: "PARTICIPATION_STATUS_CHANGED",
					details: { status: "DEACTIVATED" },
				},
			],
			nextCursor: null,
		});
		render(<HistoryView />);

		expect(await screen.findByRole("heading", { name: "Membership activated" })).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "Membership deactivated" })).toBeInTheDocument();
	});

	it("filters entries and reflects the selected type in the URL", async () => {
		vi.spyOn(Apies, "getHistory").mockResolvedValue({
			entries: [
				entry,
				{
					...entry,
					id: "adjustment-1",
					action: "POINTS_ADJUSTED",
					details: { points: -2, reason: "Correction" },
				},
			],
			nextCursor: null,
		});
		render(<HistoryView />);

		await screen.findByText("Correction");
		fireEvent.click(screen.getByRole("radio", { name: "Adjustments" }));

		expect(await screen.findByText("Points adjusted")).toBeInTheDocument();
		expect(screen.queryByText("Credit awarded")).not.toBeInTheDocument();
		expect(window.location.search).toContain("type=adjustments");
	});

	it("loads 50 visible items at a time and reports history failures", async () => {
		vi.spyOn(Apies, "getHistory").mockResolvedValue({
			entries: Array.from({ length: 51 }, (_, index) => ({
				...entry,
				id: `entry-${index}`,
			})),
			nextCursor: null,
		});
		const { unmount } = render(<HistoryView />);
		expect(await screen.findByRole("button", { name: "Load older" })).toBeInTheDocument();
		unmount();

		vi.spyOn(Apies, "getHistory").mockRejectedValue(new Error("Unavailable"));
		render(<HistoryView />);
		expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't fetch history");
	});
});

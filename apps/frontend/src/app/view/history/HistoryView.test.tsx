import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { HistoryView } from "./HistoryView";

const seasons = [
	{ id: "season-2026", startsOn: "2026-01-01", endsOn: null },
	{ id: "season-2025", startsOn: "2025-01-01", endsOn: "2025-12-31" },
];
const summary = {
	points: 8,
	tier: "Novice",
	rank: 1,
	breakdown: {
		slack: 4,
		deltaRegistration: 0,
		githubCommit: 0,
		githubPullRequest: 0,
		securityEvent: 0,
		adjustments: 4,
	},
	seasons,
};
const participantEntries = [
	{
		kind: "credit" as const,
		occurredAt: "2026-10-06T10:00:00Z",
		creditType: "SLACK_WEEK" as const,
		points: 4,
		displayName: "Weekly participation",
		action: null,
	},
	{
		kind: "adjustment" as const,
		occurredAt: "2026-10-06T09:00:00Z",
		creditType: null,
		points: 4,
		displayName: null,
		action: null,
	},
];

afterEach(() => {
	vi.restoreAllMocks();
	window.history.replaceState(null, "", "/history");
});

beforeEach(() => {
	vi.spyOn(Apies, "getScoreHistorySummary").mockResolvedValue(summary);
	vi.spyOn(Apies, "getScoreHistoryPage").mockResolvedValue({
		entries: participantEntries,
		nextCursor: null,
	});
});

describe("participant score history", () => {
	it("shows summary and safe participant entries without operational details", async () => {
		render(<HistoryView />);

		expect(await screen.findAllByText("Slack participation")).toHaveLength(2);
		expect(screen.getByText(/Weekly participation/)).toBeInTheDocument();
		expect(screen.getAllByText("+4")).toHaveLength(3);
		expect(
			screen.getByText("Season points").nextElementSibling,
		).toHaveTextContent("8");
		expect(
			screen.queryByText(/source reference|reason|credit id|actor/i),
		).not.toBeInTheDocument();
		expect(Apies.getScoreHistorySummary).toHaveBeenCalledWith(
			"participant",
			undefined,
			undefined,
		);
		expect(Apies.getScoreHistoryPage).toHaveBeenCalledWith(
			"participant",
			undefined,
			expect.objectContaining({
				season: "season-2026",
				type: "all",
				limit: 25,
			}),
		);
	});

	it("updates season and filter URL state and requests the matching page", async () => {
		render(<HistoryView />);
		await screen.findByText(/Weekly participation/);

		fireEvent.change(screen.getByLabelText("Season"), {
			target: { value: "season-2025" },
		});
		await waitFor(() =>
			expect(Apies.getScoreHistorySummary).toHaveBeenLastCalledWith(
				"participant",
				undefined,
				"season-2025",
			),
		);
		fireEvent.click(screen.getByRole("radio", { name: "Adjustments" }));
		await waitFor(() =>
			expect(Apies.getScoreHistoryPage).toHaveBeenLastCalledWith(
				"participant",
				undefined,
				expect.objectContaining({
					season: "season-2025",
					type: "adjustment",
				}),
			),
		);
		expect(window.location.search).toContain("season=season-2025");
		expect(window.location.search).toContain("type=adjustment");
	});

	it("loads another cursor page and reports request failures", async () => {
		vi.spyOn(Apies, "getScoreHistoryPage")
			.mockResolvedValueOnce({
				entries: participantEntries,
				nextCursor: "cursor-1",
			})
			.mockResolvedValueOnce({
				entries: [participantEntries[0]],
				nextCursor: null,
			});
		render(<HistoryView />);
		await screen.findByText(/Weekly participation/);
		fireEvent.click(screen.getByRole("button", { name: "Load older" }));
		await waitFor(() =>
			expect(Apies.getScoreHistoryPage).toHaveBeenLastCalledWith(
				"participant",
				undefined,
				expect.objectContaining({ cursor: "cursor-1" }),
			),
		);
		expect(screen.getAllByText(/Weekly participation/)).toHaveLength(2);

		vi.restoreAllMocks();
		vi.spyOn(Apies, "getScoreHistorySummary").mockRejectedValue(
			new Error("Unavailable"),
		);
		render(<HistoryView />);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't load score history",
		);
	});
});

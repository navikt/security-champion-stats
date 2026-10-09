import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type { AdminScoreHistoryEntry } from "@/app/utils/Variables";
import { HistoryView } from "./HistoryView";

afterEach(() => {
	vi.unstubAllGlobals();
	window.history.replaceState(null, "", "/history");
});

beforeEach(() => {
	vi.stubGlobal(
		"fetch",
		vi.fn((input: RequestInfo | URL) => {
			const path = String(input);
			if (path === "/api/me/score-summary") {
				return Promise.resolve(
					Response.json({
						points: 1,
						tier: "Novice",
						rank: 1,
						breakdown: {
							slack: 1,
							deltaRegistration: 0,
							githubCommit: 0,
							githubPullRequest: 0,
							securityEvent: 0,
							adjustments: 0,
						},
						seasons: [
							{
								id: "season",
								startsOn: "2026-01-01",
								endsOn: null,
							},
						],
					}),
				);
			}
			if (path === "/api/me/score-history?type=all&season=season&limit=25") {
				return Promise.resolve(
					Response.json({
						entries: [
							{
								kind: "credit",
								occurredAt: "2026-10-06T10:00:00Z",
								creditType: "SLACK_WEEK",
								points: 1,
								displayName: null,
								action: null,
							},
						],
						nextCursor: null,
					}),
				);
			}
			throw new Error(`Unexpected history request: ${path}`);
		}),
	);
});

it("uses the participant summary and privacy-safe history API contract", async () => {
	render(<HistoryView />);

	expect(await screen.findAllByText("Slack participation")).toHaveLength(2);
	expect(
		screen.queryByText(/source reference|reason|credit id|admin/i),
	).not.toBeInTheDocument();
	expect(fetch).toHaveBeenCalledWith("/api/me/score-summary");
	expect(fetch).toHaveBeenCalledWith(
		"/api/me/score-history?type=all&season=season&limit=25",
	);
});

it("rejects admin history requests without a participant ID", async () => {
	const fetch = vi.fn();
	vi.stubGlobal("fetch", fetch);

	await expect(Apies.getScoreHistorySummary("admin")).rejects.toThrow(
		"A participant is required",
	);
	await expect(
		Apies.getScoreHistoryPage<AdminScoreHistoryEntry>("admin", undefined, {
			type: "all",
		}),
	).rejects.toThrow("A participant is required");
	expect(fetch).not.toHaveBeenCalled();
});

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminParticipantScore,
	ParticipantScoringHistory,
	ScoringHistoryEntry,
} from "@/app/utils/Variables";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ParticipantScoreHistoryModal } from "./ParticipantScoreHistoryModal";

const participant: AdminParticipantScore = {
	participantId: "participant-1",
	fullName: "Example Person",
	email: "person@nav.no",
	active: false,
	points: 0,
	level: "Novice",
};
const credit: ScoringHistoryEntry = {
	id: "credit-1",
	type: "CREDIT",
	recordedAt: "2025-12-01T10:00:00Z",
	activityAt: "2025-11-30T23:00:00Z",
	seasonId: "old-season",
	seasonStartsOn: "2025-01-01",
	seasonEndsOn: "2025-12-31",
	points: 3,
	creditType: "GITHUB_PULL_REQUEST",
	sourceReference: "playbook:pull:1",
	sourceCreditId: null,
	reason: null,
	actorNavNoEmail: null,
	revokedAt: "2026-10-01T12:00:00Z",
};
const history: ParticipantScoringHistory = {
	currentSeasonId: "current-season",
	seasons: [
		{
			id: "current-season",
			startsOn: "2026-01-01",
			endsOn: null,
			points: 0,
			creditPoints: {
				SLACK_WEEK: 0,
				DELTA_REGISTRATION: 0,
				GITHUB_COMMIT: 0,
				GITHUB_PULL_REQUEST: 0,
				SECURITY_EVENT_CONTRIBUTION: 0,
			},
			adjustmentPoints: 0,
			scoringRulePoints: 0,
		},
		{
			id: "old-season",
			startsOn: "2025-01-01",
			endsOn: "2025-12-31",
			points: -1,
			creditPoints: {
				SLACK_WEEK: 0,
				DELTA_REGISTRATION: 0,
				GITHUB_COMMIT: 0,
				GITHUB_PULL_REQUEST: 3,
				SECURITY_EVENT_CONTRIBUTION: 0,
			},
			adjustmentPoints: -6,
			scoringRulePoints: 2,
		},
	],
	entries: [
		{
			...credit,
			id: "adjustment-1",
			type: "ADJUSTMENT",
			recordedAt: "2026-10-01T12:00:00Z",
			activityAt: null,
			points: -6,
			sourceCreditId: credit.id,
			reason: "Revoke duplicate contribution",
			actorNavNoEmail: "admin@nav.no",
			revokedAt: null,
		},
		{
			...credit,
			id: "rule-1",
			type: "SCORING_RULE_CHANGE",
			recordedAt: "2026-09-01T10:00:00Z",
			activityAt: null,
			points: 2,
			sourceCreditId: credit.id,
			reason: "Scoring rule update",
			actorNavNoEmail: "rules@nav.no",
			revokedAt: null,
		},
		credit,
	],
};

describe("ParticipantScoreHistoryModal", () => {
	afterEach(() => vi.restoreAllMocks());

	it("omits empty source paragraphs for Slack credits and their linked corrections", async () => {
		vi.spyOn(Apies, "getParticipantScoringHistory").mockResolvedValue({
			...history,
			entries: history.entries.map((entry) => ({
				...entry,
				creditType: "SLACK_WEEK",
			})),
		});
		render(
			<ParticipantScoreHistoryModal
				participant={participant}
				onClose={vi.fn()}
			/>,
		);
		const entries = await screen.findByRole("list", {
			name: "Scoring history entries",
		});

		for (const paragraph of entries.querySelectorAll("p")) {
			expect(paragraph.textContent?.trim()).not.toBe("");
		}
		expect(
			within(entries).getAllByText("Linked credit ID: credit-1"),
		).toHaveLength(2);
	});

	it("shows the full ledger and a reconciled breakdown with corrections in their original season", async () => {
		vi.spyOn(Apies, "getParticipantScoringHistory").mockResolvedValue(history);
		render(
			<ParticipantScoreHistoryModal
				participant={participant}
				onClose={vi.fn()}
			/>,
		);
		expect(
			await screen.findByText("Current-season points:", { exact: false }),
		).toHaveTextContent("Current-season points: 0");
		const breakdown = screen.getByRole("table", {
			name: "All seasons: -1 points",
		});
		expect(
			within(breakdown).getByRole("row", { name: "GitHub pull request 3" }),
		).toBeInTheDocument();
		expect(
			within(breakdown).getByRole("row", {
				name: "Point adjustments (including revocations) -6",
			}),
		).toBeInTheDocument();
		expect(
			within(breakdown).getByRole("row", { name: "Scoring-rule changes +2" }),
		).toBeInTheDocument();
		expect(
			screen.getByText("Reason: Revoke duplicate contribution"),
		).toBeInTheDocument();
		expect(screen.getByText("Administrator: admin@nav.no")).toBeInTheDocument();
		expect(screen.getAllByText("Linked credit ID: credit-1")).toHaveLength(2);
		expect(screen.getByText("Source: playbook:pull:1")).toBeInTheDocument();
		expect(screen.getByText(/Activity date:/)).toHaveTextContent(
			"1 Dec 2025, 00:00",
		);
		expect(screen.getByText(/Revoked:/)).toHaveTextContent("1 Oct 2026, 14:00");
		const entries = within(
			screen.getByRole("list", { name: "Scoring history entries" }),
		).getAllByRole("listitem");
		expect(entries[0]).toHaveTextContent("Point adjustment: -6 points");
		expect(entries[1]).toHaveTextContent("Scoring-rule change: +2 points");
		expect(entries[2]).toHaveTextContent("GitHub pull request: +3 points");

		fireEvent.change(screen.getByRole("combobox", { name: "Season" }), {
			target: { value: "old-season" },
		});
		expect(
			screen.getByRole("table", { name: "Selected season: -1 points" }),
		).toBeInTheDocument();
		expect(screen.getByText("Showing 3 of 3 entries.")).toBeInTheDocument();
		fireEvent.change(screen.getByRole("combobox", { name: "Season" }), {
			target: { value: "current-season" },
		});
		expect(
			screen.getByRole("table", { name: "Selected season: 0 points" }),
		).toBeInTheDocument();
		expect(
			screen.getByText("No scoring history for this selection."),
		).toBeInTheDocument();
		expect(
			screen.queryByText("Administrator: admin@nav.no"),
		).not.toBeInTheDocument();
	});

	it("reveals longer histories without truncating totals and resets the visible count on season changes", async () => {
		vi.spyOn(Apies, "getParticipantScoringHistory").mockResolvedValue({
			...history,
			entries: Array.from({ length: 51 }, (_, index) => ({
				...credit,
				id: `credit-${index}`,
			})),
		});
		render(
			<ParticipantScoreHistoryModal
				participant={participant}
				onClose={vi.fn()}
			/>,
		);
		expect(
			await screen.findByText("Showing 50 of 51 entries."),
		).toBeInTheDocument();
		expect(
			screen.getByRole("table", { name: "All seasons: -1 points" }),
		).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Show more" }));
		expect(screen.getByText("Showing 51 of 51 entries.")).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Show more" }),
		).not.toBeInTheDocument();
		fireEvent.change(screen.getByRole("combobox", { name: "Season" }), {
			target: { value: "old-season" },
		});
		expect(screen.getByText("Showing 50 of 51 entries.")).toBeInTheDocument();
	});

	it("reports loading failures instead of empty history and supports retry", async () => {
		vi.spyOn(console, "error").mockImplementation(() => {});
		const getHistory = vi
			.spyOn(Apies, "getParticipantScoringHistory")
			.mockRejectedValueOnce(new Error("Unavailable"))
			.mockResolvedValueOnce(history);
		render(
			<ParticipantScoreHistoryModal
				participant={participant}
				onClose={vi.fn()}
			/>,
		);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't load score history",
		);
		expect(
			screen.queryByText("No scoring history for this selection."),
		).not.toBeInTheDocument();
		expect(screen.queryByRole("table")).not.toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Retry" }));
		expect(
			await screen.findByRole("table", { name: "All seasons: -1 points" }),
		).toBeInTheDocument();
		expect(getHistory).toHaveBeenCalledTimes(2);
	});

	it("does not show a previous participant's history after changing participants while loading", async () => {
		let resolveFirst: (result: ParticipantScoringHistory) => void = () => {};
		vi.spyOn(Apies, "getParticipantScoringHistory")
			.mockImplementationOnce(
				() =>
					new Promise((resolve) => {
						resolveFirst = resolve;
					}),
			)
			.mockResolvedValueOnce({
				...history,
				entries: [],
				seasons: [history.seasons[0]],
			});
		const { rerender } = render(
			<ParticipantScoreHistoryModal
				participant={participant}
				onClose={vi.fn()}
			/>,
		);
		expect(screen.getByRole("status")).toHaveTextContent(
			"Loading score history",
		);
		rerender(
			<ParticipantScoreHistoryModal
				participant={{
					...participant,
					participantId: "participant-2",
					fullName: "Other Person",
				}}
				onClose={vi.fn()}
			/>,
		);
		expect(
			await screen.findByText("No scoring history for this selection."),
		).toBeInTheDocument();
		await act(async () => resolveFirst(history));
		expect(
			screen.queryByText("Administrator: admin@nav.no"),
		).not.toBeInTheDocument();
		expect(
			screen.getByRole("dialog", { name: "Score history for Other Person" }),
		).toBeInTheDocument();
	});
});

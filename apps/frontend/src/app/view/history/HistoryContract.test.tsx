import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { HistoryView } from "./HistoryView";

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

beforeEach(() => {
	vi.spyOn(Apies, "getParticipantSeasonScore").mockResolvedValue({
		season: {
			id: "season",
			startsOn: "2026-01-01",
			endsOn: null,
			nextResetDate: "2027-01-01",
		},
		points: 1,
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

it("renders the backend personal history array and does not leak operational fields", async () => {
	const fetch = vi.fn().mockResolvedValue(
		Response.json([
			{
				id: "credit-1",
				occurredAt: "2026-10-06T10:00:00Z",
				type: "CREDIT",
				action: "CREDIT_AWARDED",
				status: null,
				creditType: "SLACK_WEEK",
				points: 1,
				sourceReference: "channel:timestamp",
				reason: null,
			},
		]),
	);
	vi.stubGlobal("fetch", fetch);
	render(<HistoryView />);

	expect(await screen.findByText("Slack participation")).toBeInTheDocument();
	expect(screen.getByText("channel:timestamp")).toBeInTheDocument();
	expect(fetch).toHaveBeenCalledWith("/api/history");
	expect(screen.queryByRole("button", { name: "Older entries" })).not.toBeInTheDocument();
	expect(screen.queryByText(/Actor:/)).not.toBeInTheDocument();
});

it("renders participant adjustments and membership changes from the real backend contract", async () => {
	vi.stubGlobal(
		"fetch",
		vi.fn().mockResolvedValue(
			Response.json([
				{
					id: "adjustment-1",
					occurredAt: "2026-10-06T10:00:00Z",
					type: "ADJUSTMENT",
					action: "POINTS_ADJUSTED",
					status: null,
					creditType: null,
					points: -2,
					sourceReference: null,
					reason: "Duplicate credit corrected",
				},
				{
					id: "membership-1",
					occurredAt: "2026-10-06T09:00:00Z",
					type: "MEMBERSHIP",
					action: "PARTICIPANT_LEFT",
					status: "LEFT",
					creditType: null,
					points: null,
					sourceReference: null,
					reason: null,
				},
				{
					id: "membership-2",
					occurredAt: "2026-10-06T08:00:00Z",
					type: "MEMBERSHIP",
					action: "PARTICIPATION_STATUS_CHANGED",
					status: "DEACTIVATED",
					creditType: null,
					points: null,
					sourceReference: null,
					reason: null,
				},
			]),
		),
	);
	render(<HistoryView />);

	expect(await screen.findByText("Duplicate credit corrected")).toBeInTheDocument();
	expect(screen.getAllByText("−2")).toHaveLength(1);
	expect(screen.getByRole("heading", { name: "Left program" })).toBeInTheDocument();
	expect(screen.getByRole("heading", { name: "Membership deactivated" })).toBeInTheDocument();
});

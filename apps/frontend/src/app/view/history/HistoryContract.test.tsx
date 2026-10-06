import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HistoryView } from "./HistoryView";

afterEach(() => vi.unstubAllGlobals());

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

	expect(await screen.findByText("Weekly Slack participation")).toBeInTheDocument();
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
			]),
		),
	);
	render(<HistoryView />);

	expect(await screen.findByText("Duplicate credit corrected")).toBeInTheDocument();
	expect(screen.getByText("-2")).toBeInTheDocument();
	expect(screen.getByText("LEFT")).toBeInTheDocument();
	expect(screen.getByRole("heading", { name: "Participant left" })).toBeInTheDocument();
});

it("uses the backend audit page shape and q/page/size parameters through the real API helper", async () => {
	const fetch = vi.fn().mockImplementation(async () =>
		Response.json({
			items: [
				{
					id: "audit-1",
					createdAt: "2026-10-06T10:00:00Z",
					action: "SYNC_STARTED",
					outcome: "SUCCEEDED",
					actorNavNoEmail: "admin@nav.no",
					targetParticipantId: null,
					correlationId: "run-1",
					details: { job: "Slack scoring" },
				},
			],
			total: 51,
			page: fetch.mock.calls.length > 1 ? 1 : 0,
			size: 50,
		}),
	);
	vi.stubGlobal("fetch", fetch);
	render(<HistoryView admin />);

	expect(await screen.findByText(/Actor: admin@nav.no/)).toBeInTheDocument();
	expect(fetch).toHaveBeenCalledWith("/api/admin/audit?size=50&page=0");
	fireEvent.click(screen.getByRole("button", { name: "Older entries" }));
	await waitFor(() => expect(fetch).toHaveBeenLastCalledWith("/api/admin/audit?size=50&page=1"));
	expect(await screen.findByRole("button", { name: "Newer entries" })).toBeInTheDocument();
	fireEvent.change(screen.getByRole("textbox", { name: "Search audit trail" }), { target: { value: "Slack" } });
	fireEvent.click(screen.getByRole("button", { name: "Search" }));
	await waitFor(() => expect(fetch).toHaveBeenLastCalledWith("/api/admin/audit?size=50&page=0&q=Slack"));
});

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { HistoryEntry } from "@/app/utils/Variables";
import { HistoryView } from "./HistoryView";

const entry: HistoryEntry = {
	id: "entry-1",
	action: "CREDIT_AWARDED",
	outcome: "SUCCEEDED",
	recordedAt: "2026-10-06T10:00:00Z",
	occurredAt: "2026-10-05T09:00:00Z",
	details: { creditType: "SLACK_WEEK", points: 1, sourceReference: "channel:timestamp" },
	actor: "admin@nav.no",
	participantId: "participant-1",
	runId: "run-1",
};

afterEach(() => vi.restoreAllMocks());

describe("HistoryView", () => {
	it("shows score provenance without administrative fields in the participant view", async () => {
		const getHistory = vi.spyOn(Apies, "getHistory").mockResolvedValue({ entries: [entry], nextCursor: null });
		render(<HistoryView />);

		expect(await screen.findByText("Weekly Slack participation")).toBeInTheDocument();
		expect(screen.getByText("channel:timestamp")).toBeInTheDocument();
		expect(screen.getByText(/Activity time:/)).toBeInTheDocument();
		expect(screen.queryByText(/admin@nav.no/)).not.toBeInTheDocument();
		expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
		expect(getHistory).toHaveBeenCalledWith(false, "", null);
	});

	it("searches server-side and moves between history pages", async () => {
		const getHistory = vi
			.spyOn(Apies, "getHistory")
			.mockResolvedValueOnce({ entries: [entry], nextCursor: "entry-1" })
			.mockResolvedValueOnce({ entries: [{ ...entry, id: "older-1" }], nextCursor: null })
			.mockResolvedValueOnce({ entries: [entry], nextCursor: "entry-1" })
			.mockResolvedValue({ entries: [], nextCursor: null });
		render(<HistoryView admin />);

		expect(await screen.findByText(/Actor: admin@nav.no/)).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Older entries" }));
		await waitFor(() => expect(getHistory).toHaveBeenLastCalledWith(true, "", "entry-1"));
		fireEvent.click(await screen.findByRole("button", { name: "Newer entries" }));
		await waitFor(() => expect(getHistory).toHaveBeenLastCalledWith(true, "", null));
		fireEvent.change(screen.getByRole("textbox", { name: "Search audit trail" }), { target: { value: "SLACK" } });
		fireEvent.click(screen.getByRole("button", { name: "Search" }));

		expect(await screen.findByText("No history recorded.")).toBeInTheDocument();
		expect(getHistory).toHaveBeenLastCalledWith(true, "SLACK", null);
		expect(screen.queryByRole("button", { name: "Newer entries" })).not.toBeInTheDocument();
	});

	it("reports a fetch failure instead of showing an empty history and permits retry", async () => {
		const getHistory = vi
			.spyOn(Apies, "getHistory")
			.mockRejectedValueOnce(new Error("Unavailable"))
			.mockResolvedValue({ entries: [], nextCursor: null });
		render(<HistoryView />);

		expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't fetch history");
		expect(screen.queryByText("No history recorded.")).not.toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Retry" }));
		expect(await screen.findByText("No history recorded.")).toBeInTheDocument();
		expect(getHistory).toHaveBeenCalledTimes(2);
	});
});

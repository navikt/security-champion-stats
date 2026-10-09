import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type { AuditResponse } from "@/app/utils/Variables";
import { AdminAuditView } from "./AdminAuditView";

const auditPage: AuditResponse = {
	items: [
		{
			id: "event-1",
			createdAt: "2026-10-06T10:00:00Z",
			action: "SLACK_SCORING_SYNC_COMPLETED",
			outcome: "SUCCEEDED",
			actorNavNoEmail: "admin@nav.no",
			targetParticipantId: "participant-1",
			targetParticipantName: "Example Participant",
			correlationId: "12345678-1234-1234-1234-123456789012",
			details: { eventsScanned: "8", unmapped_authors: "0" },
		},
	],
	total: 51,
	page: 0,
	size: 50,
};

afterEach(() => {
	vi.restoreAllMocks();
	window.history.replaceState(null, "", "/appsec/audit");
});

describe("AdminAuditView", () => {
	it("omits an empty source paragraph for Slack credit audits", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockResolvedValue({
			...auditPage,
			items: [
				{
					...auditPage.items[0],
					action: "CREDIT_AWARDED",
					details: { creditType: "SLACK_WEEK", points: "1" },
				},
			],
		});
		const { container } = render(<AdminAuditView />);
		await screen.findByRole("button", { name: /Credit awarded/ });

		expect(container.querySelector(".auditView__panel > p")).toBeNull();
		expect(screen.getByText("SLACK_WEEK")).toBeInTheDocument();
	});

	it("shows date-only source context even without a source name URL or credit type", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockResolvedValue({
			...auditPage,
			items: [
				{
					...auditPage.items[0],
					details: { sourceOccurredAt: "2026-10-03T08:00:00Z" },
				},
			],
		});
		render(<AdminAuditView />);

		expect(await screen.findByText(/Activity:/)).toHaveTextContent(
			"3 Oct 2026, 10:00",
		);
	});

	it("shows an event name and date with a source link while retaining the technical reference", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockResolvedValue({
			...auditPage,
			items: [
				{
					...auditPage.items[0],
					action: "CREDIT_AWARDED",
					details: {
						creditType: "DELTA_REGISTRATION",
						sourceName: "Security workshop",
						sourceReference: "event-id",
						sourceUrl: "https://delta.nav.no/event/event-id",
						sourceOccurredAt: "2026-10-03T08:00:00Z",
					},
				},
			],
		});
		render(<AdminAuditView />);

		expect(
			await screen.findByRole("link", { name: "Security workshop" }),
		).toHaveAttribute("href", "https://delta.nav.no/event/event-id");
		expect(screen.getByText(/Event:/)).toHaveTextContent(
			"Event: 3 Oct 2026, 10:00",
		);
		expect(screen.getByText("event-id")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Credit awarded.*Security workshop/ }),
		).toHaveAttribute("aria-expanded", "true");
	});

	it("expands an accessible event row and filters the run through the search", async () => {
		const getAdminAudit = vi
			.spyOn(Apies, "getAdminAudit")
			.mockResolvedValue(auditPage);
		render(<AdminAuditView />);

		const row = await screen.findByRole("button", {
			name: /Slack scoring sync completed/,
		});
		expect(row).toHaveAttribute("aria-expanded", "true");
		expect(row).toHaveAttribute("aria-controls", "audit-panel-event-1");
		expect(screen.getByText("Events scanned")).toBeInTheDocument();
		expect(screen.getByText("8")).toBeInTheDocument();
		expect(await screen.findAllByText("admin@nav.no")).toHaveLength(2);
		expect(screen.getByText("Example Participant")).toBeInTheDocument();
		expect(screen.getByText("participant-1")).toBeInTheDocument();
		fireEvent.click(
			screen.getByRole("button", { name: "Show all events in this run" }),
		);

		await waitFor(() => {
			expect(getAdminAudit).toHaveBeenLastCalledWith(
				"12345678-1234-1234-1234-123456789012",
				"all",
				0,
			);
		});
		expect(screen.getByRole("textbox", { name: /Search events/ })).toHaveValue(
			"12345678-1234-1234-1234-123456789012",
		);
	});

	it("labels events without a human actor as system-generated", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockResolvedValue({
			...auditPage,
			items: [
				{
					...auditPage.items[0],
					actorNavNoEmail: null,
					targetParticipantId: null,
					targetParticipantName: null,
				},
			],
		});
		render(<AdminAuditView />);

		await screen.findByRole("button", { name: /Slack scoring sync completed/ });

		expect(screen.getAllByText("System")).toHaveLength(2);
		expect(screen.queryByText(/erased identity/i)).not.toBeInTheDocument();
	});

	it("filters categories and paginates using the backend page contract", async () => {
		const getAdminAudit = vi
			.spyOn(Apies, "getAdminAudit")
			.mockImplementation(async (_query, category, page) => ({
				...auditPage,
				items:
					category === "all" || category === "syncs" ? auditPage.items : [],
				page,
			}));
		render(<AdminAuditView />);

		await screen.findAllByText("admin@nav.no");
		fireEvent.click(screen.getByRole("radio", { name: "Syncs" }));
		await waitFor(() =>
			expect(getAdminAudit).toHaveBeenLastCalledWith("", "syncs", 0),
		);

		fireEvent.click(screen.getByRole("button", { name: "Older →" }));
		await waitFor(() =>
			expect(getAdminAudit).toHaveBeenLastCalledWith("", "syncs", 1),
		);
		expect(screen.getByText(/Showing 51–51 of 51 events/)).toBeInTheDocument();
	});

	it("does not reset an already selected page when the search debounce expires unchanged", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockImplementation(
			async (_query, _category, page) => ({
				...auditPage,
				page,
			}),
		);
		render(<AdminAuditView />);

		await screen.findByRole("button", { name: /Slack scoring sync completed/ });
		fireEvent.click(screen.getByRole("button", { name: "Older →" }));
		await waitFor(() =>
			expect(Apies.getAdminAudit).toHaveBeenLastCalledWith("", "all", 1),
		);

		await new Promise((resolve) => window.setTimeout(resolve, 300));
		expect(Apies.getAdminAudit).toHaveBeenLastCalledWith("", "all", 1);
	});

	it("limits audit searches to the backend's 100-character contract", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockResolvedValue(auditPage);
		render(<AdminAuditView />);
		await screen.findByRole("button", { name: /Slack scoring sync completed/ });
		expect(
			screen.getByRole("textbox", { name: /Search events/ }),
		).toHaveAttribute("maxLength", "100");
	});

	it("reports errors rather than showing empty results", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockRejectedValue(
			new Error("Unavailable"),
		);
		render(<AdminAuditView />);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't fetch audit events",
		);
	});
});

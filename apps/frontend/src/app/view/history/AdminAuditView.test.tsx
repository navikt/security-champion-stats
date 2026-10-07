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
		fireEvent.click(screen.getByRole("button", { name: "Show all events in this run" }));

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

	it("filters categories and paginates using the backend page contract", async () => {
		const getAdminAudit = vi
			.spyOn(Apies, "getAdminAudit")
			.mockImplementation(async (_query, category, page) => ({
				...auditPage,
				items: category === "all" || category === "syncs" ? auditPage.items : [],
				page,
			}));
		render(<AdminAuditView />);

		await screen.findAllByText("admin@nav.no");
		fireEvent.click(screen.getByRole("radio", { name: "Syncs" }));
		await waitFor(() => expect(getAdminAudit).toHaveBeenLastCalledWith("", "syncs", 0));

		fireEvent.click(screen.getByRole("button", { name: "Older →" }));
		await waitFor(() => expect(getAdminAudit).toHaveBeenLastCalledWith("", "syncs", 1));
		expect(screen.getByText(/Showing 51–51 of 51 events/)).toBeInTheDocument();
	});

	it("reports errors rather than showing empty results", async () => {
		vi.spyOn(Apies, "getAdminAudit").mockRejectedValue(new Error("Unavailable"));
		render(<AdminAuditView />);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't fetch audit events",
		);
	});
});

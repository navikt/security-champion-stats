import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { TeamCatalogRoleStatusView } from "./TeamCatalogRoleStatusView";

afterEach(() => vi.restoreAllMocks());

describe("TeamCatalogRoleStatusView", () => {
	it("lists confirmed missing and unknown participant statuses separately", async () => {
		vi.spyOn(Apies, "getAdminParticipants").mockResolvedValue([
			{
				id: "participant-1",
				fullname: "Ada Lovelace",
				email: "ada@nav.no",
				teams: [],
				active: true,
				joinedAt: "2026-01-01T00:00:00Z",
				status: "ACTIVE",
			},
			{
				id: "participant-2",
				fullname: "Mina Haugen",
				email: "mina@nav.no",
				teams: [],
				active: true,
				joinedAt: "2026-01-01T00:00:00Z",
				status: "ACTIVE",
			},
			{
				id: "participant-3",
				fullname: "Inactive User",
				email: "inactive@nav.no",
				teams: [],
				active: false,
				joinedAt: "2026-01-01T00:00:00Z",
				status: "DEACTIVATED",
			},
		]);
		vi.spyOn(Apies, "getTeamCatalogChampionStatus").mockResolvedValue({
			participants: [
				{ participantId: "participant-1", status: "ABSENT" },
				{ participantId: "participant-2", status: "UNKNOWN" },
			],
		});

		render(<TeamCatalogRoleStatusView />);

		expect(await screen.findByRole("heading", { name: "Missing Security Champion tag (1)" }))
			.toBeInTheDocument();
		expect(screen.getByText("Ada Lovelace")).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "Unknown status (1)" })).toBeInTheDocument();
		expect(screen.getByText("Mina Haugen")).toBeInTheDocument();
		expect(screen.queryByText("Inactive User")).not.toBeInTheDocument();
	});
});

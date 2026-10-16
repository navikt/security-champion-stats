import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { TeamCatalogRolesView } from "./TeamCatalogRolesView";

afterEach(() => vi.restoreAllMocks());

describe("TeamCatalogRolesView", () => {
	it("preselects existing tags and previews the redacted payload without writing", async () => {
		vi.spyOn(Apies, "getTeamCatalogRoles").mockResolvedValue({
			teams: [
				{ id: "team-a", name: "Alpha", isSecurityChampion: false },
				{ id: "team-b", name: "Beta", isSecurityChampion: true },
			],
			writeEnabled: false,
			dryRun: true,
		});
		const save = vi.spyOn(Apies, "saveTeamCatalogRoles").mockResolvedValue({
			teams: [
				{ id: "team-a", name: "Alpha", isSecurityChampion: false },
				{ id: "team-b", name: "Beta", isSecurityChampion: true },
			],
			writeEnabled: false,
			dryRun: true,
			updates: [{
				teamId: "team-a",
				teamName: "Alpha",
				operation: "ADD",
				status: "PREVIEW",
				payloadPreview: {
					id: "team-a",
					name: "Alpha",
					teamMemberCount: 2,
					currentMemberRoles: ["DEVELOPER", "SECURITY_CHAMPION"],
				},
			}],
		});
		const user = userEvent.setup();
		render(<TeamCatalogRolesView />);

		const alpha = await screen.findByRole("checkbox", { name: "Alpha" });
		const beta = screen.getByRole("checkbox", { name: "Beta" });
		expect(alpha).not.toBeChecked();
		expect(beta).toBeChecked();
		await user.click(alpha);
		await user.click(screen.getByRole("button", { name: "Preview team role changes" }));

		await waitFor(() => expect(save).toHaveBeenCalledWith(["team-b", "team-a"]));
		expect(await screen.findByText("Dry run complete. No Teamkatalogen changes were written."))
			.toBeInTheDocument();
		expect(screen.getByText("Redacted payload preview")).toBeInTheDocument();
		expect(screen.getByText(/redacted: 2 member records/)).toBeInTheDocument();
		expect(screen.getByText(/SECURITY_CHAMPION/)).toBeInTheDocument();
	});

	it("keeps failed changes selected so a participant can retry them", async () => {
		vi.spyOn(Apies, "getTeamCatalogRoles").mockResolvedValue({
			teams: [{ id: "team-a", name: "Alpha", isSecurityChampion: false }],
			writeEnabled: true,
			dryRun: false,
		});
		vi.spyOn(Apies, "saveTeamCatalogRoles").mockResolvedValue({
			teams: [{ id: "team-a", name: "Alpha", isSecurityChampion: false }],
			writeEnabled: true,
			dryRun: false,
			updates: [{
				teamId: "team-a",
				teamName: "Alpha",
				operation: "ADD",
				status: "FAILED",
				payloadPreview: {
					id: "team-a",
					name: "Alpha",
					teamMemberCount: 1,
					currentMemberRoles: ["SECURITY_CHAMPION"],
				},
			}],
		});
		const user = userEvent.setup();
		render(<TeamCatalogRolesView />);

		await user.click(await screen.findByRole("checkbox", { name: "Alpha" }));
		await user.click(screen.getByRole("button", { name: "Save team roles" }));

		expect(await screen.findByText("Some teams could not be updated. Review the results and retry."))
			.toBeInTheDocument();
		expect(screen.getByRole("checkbox", { name: "Alpha" })).toBeChecked();
		expect(screen.getByText("Add Security Champion tag: failed.")).toBeInTheDocument();
	});
});

import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import Page from "./page";

vi.mock("@/app/shared/hooks/UseMe", () => ({ useMe: vi.fn() }));
vi.mock("@/app/view/HomeView", () => ({
	MainView: () => <div>Employee home</div>,
}));

afterEach(() => vi.restoreAllMocks());

describe("Slack administration page", () => {
	it("mounts membership operations alongside existing mappings for administrators", async () => {
		authenticated(true);
		vi.spyOn(Apies, "getAdminParticipants").mockResolvedValue([]);
		vi.spyOn(Apies, "getSlackMappingOverview").mockResolvedValue({
			mappings: [],
			unmappedAuthors: [],
		});
		vi.spyOn(Apies, "getSlackMembershipConfiguration").mockResolvedValue({
			enabled: false,
			dryRun: true,
		});
		vi.spyOn(Apies, "getSlackMembershipAnnouncements").mockResolvedValue([]);

		render(<Page />);

		expect(
			await screen.findByRole("heading", { name: "Manage Slack mappings" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Membership sync is disabled."),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Preview membership changes" }),
		).toBeInTheDocument();
	});

	it("does not mount or fetch administration operations for non-administrators", async () => {
		authenticated(false);
		const mappings = vi.spyOn(Apies, "getSlackMappingOverview");
		const configuration = vi.spyOn(Apies, "getSlackMembershipConfiguration");
		const announcements = vi.spyOn(Apies, "getSlackMembershipAnnouncements");

		render(<Page />);

		expect(screen.getByText("Employee home")).toBeInTheDocument();
		await waitFor(() => {
			expect(mappings).not.toHaveBeenCalled();
			expect(configuration).not.toHaveBeenCalled();
			expect(announcements).not.toHaveBeenCalled();
		});
	});
});

function authenticated(isAdmin: boolean) {
	vi.mocked(useMe).mockReturnValue({
		me: {
			username: "employee",
			displayName: "Example",
			isAdmin,
			isParticipant: false,
			isActive: false,
		},
		loading: false,
	});
}

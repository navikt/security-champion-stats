import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import PersonalHistoryPage from "@/app/history/page";
import AdminHistoryPage from "@/app/appsec/audit/page";

const state = vi.hoisted(() => ({
	loading: false,
	me: {
		username: "person@nav.no",
		displayName: null,
		isAdmin: false,
		isParticipant: true,
		isActive: false,
	},
}));
vi.mock("@/app/shared/hooks/UseMe", () => ({
	useMe: () => state,
}));

beforeEach(() => {
	vi.restoreAllMocks();
	state.me.isParticipant = true;
	state.me.isAdmin = false;
	vi.spyOn(Apies, "getHistory").mockResolvedValue({
		entries: [],
		nextCursor: null,
	});
});

describe("History page access", () => {
	it("lets an inactive enrolled participant access their own history", async () => {
		render(<PersonalHistoryPage />);
		expect(await screen.findByText("No history recorded.")).toBeInTheDocument();
		expect(Apies.getHistory).toHaveBeenCalledWith(false, "", null);
	});

	it("does not fetch history for an employee who has not enrolled", () => {
		state.me.isParticipant = false;
		render(<PersonalHistoryPage />);
		expect(
			screen.getByText("Join the program to view your history."),
		).toBeInTheDocument();
		expect(Apies.getHistory).not.toHaveBeenCalled();
	});

	it("does not fetch the audit trail for a non-admin", () => {
		render(<AdminHistoryPage />);
		expect(
			screen.getByText("Administrator access is required."),
		).toBeInTheDocument();
		expect(Apies.getHistory).not.toHaveBeenCalled();
	});

	it("lets an administrator read the programme-wide audit without being a participant", async () => {
		state.me.isParticipant = false;
		state.me.isAdmin = true;
		render(<AdminHistoryPage />);
		expect(await screen.findByText("No history recorded.")).toBeInTheDocument();
		expect(Apies.getHistory).toHaveBeenCalledWith(true, "", null);
	});
});

import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { Me, ProgramParticipant } from "@/app/utils/Variables";
import { MembershipView } from "./MembershipView";

const me: Me = {
	username: "person@nav.no",
	displayName: null,
	isAdmin: false,
	isParticipant: true,
	isActive: true,
};
const participant: ProgramParticipant = {
	id: "participant-1",
	email: me.username,
	fullname: "Example Person",
	teams: [],
	joinedAt: "2026-01-01T00:00:00Z",
	active: true,
	status: "ACTIVE",
};

afterEach(() => vi.restoreAllMocks());

describe("MembershipView", () => {
	it("updates active status after leaving even if refreshing membership fails", async () => {
		vi.spyOn(Apies, "fetchMembership")
			.mockResolvedValueOnce(participant)
			.mockResolvedValue(null);
		vi.spyOn(Apies, "leaveProgram").mockResolvedValue();
		const onMembershipChanged = vi.fn();
		render(<MembershipView me={me} onMembershipChanged={onMembershipChanged} />);

		fireEvent.click(await screen.findByRole("button", { name: "Leave program" }));
		fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Leave program" }));

		expect(await screen.findByText("We couldn't fetch your participant details. Try again later."))
			.toBeInTheDocument();
		expect(onMembershipChanged).toHaveBeenLastCalledWith({ ...me, isActive: false });
	});

	it("requires confirmation before leaving and refreshes active status", async () => {
		vi.spyOn(Apies, "fetchMembership")
			.mockResolvedValueOnce(participant)
			.mockResolvedValue({ ...participant, active: false, status: "LEFT" });
		const leave = vi.spyOn(Apies, "leaveProgram").mockResolvedValue();
		const onMembershipChanged = vi.fn();
		render(<MembershipView me={me} onMembershipChanged={onMembershipChanged} />);

		fireEvent.click(await screen.findByRole("button", { name: "Leave program" }));
		expect(leave).not.toHaveBeenCalled();
		const dialog = screen.getByRole("dialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Leave program" }));

		expect(await screen.findByRole("button", { name: "Rejoin program" })).toBeInTheDocument();
		expect(leave).toHaveBeenCalledOnce();
		expect(onMembershipChanged).toHaveBeenLastCalledWith({ ...me, isActive: false });
		expect(screen.getByRole("link", { name: "My history" })).toHaveAttribute("href", "/history");
	});

	it("retains the active view and reports a failed departure", async () => {
		vi.spyOn(Apies, "fetchMembership").mockResolvedValue(participant);
		vi.spyOn(Apies, "leaveProgram").mockRejectedValue(new Error("Departure failed"));
		render(<MembershipView me={me} />);

		fireEvent.click(await screen.findByRole("button", { name: "Leave program" }));
		fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Leave program" }));

		expect(await screen.findByRole("alert")).toHaveTextContent("Departure failed");
		expect(screen.getByText("Active participant")).toBeInTheDocument();
	});

	it("lets a voluntary leaver rejoin with the same membership", async () => {
		vi.spyOn(Apies, "fetchMembership")
			.mockResolvedValueOnce({ ...participant, active: false, status: "LEFT" })
			.mockResolvedValue(participant);
		const rejoin = vi.spyOn(Apies, "joinProgram").mockResolvedValue(true);
		render(<MembershipView me={{ ...me, isActive: false }} />);

		fireEvent.click(await screen.findByRole("button", { name: "Rejoin program" }));

		await waitFor(() => expect(screen.getByText("Active participant")).toBeInTheDocument());
		expect(rejoin).toHaveBeenCalledOnce();
	});

	it("does not offer self-reactivation after admin deactivation", async () => {
		vi.spyOn(Apies, "fetchMembership").mockResolvedValue({
			...participant,
			active: false,
			status: "DEACTIVATED",
		});
		render(<MembershipView me={{ ...me, isActive: false }} />);

		expect(await screen.findByText("A program administrator has deactivated your participation.")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Rejoin program" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Leave program" })).not.toBeInTheDocument();
	});
});

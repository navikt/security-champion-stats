import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { AdminProgramParticipant } from "@/app/utils/Variables";
import { ManageParticipantsView } from "./ManageParticipantsView";

const participants: AdminProgramParticipant[] = [
	{
		id: "participant-1",
		email: "person@nav.no",
		fullname: "Example Person",
		teams: ["Team A"],
		active: true,
		status: "ACTIVE",
		joinedAt: "2026-01-01T00:00:00Z",
	},
];

describe("ManageParticipantsView", () => {
	it("distinguishes voluntary departure and lets admins prevent self-rejoin", async () => {
		const updateStatus = vi.spyOn(Apies, "updateParticipantStatus").mockResolvedValue(204);
		render(<ManageParticipantsView participants={[{ ...participants[0], active: false, status: "LEFT" }]} />);

		expect(screen.getByText("Left program")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Deactivate" }));

		expect(await screen.findByText("Deactivated")).toBeInTheDocument();
		expect(updateStatus).toHaveBeenCalledWith("participant-1", false);
		updateStatus.mockRestore();
	});

	it("should deactivate a participant through the admin operation", async () => {
		const updateStatus = vi.spyOn(Apies, "updateParticipantStatus").mockResolvedValue(204);
		render(<ManageParticipantsView participants={participants} />);

		fireEvent.click(screen.getByRole("button", { name: "Deactivate" }));

		expect(updateStatus).toHaveBeenCalledWith("participant-1", false);
		expect(await screen.findByText("Deactivated")).toBeInTheDocument();

		updateStatus.mockRestore();
	});

	it("should require a reason and remove a permanently deleted participant", async () => {
		const deleteParticipant = vi.spyOn(Apies, "deleteParticipant").mockResolvedValue(204);
		render(<ManageParticipantsView participants={participants} />);

		fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
		fireEvent.click(screen.getByRole("button", { name: "Delete participant" }));
		expect(await screen.findByText("Enter a reason.")).toBeInTheDocument();
		expect(deleteParticipant).not.toHaveBeenCalled();

		fireEvent.change(screen.getByRole("textbox", { name: "Reason for deletion" }), {
			target: { value: "Requested by employee" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Delete participant" }));

		await waitFor(() =>
			expect(deleteParticipant).toHaveBeenCalledWith(
				"participant-1",
				"Requested by employee",
			),
		);
		await waitFor(() =>
			expect(screen.queryByText("Example Person")).not.toBeInTheDocument(),
		);

		deleteParticipant.mockRestore();
	});
});

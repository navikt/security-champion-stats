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
		joinedAt: "2026-01-01T00:00:00Z",
	},
];

describe("ManageParticipantsView", () => {
	it("should deactivate a participant through the admin operation", async () => {
		const updateStatus = vi.spyOn(Apies, "updateParticipantStatus").mockResolvedValue(204);
		render(<ManageParticipantsView participants={participants} />);

		fireEvent.click(screen.getByRole("button", { name: "deactivate" }));

		expect(updateStatus).toHaveBeenCalledWith("participant-1", false);
		expect(await screen.findByText("deactivated")).toBeInTheDocument();

		updateStatus.mockRestore();
	});

	it("should require a reason and remove a permanently deleted participant", async () => {
		const deleteParticipant = vi.spyOn(Apies, "deleteParticipant").mockResolvedValue(204);
		render(<ManageParticipantsView participants={participants} />);

		fireEvent.click(screen.getByRole("button", { name: "delete" }));
		fireEvent.click(screen.getByRole("button", { name: "confirmDelete" }));
		expect(await screen.findByText("reasonRequired")).toBeInTheDocument();
		expect(deleteParticipant).not.toHaveBeenCalled();

		fireEvent.change(screen.getByRole("textbox", { name: "reason" }), {
			target: { value: "Requested by employee" },
		});
		fireEvent.click(screen.getByRole("button", { name: "confirmDelete" }));

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

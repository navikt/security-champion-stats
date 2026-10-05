import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { AdminProgramParticipant, SlackMappingOverview } from "@/app/utils/Variables";
import { ManageSlackMappingsView } from "./ManageSlackMappingsView";

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

const overview: SlackMappingOverview = {
	mappings: [],
	unmappedAuthors: [
		{
			slackUserId: "U_SLACK",
			firstSeenAt: "2026-10-05T10:00:00Z",
			lastSeenAt: "2026-10-05T10:00:00Z",
		},
	],
};

describe("ManageSlackMappingsView", () => {
	it("should explicitly map an unmapped Slack account to a participant", async () => {
		const addMapping = vi.spyOn(Apies, "addSlackMapping").mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={overview}
				onRefresh={onRefresh}
			/>,
		);

		fireEvent.change(screen.getByLabelText("Participant for Slack account U_SLACK"), {
			target: { value: "participant-1" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Map account" }));

		await waitFor(() => expect(addMapping).toHaveBeenCalledWith("U_SLACK", "participant-1"));
		expect(await screen.findByRole("status")).toHaveTextContent("The Slack account U_SLACK was mapped.");
		expect(onRefresh).toHaveBeenCalled();

		addMapping.mockRestore();
	});

	it("should show when the Slack account or participant already has a mapping", async () => {
		const addMapping = vi.spyOn(Apies, "addSlackMapping").mockResolvedValue(409);
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(undefined)}
			/>,
		);

		fireEvent.change(screen.getByLabelText("Participant for Slack account U_SLACK"), {
			target: { value: "participant-1" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Map account" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"This Slack account or participant already has a mapping.",
		);

		addMapping.mockRestore();
	});
});

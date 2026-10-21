import {
	fireEvent,
	render,
	screen,
	waitFor,
	within,
} from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminProgramParticipant,
	SlackMappingOverview,
} from "@/app/utils/Variables";
import { ManageSlackMappingsView } from "./ManageSlackMappingsView";

const participants: AdminProgramParticipant[] = [
	{
		id: "participant-1",
		email: "person@nav.no",
		fullname: "Example Person",
		teams: ["Team A"],
		active: true,
		joinedAt: "2026-01-01T00:00:00Z",
		status: "ACTIVE",
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
	it("shows Slack names alongside stable account IDs and a fallback for unnamed accounts", () => {
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={{
					...overview,
					unmappedAuthors: [
						{
							...overview.unmappedAuthors[0],
							displayName: "Slack Display Name",
						},
						{
							...overview.unmappedAuthors[0],
							slackUserId: "U_UNNAMED",
							displayName: null,
						},
					],
				}}
				onRefresh={vi.fn()}
			/>,
		);

		fireEvent.click(
			within(
				screen.getByRole("region", { name: "Unmapped Slack accounts (2)" }),
			).getByRole("button"),
		);
		expect(
			screen.getByRole("rowheader", { name: "Slack Display Name U_SLACK" }),
		).toBeVisible();
		expect(
			screen.getByRole("rowheader", { name: "Name unavailable U_UNNAMED" }),
		).toBeVisible();
		expect(
			screen.queryByText(/Names are not used to infer identity/),
		).not.toBeInTheDocument();
	});

	it("collapses long approved mapping lists by default and preserves removal after expanding", async () => {
		const removeMapping = vi
			.spyOn(Apies, "removeSlackMapping")
			.mockResolvedValue(204);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={{
					...overview,
					mappings: Array.from({ length: 30 }, (_, index) => ({
						slackUserId: `U_MAPPED_${index}`,
						participantId: `participant-${index}`,
						participantName: `Example Person ${index}`,
						participantEmail: `example${index}@nav.no`,
						createdAt: "2026-10-05T10:00:00Z",
					})),
				}}
				onRefresh={onRefresh}
			/>,
		);

		const toggle = within(
			screen.getByRole("region", { name: "Approved mappings (30)" }),
		).getByRole("button");
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		expect(
			screen.queryByRole("button", { name: "Remove mapping" }),
		).not.toBeInTheDocument();
		fireEvent.click(toggle);
		expect(toggle).toHaveAttribute("aria-expanded", "true");
		expect(
			screen.getAllByRole("button", { name: "Remove mapping" }),
		).toHaveLength(30);
		fireEvent.click(
			screen.getAllByRole("button", { name: "Remove mapping" })[0],
		);
		await waitFor(() =>
			expect(removeMapping).toHaveBeenCalledWith("U_MAPPED_0"),
		);
		expect(await screen.findByRole("status")).toHaveTextContent(
			"U_MAPPED_0 was unmapped",
		);
		expect(onRefresh).toHaveBeenCalledOnce();
		fireEvent.click(toggle);
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		removeMapping.mockRestore();
	});

	it("collapses unmapped accounts by default and preserves mapping after expanding", async () => {
		const addMapping = vi
			.spyOn(Apies, "addSlackMapping")
			.mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={overview}
				onRefresh={onRefresh}
			/>,
		);

		const toggle = within(
			screen.getByRole("region", { name: "Unmapped Slack accounts (1)" }),
		).getByRole("button");
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		expect(
			screen.queryByRole("button", { name: "Map account" }),
		).not.toBeInTheDocument();
		fireEvent.click(toggle);
		expect(toggle).toHaveAttribute("aria-expanded", "true");
		fireEvent.change(
			screen.getByLabelText("Participant for Slack account U_SLACK"),
			{
				target: { value: "participant-1" },
			},
		);
		fireEvent.click(screen.getByRole("button", { name: "Map account" }));

		await waitFor(() =>
			expect(addMapping).toHaveBeenCalledWith("U_SLACK", "participant-1"),
		);
		expect(await screen.findByRole("status")).toHaveTextContent(
			"The Slack account U_SLACK was mapped.",
		);
		expect(onRefresh).toHaveBeenCalled();

		fireEvent.click(toggle);
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		addMapping.mockRestore();
	});

	it("should show when the Slack account or participant already has a mapping", async () => {
		const addMapping = vi
			.spyOn(Apies, "addSlackMapping")
			.mockResolvedValue(409);
		render(
			<ManageSlackMappingsView
				participants={participants}
				overview={overview}
				onRefresh={vi.fn().mockResolvedValue(undefined)}
			/>,
		);

		fireEvent.click(
			within(
				screen.getByRole("region", { name: "Unmapped Slack accounts (1)" }),
			).getByRole("button"),
		);
		fireEvent.change(
			screen.getByLabelText("Participant for Slack account U_SLACK"),
			{
				target: { value: "participant-1" },
			},
		);
		fireEvent.click(screen.getByRole("button", { name: "Map account" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"This Slack account or participant already has a mapping.",
		);

		addMapping.mockRestore();
	});
});

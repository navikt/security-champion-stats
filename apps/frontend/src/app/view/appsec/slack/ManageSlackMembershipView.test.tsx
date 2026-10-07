import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { ManageSlackMembershipView } from "./ManageSlackMembershipView";

afterEach(() => vi.restoreAllMocks());

describe("Slack membership administration", () => {
	it("loads configuration without calling Slack and previews through the frontend API on request", async () => {
		vi.spyOn(Apies, "getSlackMembershipConfiguration").mockResolvedValue({
			enabled: false,
			dryRun: true,
		});
		vi.spyOn(Apies, "getSlackMembershipAnnouncements").mockResolvedValue([]);
		const preview = vi
			.spyOn(Apies, "getSlackMembershipPreview")
			.mockResolvedValue({
				activeParticipants: 128,
				addedUserIds: ["U_NEW"],
				removedUserIds: ["U_OLD"],
				unresolvedParticipantIds: [],
			});
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		expect(
			await screen.findByText("Membership sync is disabled."),
		).toBeInTheDocument();
		expect(preview).not.toHaveBeenCalled();
		fireEvent.click(
			screen.getByRole("button", { name: "Preview membership changes" }),
		);

		expect(
			await screen.findByText("128 active participants"),
		).toBeInTheDocument();
		expect(screen.getByText("U_NEW")).toBeInTheDocument();
		expect(screen.getByText("U_OLD")).toBeInTheDocument();
	});

	it("queues dry-run sync without claiming that reconciliation finished", async () => {
		setup({ enabled: true, dryRun: true });
		const sync = vi
			.spyOn(Apies, "triggerSlackMembershipSync")
			.mockResolvedValue(202);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		fireEvent.click(
			await screen.findByRole("button", { name: "Run membership dry run" }),
		);

		await waitFor(() =>
			expect(screen.getByRole("status")).toHaveTextContent("queued"),
		);
		expect(screen.getByRole("status")).toHaveTextContent("audit trail");
		expect(sync).toHaveBeenCalledOnce();
	});

	it("requires a fresh preview and confirmation before triggering a write-enabled sync", async () => {
		setup({ enabled: true, dryRun: false });
		vi.spyOn(Apies, "getSlackMembershipPreview").mockResolvedValue({
			activeParticipants: 128,
			addedUserIds: ["U_NEW"],
			removedUserIds: ["U_OLD"],
			unresolvedParticipantIds: [],
		});
		const sync = vi
			.spyOn(Apies, "triggerSlackMembershipSync")
			.mockResolvedValue(202);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		const button = await screen.findByRole("button", {
			name: "Sync membership",
		});
		expect(button).toBeDisabled();
		fireEvent.click(
			screen.getByRole("button", { name: "Preview membership changes" }),
		);
		await screen.findByText("128 active participants");
		fireEvent.click(button);
		expect(sync).not.toHaveBeenCalled();
		fireEvent.click(
			screen.getByRole("button", { name: "Confirm membership sync" }),
		);

		await waitFor(() => expect(sync).toHaveBeenCalledOnce());
	});

	it("shows disabled or conflicting sync errors without reporting success", async () => {
		setup({ enabled: true, dryRun: true });
		vi.spyOn(Apies, "triggerSlackMembershipSync").mockResolvedValue(409);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		fireEvent.click(
			await screen.findByRole("button", { name: "Run membership dry run" }),
		);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"disabled or already running",
		);
		expect(screen.queryByRole("status")).not.toBeInTheDocument();
	});

	it("only offers recovery for uncertain announcements and requires retry confirmation", async () => {
		setup({ enabled: true, dryRun: true });
		vi.mocked(Apies.getSlackMembershipAnnouncements).mockResolvedValue([
			{
				id: "delivery-1",
				participantId: "participant-1",
				slackUserId: "U_PERSON",
				kind: "WELCOME",
				status: "UNCERTAIN",
			},
			{
				id: "delivery-2",
				participantId: "participant-2",
				slackUserId: "U_PENDING",
				kind: "REMOVAL",
				status: "PENDING",
			},
		]);
		const resolve = vi
			.spyOn(Apies, "resolveSlackMembershipDelivery")
			.mockResolvedValue(204);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		fireEvent.click(
			await screen.findByRole("button", {
				name: "Retry announcement delivery-1",
			}),
		);
		expect(resolve).not.toHaveBeenCalled();
		expect(screen.getByText(/may send a duplicate/)).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Retry announcement delivery-2" }),
		).not.toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Confirm retry" }));

		await waitFor(() =>
			expect(resolve).toHaveBeenCalledWith("delivery-1", true),
		);
	});

	it("reports preview failure rather than showing an empty successful preview", async () => {
		setup({ enabled: false, dryRun: true });
		vi.spyOn(Apies, "getSlackMembershipPreview").mockRejectedValue(
			new Error("unavailable"),
		);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);
		await screen.findByText("Membership sync is disabled.");

		fireEvent.click(
			screen.getByRole("button", { name: "Preview membership changes" }),
		);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"couldn't preview",
		);
		expect(screen.queryByText(/active participants/)).not.toBeInTheDocument();
	});

	it("maps an unresolved participant and invalidates the old preview", async () => {
		setup({ enabled: true, dryRun: false });
		vi.spyOn(Apies, "getSlackMembershipPreview").mockResolvedValue({
			activeParticipants: 128,
			addedUserIds: [],
			removedUserIds: [],
			unresolvedParticipantIds: ["participant-1"],
		});
		const mapping = vi.spyOn(Apies, "addSlackMapping").mockResolvedValue(201);
		const refresh = vi.fn().mockResolvedValue(undefined);
		render(
			<ManageSlackMembershipView
				participants={[
					{
						id: "participant-1",
						email: "person@nav.no",
						fullname: "Example Person",
						teams: [],
						active: true,
						joinedAt: "2026-10-07",
						status: "ACTIVE",
					},
				]}
				onRefresh={refresh}
			/>,
		);
		await screen.findByText("Membership sync is enabled.");
		fireEvent.click(
			screen.getByRole("button", { name: "Preview membership changes" }),
		);
		const input = await screen.findByLabelText(
			"Verified Slack account ID for Example Person (person@nav.no)",
		);
		expect(
			screen.getByRole("button", { name: "Sync membership" }),
		).toBeDisabled();
		fireEvent.change(input, { target: { value: " U_VERIFIED " } });
		fireEvent.click(
			screen.getByRole("button", {
				name: "Save mapping for Example Person (person@nav.no)",
			}),
		);

		await waitFor(() =>
			expect(mapping).toHaveBeenCalledWith("U_VERIFIED", "participant-1"),
		);
		await waitFor(() =>
			expect(screen.getByRole("status")).toHaveTextContent(
				"Slack mapping saved",
			),
		);
		expect(refresh).toHaveBeenCalledOnce();
		expect(
			screen.queryByText("128 active participants"),
		).not.toBeInTheDocument();
	});

	it("suppresses uncertain delivery without sending a message", async () => {
		setup({ enabled: false, dryRun: true });
		vi.mocked(Apies.getSlackMembershipAnnouncements)
			.mockResolvedValueOnce([
				{
					id: "delivery-1",
					participantId: "participant-1",
					slackUserId: "U_PERSON",
					kind: "WELCOME",
					status: "UNCERTAIN",
				},
			])
			.mockResolvedValue([]);
		const resolve = vi
			.spyOn(Apies, "resolveSlackMembershipDelivery")
			.mockResolvedValue(204);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		fireEvent.click(
			await screen.findByRole("button", {
				name: "Suppress announcement delivery-1",
			}),
		);
		expect(
			screen.getByRole("region", { name: "Confirm membership action" }),
		).toHaveFocus();
		expect(resolve).not.toHaveBeenCalled();
		fireEvent.click(
			screen.getByRole("button", { name: "Confirm suppression" }),
		);

		await waitFor(() =>
			expect(resolve).toHaveBeenCalledWith("delivery-1", false),
		);
		await waitFor(() =>
			expect(screen.getByRole("status")).toHaveTextContent(
				"Announcement suppressed",
			),
		);
		expect(
			screen.queryByRole("button", {
				name: "Suppress announcement delivery-1",
			}),
		).not.toBeInTheDocument();
	});

	it("failed configuration load exposes a retry and does not enable mutations", async () => {
		setup({ enabled: true, dryRun: false });
		vi.mocked(Apies.getSlackMembershipConfiguration).mockRejectedValue(
			new Error("unavailable"),
		);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		expect(await screen.findByRole("alert")).toHaveTextContent("couldn't load");
		expect(
			screen.queryByRole("button", { name: "Sync membership" }),
		).not.toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Refresh operations" }),
		).toBeEnabled();
	});

	it("a changed delivery returns a conflict without reporting recovery success", async () => {
		setup({ enabled: true, dryRun: true });
		vi.mocked(Apies.getSlackMembershipAnnouncements).mockResolvedValue([
			{
				id: "delivery-1",
				participantId: "participant-1",
				slackUserId: "U_PERSON",
				kind: "WELCOME",
				status: "UNCERTAIN",
			},
		]);
		vi.spyOn(Apies, "resolveSlackMembershipDelivery").mockResolvedValue(409);
		render(<ManageSlackMembershipView participants={[]} onRefresh={vi.fn()} />);

		fireEvent.click(
			await screen.findByRole("button", {
				name: "Retry announcement delivery-1",
			}),
		);
		fireEvent.click(screen.getByRole("button", { name: "Confirm retry" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Delivery changed or a sync is running",
		);
		expect(screen.queryByText(/Retry authorized/)).not.toBeInTheDocument();
	});
});

function setup(configuration: { enabled: boolean; dryRun: boolean }) {
	vi.spyOn(Apies, "getSlackMembershipConfiguration").mockResolvedValue(
		configuration,
	);
	vi.spyOn(Apies, "getSlackMembershipAnnouncements").mockResolvedValue([]);
}

import {
	cleanup,
	fireEvent,
	render,
	screen,
	waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMe } from "@/app/shared/hooks/UseMe";
import { Apies } from "@/app/shared/hooks/Apies";
import {
	EventClaimsApi,
	type EventClaim,
	type EventClaimOverview,
} from "./EventClaimsApi";
import { EventClaimsView } from "./EventClaimsView";
import { EventClaimForm } from "./EventClaimForm";

vi.mock("@/app/shared/hooks/UseMe", () => ({ useMe: vi.fn() }));
vi.mock("@/app/shared/hooks/Apies", () => ({
	Apies: { fetchEvents: vi.fn() },
}));
vi.mock("./EventClaimsApi", () => ({
	EventClaimsApi: { overview: vi.fn(), save: vi.fn(), review: vi.fn() },
}));

const claim: EventClaim = {
	id: "claim-1",
	submitterId: "host",
	seasonId: "season-1",
	seasonStartsOn: "2026-01-01",
	version: 1,
	eventId: null,
	published: false,
	editable: true,
	name: "Security workshop",
	description: "Threat modeling exercises",
	startDate: "2026-09-30T10:00:00Z",
	endDate: "2026-09-30T12:00:00Z",
	location: "Oslo",
	type: "workshop",
	externalEvent: true,
	links: ["https://example.org/event"],
	invitationEvidence: "Invited the network in Slack beforehand",
	contributors: [
		{
			participantId: "host",
			fullName: "Host",
			contribution: "Organized the exercises",
			status: "PENDING",
			creditId: null,
		},
		{
			participantId: "cohost",
			fullName: "Co-host",
			contribution: "Presented the threat model",
			status: "PENDING",
			creditId: null,
		},
	],
	reviews: [],
};
const overview: EventClaimOverview = {
	currentParticipantId: "host",
	seasonStartsOn: "2026-01-01",
	points: 3,
	participants: [
		{ id: "host", fullName: "Host" },
		{ id: "cohost", fullName: "Co-host" },
	],
	claims: [claim],
};

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(useMe).mockReturnValue({
		me: {
			username: "host@nav.no",
			displayName: "Host",
			isAdmin: true,
			isParticipant: true,
			isActive: true,
		},
		loading: false,
	});
	vi.mocked(EventClaimsApi.overview).mockResolvedValue(overview);
	vi.mocked(Apies.fetchEvents).mockResolvedValue([]);
});
afterEach(() => {
	cleanup();
	vi.restoreAllMocks();
});

describe("Event claims", () => {
	it("names retained reviews when the contributor is removed and no longer active", async () => {
		vi.mocked(EventClaimsApi.overview).mockResolvedValue({
			...overview,
			participants: [overview.participants[0]],
			claims: [
				{
					...claim,
					contributors: [claim.contributors[0]],
					reviews: [
						{
							participantId: "cohost",
							fullName: "Co-host",
							decision: "REJECTED",
							reason: "Describe the presentation delivered",
							createdAt: "2026-10-01T10:00:00Z",
						},
					],
				},
			],
		});
		render(<EventClaimsView />);
		expect(
			await screen.findByText(
				/Co-host: rejected - Describe the presentation delivered/,
			),
		).toBeInTheDocument();
	});

	it("shows invitation evidence and co-host contributions to participants", async () => {
		render(<EventClaimsView />);
		expect(
			await screen.findByText(claim.invitationEvidence, { exact: false }),
		).toBeInTheDocument();
		expect(screen.getByText("Presented the threat model")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Edit and resubmit" }),
		).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Approve Host" }),
		).not.toBeInTheDocument();
	});

	it("prevents self-approval and requires a review reason", async () => {
		render(<EventClaimsView admin />);
		const approve = await screen.findByRole("button", {
			name: "Approve Co-host",
		});
		expect(
			screen.queryByRole("button", { name: "Approve Host" }),
		).not.toBeInTheDocument();
		fireEvent.click(approve);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Provide a review reason",
		);
		expect(EventClaimsApi.review).not.toHaveBeenCalled();
	});

	it("approves individual contributors with the version and displays publication", async () => {
		vi.mocked(EventClaimsApi.review).mockResolvedValue({
			...claim,
			version: 2,
			editable: false,
			published: true,
			contributors: claim.contributors.map((contributor) =>
				contributor.participantId === "cohost"
					? { ...contributor, status: "APPROVED", creditId: "credit-1" }
					: contributor,
			),
		});
		render(<EventClaimsView admin />);
		await screen.findByRole("button", { name: "Approve Co-host" });
		fireEvent.change(
			screen.getByLabelText("Review reason for Security workshop"),
			{ target: { value: "Verified delivery and invitation" } },
		);
		fireEvent.click(screen.getByRole("button", { name: "Approve Co-host" }));
		await waitFor(() =>
			expect(EventClaimsApi.review).toHaveBeenCalledWith(
				claim,
				"cohost",
				"APPROVED",
				"Verified delivery and invitation",
			),
		);
		expect(
			await screen.findByText("Published in", { exact: false }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Revoke credit for Co-host" }),
		).toBeInTheDocument();
	});

	it("shows stale-review errors instead of claiming success", async () => {
		vi.mocked(EventClaimsApi.review).mockRejectedValue(
			new Error("The claim changed. Reload it before reviewing"),
		);
		render(<EventClaimsView admin />);
		await screen.findByRole("button", { name: "Approve Co-host" });
		fireEvent.change(
			screen.getByLabelText("Review reason for Security workshop"),
			{ target: { value: "Checked" } },
		);
		fireEvent.click(screen.getByRole("button", { name: "Approve Co-host" }));
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"The claim changed",
		);
		expect(
			screen.queryByText("Contribution approved."),
		).not.toBeInTheDocument();
	});

	it("requires confirmation before revoking credit", async () => {
		vi.mocked(EventClaimsApi.overview).mockResolvedValue({
			...overview,
			claims: [
				{
					...claim,
					contributors: [
						{
							...claim.contributors[1],
							status: "APPROVED",
							creditId: "credit",
						},
					],
				},
			],
		});
		const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
		render(<EventClaimsView admin />);
		await screen.findByRole("button", { name: "Revoke credit for Co-host" });
		fireEvent.change(
			screen.getByLabelText("Review reason for Security workshop"),
			{ target: { value: "Incorrect claim" } },
		);
		fireEvent.click(
			screen.getByRole("button", { name: "Revoke credit for Co-host" }),
		);
		expect(confirm).toHaveBeenCalled();
		expect(EventClaimsApi.review).not.toHaveBeenCalled();
	});

	it("resubmits edited evidence with co-hosts and expected version", async () => {
		vi.mocked(EventClaimsApi.save).mockResolvedValue(claim);
		const saved = vi.fn();
		render(
			<EventClaimForm
				overview={overview}
				events={[]}
				claim={claim}
				onSaved={saved}
				onCancel={vi.fn()}
			/>,
		);
		fireEvent.change(screen.getByLabelText("Advance invitation evidence"), {
			target: { value: "Added invitation link" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Submit for review" }));
		await waitFor(() =>
			expect(EventClaimsApi.save).toHaveBeenCalledWith(
				expect.objectContaining({
					expectedVersion: 1,
					invitationEvidence: "Added invitation link",
					startDate: claim.startDate,
					endDate: claim.endDate,
					contributors: claim.contributors.map(
						({ participantId, contribution }) => ({
							participantId,
							contribution,
						}),
					),
				}),
				claim.id,
			),
		);
		expect(saved).toHaveBeenCalledWith(claim);
	});

	it("submits a new external event with a co-host", async () => {
		vi.mocked(EventClaimsApi.save).mockResolvedValue(claim);
		render(
			<EventClaimForm
				overview={overview}
				events={[]}
				onSaved={vi.fn()}
				onCancel={vi.fn()}
			/>,
		);
		fireEvent.change(screen.getByLabelText("Event name"), {
			target: { value: claim.name },
		});
		fireEvent.change(screen.getByLabelText("Security content delivered"), {
			target: { value: claim.description },
		});
		fireEvent.change(
			screen.getByLabelText("Start date and time (your local time)"),
			{ target: { value: "2026-09-30T10:00" } },
		);
		fireEvent.change(
			screen.getByLabelText("End date and time (your local time)"),
			{ target: { value: "2026-09-30T12:00" } },
		);
		fireEvent.change(screen.getByLabelText("Internal or external event"), {
			target: { value: "external" },
		});
		fireEvent.change(screen.getByLabelText("Event and resource links"), {
			target: { value: claim.links[0] },
		});
		fireEvent.change(screen.getByLabelText("Advance invitation evidence"), {
			target: { value: claim.invitationEvidence },
		});
		fireEvent.change(
			screen.getByLabelText(
				"Substantive organizing or presenting contribution 1",
			),
			{ target: { value: "Organized" } },
		);
		fireEvent.click(
			screen.getByRole("button", { name: "Add co-host or presenter" }),
		);
		fireEvent.change(screen.getByLabelText("Participant 2"), {
			target: { value: "cohost" },
		});
		fireEvent.change(
			screen.getByLabelText(
				"Substantive organizing or presenting contribution 2",
			),
			{ target: { value: "Presented" } },
		);
		fireEvent.click(screen.getByRole("button", { name: "Submit for review" }));
		await waitFor(() =>
			expect(EventClaimsApi.save).toHaveBeenCalledWith(
				expect.objectContaining({
					externalEvent: true,
					links: claim.links,
					contributors: [
						{ participantId: "host", contribution: "Organized" },
						{ participantId: "cohost", contribution: "Presented" },
					],
				}),
				undefined,
			),
		);
	});

	it("does not fetch claim data for an inactive participant", () => {
		vi.mocked(useMe).mockReturnValue({
			me: {
				username: "host@nav.no",
				displayName: "Host",
				isAdmin: false,
				isParticipant: true,
				isActive: false,
			},
			loading: false,
		});
		render(<EventClaimsView />);
		expect(
			screen.getByText(
				"Active program membership is required to claim event credit.",
			),
		).toBeInTheDocument();
		expect(EventClaimsApi.overview).not.toHaveBeenCalled();
	});
});

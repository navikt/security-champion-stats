import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import type { SlackChannelParticipationOverview } from "@/app/utils/Variables";
import { SlackChannelParticipationView } from "./SlackChannelParticipationView";

afterEach(() => vi.restoreAllMocks());

const overview = (
	overrides: Partial<SlackChannelParticipationOverview> = {},
): SlackChannelParticipationOverview => ({
	enabled: true,
	channelConfigured: true,
	lastAttemptAt: "2026-10-09T07:00:00Z",
	lastSuccessAt: "2026-10-09T07:00:00Z",
	outcome: "SUCCEEDED",
	failureSummary: null,
	participants: [],
	...overrides,
});

describe("Slack channel participation", () => {
	it("lists participants who need attention with their notification state", async () => {
		vi.spyOn(Apies, "getSlackChannelParticipation").mockResolvedValue(
			overview({
				participants: [
					{
						participantId: "1",
						name: "Leaver",
						email: "leaver@nav.no",
						category: "DEACTIVATED_AFTER_LEAVING",
						absentSince: "2026-10-08T07:00:00Z",
						notificationStatus: "UNCERTAIN",
					},
					{
						participantId: "2",
						name: "Unknown",
						email: "unknown@nav.no",
						category: "IDENTITY_UNRESOLVED",
						absentSince: null,
						notificationStatus: null,
					},
				],
			}),
		);

		render(<SlackChannelParticipationView />);

		const table = await screen.findByRole("region", { name: "Participants needing attention" });
		const leaver = within(table).getByRole("row", { name: /Leaver/ });
		expect(within(leaver).getByText("Deactivated after leaving")).toBeInTheDocument();
		expect(within(leaver).getByText("Unconfirmed – check Slack")).toBeInTheDocument();
		expect(within(table).getByText("Slack account not found")).toBeInTheDocument();
	});

	it("shows a failed check prominently", async () => {
		vi.spyOn(Apies, "getSlackChannelParticipation").mockResolvedValue(
			overview({ outcome: "FAILED", failureSummary: "Slack channel members could not be read" }),
		);

		render(<SlackChannelParticipationView />);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Failed – Slack channel members could not be read",
		);
	});

	it("queues a manual check when monitoring is enabled", async () => {
		vi.spyOn(Apies, "getSlackChannelParticipation").mockResolvedValue(overview());
		const trigger = vi.spyOn(Apies, "triggerSlackChannelParticipationCheck").mockResolvedValue(202);

		render(<SlackChannelParticipationView />);
		await screen.findByText("All checked participants are in the channel.");
		await userEvent.click(screen.getByRole("button", { name: "Check channel now" }));

		expect(trigger).toHaveBeenCalledOnce();
		expect(await screen.findByRole("status")).toHaveTextContent("The channel check was queued");
	});

	it("does not allow manual checks while monitoring is disabled", async () => {
		vi.spyOn(Apies, "getSlackChannelParticipation").mockResolvedValue(overview({ enabled: false }));

		render(<SlackChannelParticipationView />);

		expect(await screen.findByText("Channel monitoring is disabled.")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Check channel now" })).toBeDisabled();
	});
});

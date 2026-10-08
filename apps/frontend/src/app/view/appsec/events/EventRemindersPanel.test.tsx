import {
	act,
	cleanup,
	fireEvent,
	render,
	screen,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SecurityEvent } from "@/app/utils/Variables";
import { EventRemindersPanel } from "./EventRemindersPanel";
import { ManageEventsView } from "./ManageEventsView";
import {
	type EventReminderPreview,
	previewEventReminders,
	sendEventReminders,
} from "./EventRemindersApi";

vi.mock("./EventRemindersApi", () => ({
	previewEventReminders: vi.fn(),
	sendEventReminders: vi.fn(),
}));

const event: SecurityEvent = {
	id: "synthetic-delta",
	name: "Synthetic meetup",
	description: "",
	startDate: "2099-10-20T08:00:00Z",
	endDate: "2099-10-20T09:00:00Z",
	location: "",
	type: "meetup",
	externalEvent: false,
	deltaEvent: true,
	signupSupported: true,
};

const preview: EventReminderPreview = {
	version: "reviewed-version",
	checkedAt: "2026-10-08T08:00:00Z",
	message: "Synthetic reminder message",
	signedUpParticipants: 3,
	recipients: [
		{
			participantId: "1",
			name: "Ready person",
			slackUserId: "U_READY",
			status: "READY",
		},
		{
			participantId: "2",
			name: "Unresolved person",
			slackUserId: null,
			status: "UNRESOLVED",
		},
		{
			participantId: "3",
			name: "Already reminded",
			slackUserId: "U_SENT",
			status: "ALREADY_SENT",
		},
		{
			participantId: "4",
			name: "Unknown delivery",
			slackUserId: "U_UNKNOWN",
			status: "DELIVERY_UNCERTAIN",
		},
	],
};

beforeEach(() => {
	vi.mocked(previewEventReminders).mockResolvedValue(preview);
	vi.mocked(sendEventReminders).mockResolvedValue(undefined);
	vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
	cleanup();
	vi.resetAllMocks();
	vi.restoreAllMocks();
});

async function loadPreview() {
	fireEvent.click(
		screen.getByRole("button", { name: "Preview Slack reminders" }),
	);
	await screen.findByDisplayValue("Synthetic reminder message");
}

describe("Event reminders", () => {
	it("previews recipients and exclusions without sending and requires explicit confirmation", async () => {
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		expect(
			screen.getByText(/3 active participants signed up or hosting/),
		).toBeInTheDocument();
		expect(
			screen.getByText(/Ready person: Will receive a Slack DM/),
		).toBeInTheDocument();
		expect(screen.getByText(/Unresolved person: Skipped/)).toBeInTheDocument();
		expect(screen.getByText(/Already reminded: Skipped/)).toBeInTheDocument();
		expect(screen.getByText(/Unknown delivery: Skipped/)).toBeInTheDocument();
		const send = screen.getByRole("button", { name: "Send Slack reminders" });
		expect(send).toBeDisabled();
		expect(sendEventReminders).not.toHaveBeenCalled();
		fireEvent.click(screen.getByRole("checkbox"));
		expect(send).toBeEnabled();
		fireEvent.click(send);
		await screen.findByRole("status");
		expect(sendEventReminders).toHaveBeenCalledExactlyOnceWith(
			event.id,
			"reviewed-version",
			preview.message,
		);
		expect(screen.getByRole("status")).toHaveTextContent(
			"queued, not yet delivered",
		);
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
	});

	it("stale previews and unavailable signup data show an error and discard confirmation", async () => {
		vi.mocked(sendEventReminders).mockRejectedValue(new Error("Preview again"));
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.click(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		);
		expect(await screen.findByRole("alert")).toHaveTextContent("Preview again");
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
		vi.mocked(previewEventReminders).mockRejectedValue(
			new Error("Signup information unavailable"),
		);
		fireEvent.click(
			screen.getByRole("button", { name: "Preview Slack reminders" }),
		);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Signup information unavailable",
		);
		expect(
			screen.queryByRole("button", { name: "Send Slack reminders" }),
		).not.toBeInTheDocument();
	});

	it("reloading the preview always resets confirmation", async () => {
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		fireEvent.click(screen.getByRole("checkbox"));
		await loadPreview();
		expect(screen.getByRole("checkbox")).not.toBeChecked();
		expect(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		).toBeDisabled();
	});

	it("cannot send when every recipient is excluded", async () => {
		vi.mocked(previewEventReminders).mockResolvedValue({
			...preview,
			recipients: preview.recipients.slice(1),
		});
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Send Slack reminders" }),
		).not.toBeInTheDocument();
	});

	it("rapid repeated clicks do not send duplicate requests", async () => {
		let resolve!: () => void;
		vi.mocked(sendEventReminders).mockReturnValue(
			new Promise<void>((done) => {
				resolve = done;
			}),
		);
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		fireEvent.click(screen.getByRole("checkbox"));
		const send = screen.getByRole("button", { name: "Send Slack reminders" });
		fireEvent.click(send);
		fireEvent.click(send);
		expect(sendEventReminders).toHaveBeenCalledTimes(1);
		await act(async () => resolve());
	});

	it("editing the pre-filled message requires confirmation again and sends the edited text", async () => {
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.change(screen.getByRole("textbox", { name: "Message" }), {
			target: { value: "Edited reminder\nSign up!" },
		});
		expect(screen.getByRole("checkbox")).not.toBeChecked();
		expect(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		).toBeDisabled();
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.click(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		);
		await screen.findByRole("status");
		expect(sendEventReminders).toHaveBeenCalledExactlyOnceWith(
			event.id,
			preview.version,
			"Edited reminder\nSign up!",
		);
	});

	it.each(["", " \n\t", "x".repeat(4001)])(
		"does not send an invalid message (%#)",
		async (message) => {
			render(<EventRemindersPanel event={event} />);
			await loadPreview();
			fireEvent.change(screen.getByRole("textbox", { name: "Message" }), {
				target: { value: message },
			});
			fireEvent.click(screen.getByRole("checkbox"));
			expect(
				screen.getByRole("button", { name: "Send Slack reminders" }),
			).toBeDisabled();
			expect(screen.getByRole("textbox", { name: "Message" })).toHaveAttribute(
				"aria-invalid",
				"true",
			);
			expect(sendEventReminders).not.toHaveBeenCalled();
		},
	);

	it("accepts a message at the length limit", async () => {
		render(<EventRemindersPanel event={event} />);
		await loadPreview();
		fireEvent.change(screen.getByRole("textbox", { name: "Message" }), {
			target: { value: "x".repeat(4000) },
		});
		fireEvent.click(screen.getByRole("checkbox"));
		expect(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		).toBeEnabled();
	});

	it("Manage events offers a selection only for supported upcoming events", () => {
		render(
			<ManageEventsView
				events={[
					event,
					{
						...event,
						id: "manual",
						name: "Manual event",
						deltaEvent: false,
						signupSupported: false,
					},
					{
						...event,
						id: "past",
						name: "Past Delta",
						startDate: "2020-01-01T08:00:00Z",
						endDate: "2020-01-01T09:00:00Z",
					},
				]}
			/>,
		);
		const select = screen.getByRole("combobox", {
			name: "Event to remind participants about",
		});
		expect(screen.getAllByRole("option")).toHaveLength(2);
		expect(
			screen.queryByRole("button", { name: "Preview Slack reminders" }),
		).not.toBeInTheDocument();
		fireEvent.change(select, { target: { value: event.id } });
		expect(
			screen.getAllByRole("button", { name: "Preview Slack reminders" }),
		).toHaveLength(1);
	});

	it("switching events clears the preview, edited message and confirmation and sends only for the selection", async () => {
		const other = { ...event, id: "other-delta", name: "Other meetup" };
		render(<ManageEventsView events={[event, other]} />);
		const select = screen.getByRole("combobox", {
			name: "Event to remind participants about",
		});
		fireEvent.change(select, { target: { value: event.id } });
		await loadPreview();
		fireEvent.change(screen.getByRole("textbox", { name: "Message" }), {
			target: { value: "First event edited text" },
		});
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.change(select, { target: { value: other.id } });
		expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
		await loadPreview();
		expect(previewEventReminders).toHaveBeenLastCalledWith(other.id);
		expect(screen.getByRole("checkbox")).not.toBeChecked();
		expect(screen.getByRole("textbox", { name: "Message" })).toHaveValue(
			preview.message,
		);
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.click(
			screen.getByRole("button", { name: "Send Slack reminders" }),
		);
		await screen.findByRole("status");
		expect(sendEventReminders).toHaveBeenCalledExactlyOnceWith(
			other.id,
			preview.version,
			preview.message,
		);
		fireEvent.change(select, { target: { value: event.id } });
		expect(screen.queryByRole("status")).not.toBeInTheDocument();
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
	});

	it("a late preview response for a previously selected event does not populate the new selection", async () => {
		let resolve!: (value: EventReminderPreview) => void;
		vi.mocked(previewEventReminders).mockReturnValueOnce(
			new Promise<EventReminderPreview>((done) => {
				resolve = done;
			}),
		);
		const other = { ...event, id: "other-delta", name: "Other meetup" };
		render(<ManageEventsView events={[event, other]} />);
		const select = screen.getByRole("combobox", {
			name: "Event to remind participants about",
		});
		fireEvent.change(select, { target: { value: event.id } });
		fireEvent.click(
			screen.getByRole("button", { name: "Preview Slack reminders" }),
		);
		fireEvent.change(select, { target: { value: other.id } });
		await act(async () => resolve(preview));
		expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
		expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
		await loadPreview();
		expect(previewEventReminders).toHaveBeenLastCalledWith(other.id);
	});

	it("shows an empty state when no upcoming event supports reminders", () => {
		render(<ManageEventsView events={[]} />);
		expect(
			screen.getByText("No upcoming Delta events available for reminders."),
		).toBeInTheDocument();
		expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
	});
});

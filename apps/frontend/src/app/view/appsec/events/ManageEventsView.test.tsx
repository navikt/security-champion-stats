import {
	act,
	cleanup,
	fireEvent,
	render,
	screen,
	waitFor,
} from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { ManageEventsView } from "./ManageEventsView";

beforeAll(() => {
	Object.defineProperties(HTMLDialogElement.prototype, {
		showModal: {
			configurable: true,
			value() {
				this.setAttribute("open", "");
			},
		},
		close: {
			configurable: true,
			value() {
				this.removeAttribute("open");
			},
		},
	});
});

afterEach(() => {
	cleanup();
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

async function openForm() {
	await act(async () => {
		fireEvent.click(screen.getByRole("button", { name: "Add event" }));
	});
}

async function fillEvent() {
	await openForm();
	fireEvent.change(screen.getByLabelText("Name"), {
		target: { value: "Security meetup" },
	});
	for (const label of ["Start date", "End date"]) {
		const input = screen.getByLabelText(label);
		fireEvent.change(input, { target: { value: "01.11.2099" } });
		fireEvent.blur(input);
	}
}

describe("ManageEventsView", () => {
	it("should show an actionable error when the server returns a non-JSON failure", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response("Synthetic gateway error", {
					status: 502,
					headers: { "Content-Type": "text/plain" },
				}),
			),
		);
		vi.spyOn(console, "error").mockImplementation(() => {});
		render(<ManageEventsView events={[]} />);
		await fillEvent();
		fireEvent.click(screen.getByRole("button", { name: "Create event" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't create the event. Try again.",
		);
	});

	it("should offer only supported event types and no inactive Delta switch", async () => {
		render(<ManageEventsView events={[]} />);
		await openForm();

		expect(screen.getByRole("option", { name: "Meeting" })).toBeInTheDocument();
		expect(
			screen.getByRole("option", { name: "Workshop" }),
		).toBeInTheDocument();
		expect(
			screen.queryByRole("option", { name: "Course" }),
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole("checkbox", { name: /delta event/i }),
		).not.toBeInTheDocument();
	});

	it("should prevent submission with missing times or a blank name", async () => {
		render(<ManageEventsView events={[]} />);
		await fillEvent();
		const submit = screen.getByRole("button", { name: "Create event" });

		fireEvent.change(screen.getByLabelText("Start time"), {
			target: { value: "" },
		});
		expect(submit).toBeDisabled();
		fireEvent.change(screen.getByLabelText("Start time"), {
			target: { value: "09:00" },
		});
		fireEvent.change(screen.getByLabelText("Name"), {
			target: { value: "   " },
		});
		expect(submit).toBeDisabled();
	});

	it("should display the saved event and reset the form after successful creation", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify({
						id: "saved-event",
						name: "Saved security meetup",
						description: "",
						location: "",
						type: "meetup",
						startDate: "2099-11-01T09:00:00Z",
						endDate: "2099-11-01T10:00:00Z",
						externalEvent: false,
						deltaEvent: false,
						amountOfPeopleJoined: 0,
					}),
					{ status: 201, headers: { "Content-Type": "application/json" } },
				),
			),
		);
		render(<ManageEventsView events={[]} />);
		await fillEvent();
		fireEvent.click(screen.getByRole("button", { name: "Create event" }));

		expect(
			await screen.findByText("Saved security meetup"),
		).toBeInTheDocument();
		await waitFor(() =>
			expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
		);
		await openForm();
		expect(screen.getByLabelText("Name")).toHaveValue("");
		expect(screen.getByLabelText("Start date")).toHaveValue("");
	});

	it("should show a duplicate error without discarding the event draft", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify({
						detail:
							"An event with this name, start time and location already exists",
					}),
					{
						status: 409,
						headers: { "Content-Type": "application/problem+json" },
					},
				),
			),
		);
		vi.spyOn(console, "error").mockImplementation(() => {});
		render(<ManageEventsView events={[]} />);
		await fillEvent();
		fireEvent.click(screen.getByRole("button", { name: "Create event" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"An event with this name, start time and location already exists",
		);
		expect(screen.getByLabelText("Name")).toHaveValue("Security meetup");
	});
});

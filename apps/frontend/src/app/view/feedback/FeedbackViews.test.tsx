import {
	cleanup,
	fireEvent,
	render,
	screen,
	waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMe } from "@/app/shared/hooks/UseMe";
import { type Feedback, FeedbackApi } from "./FeedbackApi";
import { FeedbackForm } from "./FeedbackForm";
import { FeedbackInbox } from "./FeedbackInbox";

vi.mock("@/app/shared/hooks/UseMe", () => ({ useMe: vi.fn() }));
vi.mock("./FeedbackApi", () => ({
	FeedbackApi: {
		submit: vi.fn(),
		search: vi.fn(),
		detail: vi.fn(),
		act: vi.fn(),
	},
}));

const feedback: Feedback = {
	id: "feedback-1",
	submitterId: "participant",
	submitterName: "Participant",
	submitterEmail: "participant@nav.no",
	submitterActive: true,
	seasonId: "season-1",
	title: "Improve navigation",
	description: "Make the program easier to find",
	status: "OPEN",
	ownerEmail: null,
	scoreDecision: "PENDING",
	creditId: null,
	version: 1,
	createdAt: "2026-10-01T10:00:00Z",
	history: [],
};

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(useMe).mockReturnValue({
		me: {
			username: "admin@nav.no",
			displayName: "Admin",
			isAdmin: true,
			isParticipant: true,
			isActive: true,
		},
		loading: false,
	});
	vi.mocked(FeedbackApi.search).mockResolvedValue({
		items: [feedback],
		nextOffset: null,
		actorEmail: "admin@nav.no",
	});
	vi.mocked(FeedbackApi.detail).mockResolvedValue(feedback);
});
afterEach(cleanup);

describe("Feedback submission", () => {
	it("confirms a saved submission without exposing internal handling", async () => {
		vi.mocked(FeedbackApi.submit).mockResolvedValue({ id: feedback.id });
		render(<FeedbackForm />);
		fireEvent.change(screen.getByLabelText("Title"), {
			target: { value: feedback.title },
		});
		fireEvent.change(screen.getByLabelText("Feedback"), {
			target: { value: feedback.description },
		});
		fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
		expect(
			await screen.findByText(
				"Your feedback has been saved for the program administrators.",
			),
		).toBeTruthy();
		expect(FeedbackApi.submit).toHaveBeenCalledWith({
			id: expect.any(String),
			title: feedback.title,
			description: feedback.description,
		});
		expect(screen.queryByText("Handling history")).toBeNull();
		expect(FeedbackApi.search).not.toHaveBeenCalled();
	});

	it("preserves input and the submission ID when a failed response is retried", async () => {
		vi.mocked(FeedbackApi.submit)
			.mockRejectedValueOnce(new Error("Try again"))
			.mockResolvedValueOnce({ id: feedback.id });
		render(<FeedbackForm />);
		fireEvent.change(screen.getByLabelText("Title"), {
			target: { value: feedback.title },
		});
		fireEvent.change(screen.getByLabelText("Feedback"), {
			target: { value: feedback.description },
		});
		fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
		expect(await screen.findByRole("alert")).toHaveTextContent("Try again");
		expect(screen.getByLabelText("Feedback")).toHaveValue(feedback.description);
		fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));
		await screen.findByText(
			"Your feedback has been saved for the program administrators.",
		);
		expect(vi.mocked(FeedbackApi.submit).mock.calls[0][0].id).toBe(
			vi.mocked(FeedbackApi.submit).mock.calls[1][0].id,
		);
	});

	it("does not show the form to inactive participants", () => {
		vi.mocked(useMe).mockReturnValue({
			me: {
				username: "participant@nav.no",
				displayName: null,
				isAdmin: false,
				isParticipant: true,
				isActive: false,
			},
			loading: false,
		});
		render(<FeedbackForm />);
		expect(
			screen.queryByRole("button", { name: "Submit feedback" }),
		).toBeNull();
		expect(FeedbackApi.submit).not.toHaveBeenCalled();
	});
});

describe("Admin feedback inbox", () => {
	it("reads without claiming and sends search filters to the backend", async () => {
		render(<FeedbackInbox />);
		fireEvent.click(
			await screen.findByRole("button", { name: feedback.title }),
		);
		await screen.findByRole("heading", { name: feedback.title });
		expect(FeedbackApi.act).not.toHaveBeenCalled();
		fireEvent.change(screen.getByLabelText("Search feedback"), {
			target: { value: "navigation" },
		});
		fireEvent.change(screen.getByLabelText("Owner"), {
			target: { value: "unassigned" },
		});
		fireEvent.change(screen.getByLabelText("Status"), {
			target: { value: "OPEN" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Search" }));
		await waitFor(() => {
			const params = vi.mocked(FeedbackApi.search).mock.lastCall?.[0];
			expect(params?.get("q")).toBe("navigation");
			expect(params?.get("owner")).toBe("unassigned");
			expect(params?.get("status")).toBe("OPEN");
		});
	});

	it("claims explicitly and saves a scoring decision with the current version", async () => {
		const owned: Feedback = {
			...feedback,
			status: "IN_PROGRESS",
			ownerEmail: "admin@nav.no",
			version: 2,
		};
		vi.mocked(FeedbackApi.act)
			.mockResolvedValueOnce(owned)
			.mockResolvedValueOnce({
				...owned,
				version: 3,
				scoreDecision: "QUALIFIED",
				creditId: "credit-1",
			});
		render(<FeedbackInbox />);
		fireEvent.click(
			await screen.findByRole("button", { name: feedback.title }),
		);
		fireEvent.click(
			await screen.findByRole("button", { name: "Take ownership" }),
		);
		const select = await screen.findByLabelText("Follow-up action");
		fireEvent.change(select, { target: { value: "QUALIFY" } });
		fireEvent.change(screen.getByLabelText("Internal note"), {
			target: { value: "Useful suggestion" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save action" }));
		await waitFor(() =>
			expect(FeedbackApi.act).toHaveBeenLastCalledWith(
				owned,
				"QUALIFY",
				"Useful suggestion",
				undefined,
			),
		);
		expect(await screen.findByText(/Scoring: Qualified/)).toBeTruthy();
	});

	it("retains a note after a conflict and supports reloading the feedback", async () => {
		const owned: Feedback = {
			...feedback,
			status: "IN_PROGRESS",
			ownerEmail: "admin@nav.no",
			version: 2,
		};
		vi.mocked(FeedbackApi.detail).mockResolvedValue(owned);
		vi.mocked(FeedbackApi.act).mockRejectedValue(
			new Error("The feedback changed. Reload it before continuing"),
		);
		render(<FeedbackInbox />);
		fireEvent.click(
			await screen.findByRole("button", { name: feedback.title }),
		);
		fireEvent.change(await screen.findByLabelText("Internal note"), {
			target: { value: "Keep this note" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save action" }));
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"The feedback changed.",
		);
		expect(screen.getByLabelText("Internal note")).toHaveValue(
			"Keep this note",
		);
		fireEvent.click(screen.getByRole("button", { name: "Reload feedback" }));
		await waitFor(() => expect(FeedbackApi.detail).toHaveBeenCalledTimes(2));
		expect(await screen.findByLabelText("Internal note")).toHaveValue(
			"Keep this note",
		);
	});

	it("does not expose the self-award action on an administrator's own feedback", async () => {
		vi.mocked(FeedbackApi.detail).mockResolvedValue({
			...feedback,
			submitterEmail: "admin@nav.no",
			status: "IN_PROGRESS",
			ownerEmail: "admin@nav.no",
		});
		render(<FeedbackInbox />);
		fireEvent.click(
			await screen.findByRole("button", { name: feedback.title }),
		);
		await screen.findByLabelText("Follow-up action");
		expect(
			screen.queryByRole("option", {
				name: "Qualify and award feedback credit",
			}),
		).toBeNull();
	});
});

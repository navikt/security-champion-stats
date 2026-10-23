import {
	act,
	cleanup,
	fireEvent,
	render,
	screen,
	waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMe } from "@/app/shared/hooks/UseMe";
import { type Feedback, FeedbackApi, type FeedbackCounts } from "./FeedbackApi";
import { FeedbackForm } from "./FeedbackForm";
import { FeedbackInbox } from "./FeedbackInbox";

vi.mock("@/app/shared/hooks/UseMe", () => ({ useMe: vi.fn() }));
vi.mock("next/navigation", async () => {
	const { useMemo, useSyncExternalStore } = await import("react");
	const subscribe = (callback: () => void) => {
		window.addEventListener("popstate", callback);
		return () => window.removeEventListener("popstate", callback);
	};
	return {
		usePathname: () =>
			useSyncExternalStore(subscribe, () => window.location.pathname),
		useSearchParams: () => {
			const query = useSyncExternalStore(
				subscribe,
				() => window.location.search,
			);
			return useMemo(() => new URLSearchParams(query), [query]);
		},
	};
});
vi.mock("./FeedbackApi", () => ({
	FeedbackApi: {
		submit: vi.fn(),
		search: vi.fn(),
		detail: vi.fn(),
		act: vi.fn(),
		markRead: vi.fn(),
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
	ownerName: null,
	readByMe: false,
	awardedPoints: null,
	scoreDecision: "PENDING",
	creditId: null,
	version: 1,
	createdAt: "2026-10-01T10:00:00Z",
	history: [],
};
const counts: FeedbackCounts = {
	active: 1,
	open: 1,
	inProgress: 0,
	resolved: 0,
	all: 1,
	unread: 1,
	unassigned: 1,
	mine: 0,
};
let serverItems: Feedback[];
const originalReplaceState = window.history.replaceState.bind(window.history);
const originalPushState = window.history.pushState.bind(window.history);

function save(result: Feedback) {
	vi.mocked(FeedbackApi.act).mockImplementationOnce(async () => {
		serverItems = serverItems.map((item) =>
			item.id === result.id ? result : item,
		);
		return result;
	});
}

beforeEach(() => {
	vi.resetAllMocks();
	originalReplaceState(null, "", "/appsec/feedback");
	vi.spyOn(window.history, "replaceState").mockImplementation((...args) => {
		originalReplaceState(...args);
		window.dispatchEvent(new PopStateEvent("popstate"));
	});
	vi.spyOn(window.history, "pushState").mockImplementation((...args) => {
		originalPushState(...args);
		window.dispatchEvent(new PopStateEvent("popstate"));
	});
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
	serverItems = [{ ...feedback }];
	vi.mocked(FeedbackApi.search).mockImplementation(async () => ({
		items: serverItems,
		nextCursor: null,
		actorEmail: "admin@nav.no",
		counts,
	}));
	vi.mocked(FeedbackApi.detail).mockImplementation(async (id) => {
		const item = serverItems.find((item) => item.id === id);
		if (!item) throw new Error("Not found");
		return item;
	});
	vi.mocked(FeedbackApi.markRead).mockImplementation(async (id) => {
		serverItems = serverItems.map((item) =>
			item.id === id ? { ...item, readByMe: true } : item,
		);
	});
});
afterEach(() => {
	cleanup();
	vi.useRealTimers();
	vi.restoreAllMocks();
});

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
	it("marks the selection read without claiming and applies debounced URL-backed filters", async () => {
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: feedback.title });
		await waitFor(() =>
			expect(FeedbackApi.markRead).toHaveBeenCalledWith(
				feedback.id,
				expect.any(AbortSignal),
			),
		);
		expect(FeedbackApi.act).not.toHaveBeenCalled();
		expect(
			screen.queryByRole("button", { name: "Search" }),
		).not.toBeInTheDocument();
		expect(window.location.pathname).toBe(`/appsec/feedback/${feedback.id}`);
		fireEvent.change(screen.getByLabelText("Search feedback"), {
			target: { value: "navigation" },
		});
		await waitFor(() =>
			expect(window.location.search).toContain("q=navigation"),
		);
		fireEvent.change(screen.getByLabelText("Owner filter"), {
			target: { value: "unassigned" },
		});
		fireEvent.change(screen.getByLabelText("Status filter"), {
			target: { value: "OPEN" },
		});
		await waitFor(() => {
			const params = vi.mocked(FeedbackApi.search).mock.lastCall?.[0];
			expect(params?.get("q")).toBe("navigation");
			expect(params?.get("owner")).toBe("unassigned");
			expect(params?.get("status")).toBe("OPEN");
		});
	});

	it("restores filters and selection from a deep link", async () => {
		const other = { ...feedback, id: "feedback-2", title: "Other feedback" };
		serverItems.push(other);
		originalReplaceState(
			null,
			"",
			`/appsec/feedback/${other.id}?q=other&status=all&owner=mine&scoreDecision=PENDING`,
		);
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: other.title });
		expect(screen.getByLabelText("Search feedback")).toHaveValue("other");
		const params = vi.mocked(FeedbackApi.search).mock.calls[0][0];
		expect(params.get("status")).toBe("all");
		expect(params.get("owner")).toBe("mine");
		expect(params.get("scoreDecision")).toBe("PENDING");
		expect(FeedbackApi.markRead).toHaveBeenCalledWith(
			other.id,
			expect.any(AbortSignal),
		);
	});

	it("assesses scoring without ownership and retains the final decision", async () => {
		save({
			...feedback,
			readByMe: true,
			version: 2,
			scoreDecision: "QUALIFIED",
			creditId: "credit-1",
			awardedPoints: 4,
		});
		render(<FeedbackInbox />);
		const scoring = await screen.findByLabelText("Scoring");
		expect(scoring).not.toBeDisabled();
		expect(screen.getByRole("combobox", { name: "Status" })).toBeDisabled();
		fireEvent.change(scoring, { target: { value: "QUALIFIED" } });
		await waitFor(() =>
			expect(FeedbackApi.act).toHaveBeenCalledWith(
				expect.objectContaining({ version: 1, ownerEmail: null }),
				"QUALIFY",
				"",
				undefined,
				undefined,
			),
		);
		await waitFor(() =>
			expect(screen.getByLabelText("Scoring")).toBeDisabled(),
		);
		expect(
			await screen.findByRole("option", { name: "Points awarded · +4" }),
		).toBeInTheDocument();
	});

	it("takes over explicitly and replaces the follow-up dropdown with a note composer", async () => {
		const owned: Feedback = {
			...feedback,
			status: "IN_PROGRESS",
			ownerEmail: "admin@nav.no",
			ownerName: "Admin",
			readByMe: true,
			version: 2,
		};
		serverItems = [
			{
				...feedback,
				status: "IN_PROGRESS",
				ownerEmail: "other@nav.no",
				ownerName: "Other admin",
			},
		];
		save(owned);
		save({
			...owned,
			version: 3,
			history: [
				{
					id: 1,
					actorEmail: "admin@nav.no",
					actorName: "Admin",
					action: "NOTE",
					note: "Useful suggestion",
					ticketUrl: null,
					previousOwnerName: null,
					status: null,
					createdAt: "2026-10-01T11:00:00Z",
				},
			],
		});
		render(<FeedbackInbox />);
		fireEvent.click(
			(await screen.findAllByRole("button", { name: "Take over" }))[0],
		);
		const note = await screen.findByRole("textbox", { name: "Internal note" });
		fireEvent.change(note, {
			target: { value: "Useful suggestion" },
		});
		fireEvent.keyDown(note, { key: "Enter", ctrlKey: true });
		await waitFor(() =>
			expect(FeedbackApi.act).toHaveBeenLastCalledWith(
				expect.objectContaining({ version: owned.version }),
				"NOTE",
				"Useful suggestion",
				undefined,
				undefined,
			),
		);
		expect(await screen.findByText("Added internal note")).toBeInTheDocument();
		expect(screen.getByRole("textbox", { name: "Internal note" })).toHaveValue(
			"",
		);
		expect(screen.queryByLabelText("Follow-up action")).not.toBeInTheDocument();
	});

	it("rolls back a failed status change and retains the note when reloading after a conflict", async () => {
		const owned: Feedback = {
			...feedback,
			status: "IN_PROGRESS",
			ownerEmail: "admin@nav.no",
			version: 2,
		};
		serverItems = [owned];
		vi.mocked(FeedbackApi.act).mockRejectedValue(
			new Error("The feedback changed. Reload it before continuing"),
		);
		render(<FeedbackInbox />);
		fireEvent.change(
			await screen.findByRole("textbox", { name: "Internal note" }),
			{
				target: { value: "Keep this note" },
			},
		);
		const status = screen.getByRole("combobox", { name: "Status" });
		fireEvent.change(status, { target: { value: "CLOSED" } });
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"The feedback changed.",
		);
		await waitFor(() => expect(status).toHaveValue("IN_PROGRESS"));
		fireEvent.click(screen.getByRole("button", { name: "Add note" }));
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"The feedback changed.",
		);
		expect(screen.getByRole("textbox", { name: "Internal note" })).toHaveValue(
			"Keep this note",
		);
		fireEvent.click(screen.getByRole("button", { name: "Reload feedback" }));
		await waitFor(() => expect(FeedbackApi.detail).toHaveBeenCalledTimes(2));
		expect(
			await screen.findByRole("textbox", { name: "Internal note" }),
		).toHaveValue("Keep this note");
	});

	it("does not allow an administrator to award their own submission", async () => {
		serverItems = [
			{
				...feedback,
				submitterEmail: "admin@nav.no",
				status: "IN_PROGRESS",
				ownerEmail: "admin@nav.no",
			},
		];
		render(<FeedbackInbox />);
		await screen.findByLabelText("Scoring");
		expect(
			screen
				.getAllByRole("option", { name: "Points awarded" })
				.some((option) => option.hasAttribute("disabled")),
		).toBe(true);
		expect(
			screen.getByText(
				"Another administrator must award your feedback credit.",
			),
		).toBeInTheDocument();
	});

	it("navigates with keyboard shortcuts but ignores them while typing", async () => {
		serverItems.push({
			...feedback,
			id: "feedback-2",
			title: "Second submission",
		});
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: feedback.title });
		fireEvent.keyDown(document.body, { key: "j" });
		await screen.findByRole("heading", { name: "Second submission" });
		fireEvent.keyDown(document.body, { key: "/" });
		const search = screen.getByLabelText("Search feedback");
		expect(search).toHaveFocus();
		fireEvent.keyDown(search, { key: "k" });
		expect(window.location.pathname).toContain("feedback-2");
		fireEvent.keyDown(search, { key: "Escape" });
		expect(search).not.toHaveFocus();
		fireEvent.keyDown(document.body, { key: "k" });
		await screen.findByRole("heading", { name: feedback.title });
	});

	it("keeps selection on refresh and selects the first match when filters exclude it", async () => {
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: feedback.title });
		const newer = { ...feedback, id: "feedback-2", title: "New feedback" };
		serverItems = [newer, ...serverItems];
		fireEvent.click(screen.getByRole("button", { name: "Refresh inbox" }));
		await screen.findByRole("option", { name: /New feedback/ });
		expect(
			screen.getByRole("heading", { name: feedback.title }),
		).toBeInTheDocument();
		serverItems = [newer];
		fireEvent.change(screen.getByLabelText("Owner filter"), {
			target: { value: "mine" },
		});
		await screen.findByRole("heading", { name: newer.title });
		expect(window.location.pathname).toContain(newer.id);
	});

	it("adds validated ticket links without requiring an internal note", async () => {
		const owned = {
			...feedback,
			ownerEmail: "admin@nav.no",
			status: "IN_PROGRESS" as const,
		};
		serverItems = [owned];
		save({
			...owned,
			version: 2,
			history: [
				{
					id: 1,
					actorEmail: "admin@nav.no",
					actorName: "Admin",
					action: "EXTERNAL_TICKET",
					note: "",
					ticketUrl: "https://example.org/ticket",
					previousOwnerName: null,
					status: null,
					createdAt: "2026-10-01T11:00:00Z",
				},
			],
		});
		render(<FeedbackInbox />);
		fireEvent.click(await screen.findByRole("tab", { name: "Link ticket" }));
		const input = screen.getByLabelText("Ticket URL");
		fireEvent.change(input, { target: { value: "javascript:alert(1)" } });
		expect(screen.getByRole("button", { name: "Link ticket" })).toBeDisabled();
		fireEvent.change(input, {
			target: { value: "https://example.org/ticket" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Link ticket" }));
		await waitFor(() =>
			expect(FeedbackApi.act).toHaveBeenCalledWith(
				expect.objectContaining({ id: feedback.id }),
				"EXTERNAL_TICKET",
				"",
				"https://example.org/ticket",
				undefined,
			),
		);
		await screen.findByText("Linked ticket");
		expect(screen.getByLabelText("Ticket URL")).toHaveValue("");
	});

	it("appends cursor pages without discarding the current selection", async () => {
		vi.mocked(FeedbackApi.search)
			.mockResolvedValueOnce({
				items: [
					feedback,
					...Array.from({ length: 49 }, (_, index) => ({
						...feedback,
						id: `page-one-${index}`,
						title: `Feedback ${index}`,
					})),
				],
				nextCursor: "next-page",
				actorEmail: "admin@nav.no",
				counts,
			})
			.mockResolvedValueOnce({
				items: [{ ...feedback, id: "older", title: "Older feedback" }],
				nextCursor: null,
				actorEmail: "admin@nav.no",
				counts,
			});
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: feedback.title });
		fireEvent.click(screen.getByRole("button", { name: "Load more feedback" }));
		await screen.findByRole("option", { name: /Older feedback/ });
		expect(
			screen.getByRole("heading", { name: feedback.title }),
		).toBeInTheDocument();
		expect(vi.mocked(FeedbackApi.search).mock.calls[1][0].get("cursor")).toBe(
			"next-page",
		);
	});

	it("surfaces read-state failures without claiming the submission", async () => {
		vi.mocked(FeedbackApi.markRead).mockRejectedValue(
			new Error("Read state could not be saved"),
		);
		render(<FeedbackInbox />);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Read state could not be saved",
		);
		expect(FeedbackApi.act).not.toHaveBeenCalled();
	});

	it("restores selection on browser back navigation", async () => {
		serverItems.push({ ...feedback, id: "second", title: "Second submission" });
		render(<FeedbackInbox />);
		await screen.findByRole("heading", { name: feedback.title });
		fireEvent.click(screen.getByRole("option", { name: /Second submission/ }));
		await screen.findByRole("heading", { name: "Second submission" });
		act(() => {
			originalReplaceState(
				null,
				"",
				`/appsec/feedback/${feedback.id}?status=active&owner=all`,
			);
			window.dispatchEvent(new PopStateEvent("popstate"));
		});
		await screen.findByRole("heading", { name: feedback.title });
	});

	it("ignores a late detail response for a previously selected submission", async () => {
		const other = { ...feedback, id: "second", title: "Second submission" };
		serverItems.push(other);
		let resolveFirst!: (result: Feedback) => void;
		vi.mocked(FeedbackApi.detail).mockImplementationOnce(
			() =>
				new Promise<Feedback>((resolve) => {
					resolveFirst = resolve;
				}),
		);
		render(<FeedbackInbox />);
		await waitFor(() => expect(FeedbackApi.detail).toHaveBeenCalledTimes(1));
		fireEvent.click(screen.getByRole("option", { name: /Second submission/ }));
		await screen.findByRole("heading", { name: other.title });
		await act(async () => resolveFirst(feedback));
		expect(
			screen.getByRole("heading", { name: other.title }),
		).toBeInTheDocument();
		expect(FeedbackApi.markRead).not.toHaveBeenCalledWith(
			feedback.id,
			expect.any(AbortSignal),
		);
	});

	it("polls every minute without discarding an owner's composer draft", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		serverItems = [
			{ ...feedback, ownerEmail: "admin@nav.no", status: "IN_PROGRESS" },
		];
		render(<FeedbackInbox />);
		const note = await screen.findByRole("textbox", { name: "Internal note" });
		fireEvent.change(note, { target: { value: "Keep this draft" } });
		await act(async () => {
			vi.advanceTimersByTime(60_000);
		});
		await waitFor(() => expect(FeedbackApi.search).toHaveBeenCalledTimes(2));
		expect(screen.getByRole("textbox", { name: "Internal note" })).toHaveValue(
			"Keep this draft",
		);
		expect(FeedbackApi.act).not.toHaveBeenCalled();
	});
});

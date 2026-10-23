export type FeedbackStatus = "OPEN" | "IN_PROGRESS" | "CLOSED";
export type FeedbackScoreDecision = "PENDING" | "QUALIFIED" | "NOT_QUALIFIED";
export type FeedbackAction =
	| "CLAIM"
	| "RELEASE"
	| "NOTE"
	| "INTERNAL_DISCUSSION"
	| "EXTERNAL_TICKET"
	| "CONTACT_SUBMITTER"
	| "CLOSE"
	| "REOPEN"
	| "QUALIFY"
	| "DECLINE"
	| "TAKE_OVER"
	| "STATUS";
export type Feedback = {
	id: string;
	submitterId: string;
	submitterName: string;
	submitterEmail: string;
	submitterActive: boolean;
	seasonId: string;
	title: string;
	description: string;
	status: FeedbackStatus;
	ownerEmail: string | null;
	ownerName: string | null;
	readByMe: boolean;
	awardedPoints: number | null;
	scoreDecision: FeedbackScoreDecision;
	creditId: string | null;
	version: number;
	createdAt: string;
	history: {
		id: number;
		actorEmail: string | null;
		action: FeedbackAction;
		note: string;
		ticketUrl: string | null;
		createdAt: string;
		actorName: string | null;
		previousOwnerName: string | null;
		status: FeedbackStatus | null;
	}[];
};
export type FeedbackPage = {
	items: Feedback[];
	nextCursor: string | null;
	actorEmail: string;
	counts: FeedbackCounts;
};
export type FeedbackCounts = {
	active: number;
	open: number;
	inProgress: number;
	resolved: number;
	all: number;
	unread: number;
	unassigned: number;
	mine: number;
};

async function checkedRequest(
	path: string,
	method = "GET",
	body?: unknown,
	signal?: AbortSignal,
): Promise<Response> {
	const response = await fetch(path, {
		method,
		headers:
			body === undefined ? undefined : { "Content-Type": "application/json" },
		body: body === undefined ? undefined : JSON.stringify(body),
		signal,
	});
	if (!response.ok) {
		const problem: unknown = response.headers
			.get("content-type")
			?.includes("application/problem+json")
			? await response.json()
			: null;
		throw new Error(
			problem &&
				typeof problem === "object" &&
				"detail" in problem &&
				typeof problem.detail === "string"
				? problem.detail
				: "The feedback request could not be completed. Try again.",
		);
	}
	return response;
}

async function request<T>(
	path: string,
	method = "GET",
	body?: unknown,
	signal?: AbortSignal,
): Promise<T> {
	return (await checkedRequest(path, method, body, signal)).json();
}

export const FeedbackApi = {
	submit: (body: { id: string; title: string; description: string }) =>
		request<{ id: string }>("/api/feedback", "POST", body),
	search: (params: URLSearchParams, signal?: AbortSignal) =>
		request<FeedbackPage>(
			`/api/admin/feedback?${params}`,
			"GET",
			undefined,
			signal,
		),
	detail: (id: string, signal?: AbortSignal) =>
		request<Feedback>(
			`/api/admin/feedback/${encodeURIComponent(id)}`,
			"GET",
			undefined,
			signal,
		),
	markRead: async (id: string, signal?: AbortSignal) => {
		await checkedRequest(
			`/api/admin/feedback/${encodeURIComponent(id)}/read`,
			"POST",
			undefined,
			signal,
		);
	},
	act: (
		feedback: Feedback,
		action: FeedbackAction,
		note = "",
		ticketUrl?: string,
		status?: FeedbackStatus,
	) =>
		request<Feedback>(
			`/api/admin/feedback/${encodeURIComponent(feedback.id)}/actions`,
			"POST",
			{
				expectedVersion: feedback.version,
				action,
				note,
				ticketUrl,
				status,
			},
		),
};

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
	| "DECLINE";
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
	}[];
};
export type FeedbackPage = {
	items: Feedback[];
	nextOffset: number | null;
	actorEmail: string;
};

async function request<T>(
	path: string,
	method = "GET",
	body?: unknown,
): Promise<T> {
	const response = await fetch(path, {
		method,
		headers:
			body === undefined ? undefined : { "Content-Type": "application/json" },
		body: body === undefined ? undefined : JSON.stringify(body),
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
	return response.json();
}

export const FeedbackApi = {
	submit: (body: { id: string; title: string; description: string }) =>
		request<{ id: string }>("/api/feedback", "POST", body),
	search: (params: URLSearchParams) =>
		request<FeedbackPage>(`/api/admin/feedback?${params}`),
	detail: (id: string) =>
		request<Feedback>(`/api/admin/feedback/${encodeURIComponent(id)}`),
	act: (
		feedback: Feedback,
		action: FeedbackAction,
		note = "",
		ticketUrl?: string,
	) =>
		request<Feedback>(
			`/api/admin/feedback/${encodeURIComponent(feedback.id)}/actions`,
			"POST",
			{
				expectedVersion: feedback.version,
				action,
				note,
				ticketUrl,
			},
		),
};

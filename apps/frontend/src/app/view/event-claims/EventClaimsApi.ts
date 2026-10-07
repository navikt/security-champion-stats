export type ContributionStatus =
	| "PENDING"
	| "APPROVED"
	| "REJECTED"
	| "REVOKED";

export type ClaimContributorRequest = {
	participantId: string;
	contribution: string;
};
export type EventClaimRequest = {
	name: string;
	description: string;
	startDate: string;
	endDate: string;
	location: string;
	type: string;
	externalEvent: boolean;
	links: string[];
	invitationEvidence: string;
	contributors: ClaimContributorRequest[];
	eventId: string | null;
	expectedVersion?: number;
};
export type EventClaim = Omit<EventClaimRequest, "contributors"> & {
	id: string;
	submitterId: string;
	seasonId: string;
	seasonStartsOn: string;
	version: number;
	published: boolean;
	editable: boolean;
	contributors: (ClaimContributorRequest & {
		fullName: string;
		status: ContributionStatus;
		creditId: string | null;
	})[];
	reviews: {
		participantId: string;
		fullName: string;
		decision: ContributionStatus;
		reason: string;
		createdAt: string;
	}[];
};
export type EventClaimOverview = {
	currentParticipantId: string | null;
	seasonStartsOn: string;
	points: number;
	participants: { id: string; fullName: string }[];
	claims: EventClaim[];
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
		const detail =
			problem &&
			typeof problem === "object" &&
			"detail" in problem &&
			typeof problem.detail === "string"
				? problem.detail
				: "The event claim request could not be completed. Try again.";
		throw new Error(detail);
	}
	return response.json();
}

export const EventClaimsApi = {
	overview: (admin: boolean) =>
		request<EventClaimOverview>(
			admin ? "/api/admin/event-claims" : "/api/event-claims",
		),
	save: (claim: EventClaimRequest, id?: string) =>
		request<EventClaim>(
			id ? `/api/event-claims/${encodeURIComponent(id)}` : "/api/event-claims",
			id ? "PUT" : "POST",
			claim,
		),
	review: (
		claim: EventClaim,
		participantId: string,
		decision: ContributionStatus,
		reason: string,
	) =>
		request<EventClaim>(
			`/api/admin/event-claims/${encodeURIComponent(claim.id)}/reviews`,
			"POST",
			{
				participantId,
				decision,
				reason,
				expectedVersion: claim.version,
			},
		),
};

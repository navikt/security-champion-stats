import type {
	AdminProgramParticipant,
	ActivityCredit,
	AdminScoringOverview,
	AdminDashboardOverview,
	DeltaEventMapping,
	DeltaCategory,
	DeltaEligibleCategory,
	LeaderboardEntry,
	Me,
	ParticipantSeasonScore,
	ProgramParticipant,
	ProgramParticipantSummary,
	SCData,
	SecurityEvent,
	SlackMappingOverview,
	SlackMembershipConfiguration,
	SlackMembershipPreview,
	SlackMembershipAnnouncement,
	HistoryPage,
	AuditResponse,
	ParticipantHistoryEntry,
} from "../../utils/Variables";

export const Apies = {
	getSlackMembershipConfiguration: async (): Promise<SlackMembershipConfiguration> => {
		const res = await fetch("/api/admin/slack/membership");
		if (!res.ok) throw new Error("We couldn't load membership sync configuration. Try again.");
		return res.json();
	},
	getSlackMembershipPreview: async (): Promise<SlackMembershipPreview> => {
		const res = await fetch("/api/admin/slack/membership/preview");
		if (!res.ok) throw new Error("We couldn't preview membership changes. Check the configuration and audit trail.");
		return res.json();
	},
	getSlackMembershipAnnouncements: async (): Promise<SlackMembershipAnnouncement[]> => {
		const res = await fetch("/api/admin/slack/membership/announcements");
		if (!res.ok) throw new Error("We couldn't load membership announcements. Try again.");
		return res.json();
	},
	triggerSlackMembershipSync: async (expectedVersion?: string): Promise<number> => {
		const res = await fetch("/api/admin/slack/membership/sync", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ expectedVersion }),
		});
		return res.status;
	},
	resolveSlackMembershipDelivery: async (id: string, retry: boolean): Promise<number> => {
		const res = await fetch(`/api/admin/slack/membership/announcements/${encodeURIComponent(id)}/resolve`, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ retry }),
		});
		return res.status;
	},
	getHistory: async (admin: boolean, search: string, cursor: string | null): Promise<HistoryPage> => {
		const query = new URLSearchParams({ size: "50", page: cursor || "0" });
		if (search) query.set("q", search);
		const res = await fetch(admin ? `/api/admin/audit?${query}` : "/api/history");
		if (!res.ok) {
			console.error("Failed to fetch history, status: ", res.status);
			throw new Error("We couldn't fetch history. Try again.");
		}
		if (admin) {
			const result: AuditResponse = await res.json();
			return {
				entries: result.items.map((entry) => ({
					id: entry.id,
					action: entry.action,
					outcome: entry.outcome,
					recordedAt: entry.createdAt,
					occurredAt: null,
					actor: entry.actorNavNoEmail,
					participantId: entry.targetParticipantId,
					runId: entry.correlationId,
					details: entry.details,
				})),
				nextCursor: (result.page + 1) * result.size < result.total ? String(result.page + 1) : null,
			};
		}
		const entries: ParticipantHistoryEntry[] = await res.json();
		return {
			entries: entries.map((entry) => ({
				id: entry.id,
				action: entry.action,
				outcome: "SUCCEEDED",
				recordedAt: entry.occurredAt,
				occurredAt: entry.occurredAt,
				details: {
					status: entry.status,
					creditType: entry.creditType,
					points: entry.points,
					sourceReference: entry.sourceReference,
					reason: entry.reason,
				},
			})),
			nextCursor: null,
		};
	},
	getAdminDashboard: async (): Promise<AdminDashboardOverview | null> => {
		const res = await fetch("/api/admin/dashboard/overview");
		if (!res.ok) {
			console.error("Failed to fetch admin dashboard, status: ", res.status);
			return null;
		}
		return res.json();
	},
	triggerSlackSync: async (): Promise<number> => {
		const res = await fetch("/api/admin/slack/sync", { method: "POST" });
		if (!res.ok) {
			console.error("Failed to trigger Slack sync, status: ", res.status);
		}
		return res.status;
	},
	triggerDeltaSync: async (): Promise<number> => {
		const res = await fetch("/api/admin/delta/sync", { method: "POST" });
		if (!res.ok) {
			console.error("Failed to trigger Delta sync, status: ", res.status);
		}
		return res.status;
	},
	triggerGithubSync: async (): Promise<number> => {
		const res = await fetch("/api/admin/github/sync", { method: "POST" });
		if (!res.ok) {
			console.error("Failed to trigger GitHub sync, status: ", res.status);
		}
		return res.status;
	},
	triggerDeltaEventImport: async (): Promise<number> => {
		const res = await fetch("/api/admin/delta/events/sync", { method: "POST" });
		if (!res.ok) {
			console.error("Failed to trigger Delta event import, status: ", res.status);
		}
		return res.status;
	},
	getAdminParticipants: async (): Promise<AdminProgramParticipant[] | null> => {
		const res = await fetch("/api/admin/participants");
		if (!res.ok) {
			console.error("Failed to fetch program participants, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getScoringOverview: async (): Promise<AdminScoringOverview | null> => {
		const res = await fetch("/api/admin/scoring");
		if (!res.ok) {
			console.error("Failed to fetch scoring overview, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getParticipantSeasonScore: async (): Promise<ParticipantSeasonScore | null> => {
		const res = await fetch("/api/scoring/me");
		if (!res.ok) {
			console.error("Failed to fetch participant season score, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getLeaderboard: async (): Promise<LeaderboardEntry[] | null> => {
		const res = await fetch("/api/leaderboard");
		if (!res.ok) {
			console.error("Failed to fetch leaderboard, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getSlackMappingOverview: async (): Promise<SlackMappingOverview | null> => {
		const res = await fetch("/api/admin/slack");
		if (!res.ok) {
			console.error("Failed to fetch Slack mappings, status: ", res.status);
			return null;
		}
		return res.json();
	},
	addSlackMapping: async (slackUserId: string, participantId: string): Promise<number> => {
		const res = await fetch("/api/admin/slack", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ slackUserId, participantId }),
		});
		if (!res.ok) {
			console.error("Failed to add Slack mapping, status: ", res.status);
		}
		return res.status;
	},
	removeSlackMapping: async (slackUserId: string): Promise<number> => {
		const res = await fetch(
			`/api/admin/slack/mappings/${encodeURIComponent(slackUserId)}`,
			{ method: "DELETE" },
		);
		if (!res.ok) {
			console.error("Failed to remove Slack mapping, status: ", res.status);
		}
		return res.status;
	},
	getDeltaEventMappings: async (): Promise<DeltaEventMapping[] | null> => {
		const res = await fetch("/api/admin/delta/event-mappings");
		if (!res.ok) {
			console.error("Failed to fetch Delta event mappings, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getDeltaCategories: async (): Promise<DeltaCategory[] | null> => {
		const res = await fetch("/api/admin/delta/categories");
		if (!res.ok) {
			console.error("Failed to fetch Delta categories, status: ", res.status);
			return null;
		}
		return res.json();
	},
	addDeltaEventMapping: async (
		programEventName: string,
		deltaEventUuid: string,
	): Promise<number> => {
		const res = await fetch("/api/admin/delta/event-mappings", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ programEventName, deltaEventUuid }),
		});
		if (!res.ok) {
			console.error("Failed to add Delta event mapping, status: ", res.status);
		}
		return res.status;
	},
	getDeltaEligibleCategories: async (): Promise<DeltaEligibleCategory[] | null> => {
		const res = await fetch("/api/admin/delta/eligible-categories");
		if (!res.ok) {
			console.error("Failed to fetch eligible Delta categories, status: ", res.status);
			return null;
		}
		return res.json();
	},
	addDeltaEligibleCategory: async (deltaCategoryId: number): Promise<number> => {
		const res = await fetch("/api/admin/delta/eligible-categories", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ deltaCategoryId }),
		});
		if (!res.ok) {
			console.error("Failed to add eligible Delta category, status: ", res.status);
		}
		return res.status;
	},
	removeDeltaEligibleCategory: async (categoryId: number): Promise<number> => {
		const res = await fetch(
			`/api/admin/delta/eligible-categories/${encodeURIComponent(categoryId)}`,
			{ method: "DELETE" },
		);
		if (!res.ok) {
			console.error("Failed to remove eligible Delta category, status: ", res.status);
		}
		return res.status;
	},
	removeDeltaEventMapping: async (id: string): Promise<number> => {
		const res = await fetch(
			`/api/admin/delta/event-mappings/${encodeURIComponent(id)}`,
			{ method: "DELETE" },
		);
		if (!res.ok) {
			console.error("Failed to remove Delta event mapping, status: ", res.status);
		}
		return res.status;
	},
	getParticipantCredits: async (
		id: string,
	): Promise<ActivityCredit[] | null> => {
		const res = await fetch(
			`/api/admin/scoring/participants/${encodeURIComponent(id)}/credits`,
		);
		if (!res.ok) {
			console.error("Failed to fetch participant credits, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getMembers: async (): Promise<ProgramParticipantSummary[]> => {
		const res = await fetch("/api/members");
		if (!res.ok) {
			console.warn("Failed to fetch members, status: ", res.status);
			return [];
		}
		return await res.json();
	},
	addMember: async (email: string) => {
		const res = await fetch("/api/admin/member", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ email: email }),
		});
		if (!res.ok)
			console.error("Failed to add member, with status code ", res.status);
		return res.status;
	},
	deleteMember: async (id: string) => {
		const res = await fetch(`/api/admin/member/${encodeURIComponent(id)}`, {
			method: "DELETE",
		});
		if (!res.ok)
			console.error("Failed to delete member, with status code ", res.status);
		return res.status;
	},
	updateParticipantStatus: async (id: string, active: boolean): Promise<number> => {
		const res = await fetch(
			`/api/admin/participants/${encodeURIComponent(id)}/status`,
			{
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ active }),
			},
		);
		if (!res.ok) {
			console.error("Failed to update participant status, status: ", res.status);
		}
		return res.status;
	},
	deleteParticipant: async (id: string, reason: string): Promise<number> => {
		const res = await fetch(
			`/api/admin/participants/${encodeURIComponent(id)}`,
			{
				method: "DELETE",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ confirmed: true, reason }),
			},
		);
		if (!res.ok) {
			console.error("Failed to delete participant, status: ", res.status);
		}
		return res.status;
	},
	addPointAdjustment: async (
		participantId: string,
		pointsDelta: number,
		reason: string,
		sourceCreditId?: string,
	): Promise<number> => {
		const res = await fetch(
			`/api/admin/scoring/participants/${encodeURIComponent(participantId)}/adjustments`,
			{
			method: "POST",
			headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ pointsDelta, reason, sourceCreditId }),
			},
		);
		if (!res.ok) {
			console.error("Failed to adjust points, with status code: ", res.status);
		}
		return res.status;
	},
	updateNextResetDate: async (nextResetDate: string): Promise<number> => {
		const res = await fetch("/api/admin/scoring/season/reset-date", {
			method: "PUT",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ nextResetDate }),
		});
		if (!res.ok) {
			console.error("Failed to update reset date, status: ", res.status);
		}
		return res.status;
	},
	resetSeason: async (reason: string): Promise<number> => {
		const res = await fetch("/api/admin/scoring/season/reset", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ confirmed: true, reason }),
		});
		if (!res.ok) {
			console.error("Failed to reset season, status: ", res.status);
		}
		return res.status;
	},
	joinProgram: async (): Promise<boolean> => {
		const res = await fetch("/api/enroll", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
		});
		if (!res.ok) {
			console.error("Failed to join the program, with status code: ", res.status);
			return false;
		}
		return true;
	},
	leaveProgram: async (): Promise<void> => {
		const res = await fetch("/api/leave", { method: "POST" });
		if (!res.ok) {
			console.error("Failed to leave the program, status: ", res.status);
			throw new Error("We couldn't leave the program. Try again.");
		}
	},
	validatePerson: async (): Promise<Me> => {
		const res = await fetch("/api/validate");
		if (!res.ok) {
			console.error("Failed to validate user, status: ", res.status);
			return { username: "", displayName: null, isAdmin: false, isParticipant: false, isActive: false };
		}

		return await res.json();
	},
	getSCData: async (): Promise<SCData[]> => {
		const res = await fetch("/api/admin/dashboard/members");
		if (!res.ok) {
			console.error("Failed to fetch SCData, with status: ", res.status);
			return [];
		}
		return res.json();
	},
	fetchMembership: async (): Promise<ProgramParticipant | null> => {
		const res = await fetch("/api/membership", {
			method: "GET",
			headers: { "Content-Type": "application/json" },
		});
		if (!res.ok) {
			console.error("Failed to fetch membership, with status: ", res.status);
			return null;
		}
		return res.json();
	},
	fetchEvents: async (): Promise<SecurityEvent[]> => {
		const res = await fetch("/api/events");
		if (!res.ok) {
			console.error("Failed to fetch events, with status: ", res.status);
			return [];
		}
		return res.json();
	},
	createEvent: async (event: SecurityEvent): Promise<SecurityEvent> => {
		const response = await fetch("/api/events", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(event),
		});

		if (!response.ok) {
			console.error("Failed to create event, with status: ", response.status);
			const problem: unknown =
				response.headers.get("content-type")?.includes("json")
					? await response.json()
					: null;
			const message =
				typeof problem === "object" &&
				problem !== null &&
				"detail" in problem &&
				typeof problem.detail === "string"
					? problem.detail
					: "We couldn't create the event. Try again.";
			throw new Error(message);
		}

		return response.json();
	},
};

export const AUTHENTICATED_FAILED = "Authentication failed";
export const FAILED_FETCH = "Failed fetch data";
export const INTERNAL_ERROR = "Internal server error";
export const MISSING_VALUE = "Failed fetch, due to missing value";
export const MISSING_GROUP = "Missing group id value, failed to validate admin";
export const FAILED_TO_JOIN = "Failed to join member to program, backend error";

export type Me = {
	username: string;
	displayName: string | null;
	isAdmin: boolean;
	isParticipant: boolean;
	isActive: boolean;
};
export type Member = {
	id: string;
	email: string;
	points: number;
	fullname: string;
	level: string;
	inGame: boolean;
	joinedAt: string;
	teams: string[];
};
export type ProgramParticipantSummary = {
	id: string;
	fullname: string;
	teams: string[];
};
export type ProgramParticipant = ProgramParticipantSummary & {
	email: string;
	active: boolean;
	joinedAt: string;
	status: "ACTIVE" | "LEFT" | "DEACTIVATED";
};
export type AdminProgramParticipant = ProgramParticipant;
export type HistoryEntry = {
	id: string;
	action: string;
	outcome: string;
	recordedAt: string;
	occurredAt: string | null;
	details: Record<string, string | number | boolean | null>;
	actor?: string | null;
	participantId?: string | null;
	runId?: string | null;
};
export type HistoryPage = {
	entries: HistoryEntry[];
	nextCursor: string | null;
};
export type AuditResponse = {
	items: {
		id: string;
		createdAt: string;
		action: string;
		outcome: "SUCCEEDED" | "FAILED" | "PARTIAL";
		actorNavNoEmail: string | null;
		targetParticipantId: string | null;
		correlationId: string | null;
		details: Record<string, string>;
	}[];
	total: number;
	page: number;
	size: number;
};
export type AuditCategory = "all" | "syncs" | "credits" | "admin";
export type ParticipantHistoryEntry = {
	id: string;
	occurredAt: string;
	type: "MEMBERSHIP" | "CREDIT" | "ADJUSTMENT";
	action: string;
	status: string | null;
	creditType: string | null;
	points: number | null;
	sourceReference: string | null;
	reason: string | null;
};
export type SeasonSummary = {
	id: string;
	startsOn: string;
	endsOn: string | null;
	nextResetDate: string;
};
export type ParticipantSeasonScore = {
	season: SeasonSummary;
	points: number;
	level: "Novice" | "Apprentice" | "Adept" | "Expert";
	rank: number | null;
};
export type LeaderboardEntry = {
	fullName: string;
	rank: number;
	points: number;
	level: "Novice" | "Apprentice" | "Adept" | "Expert";
	isCurrentUser: boolean;
};
export type ActivityCredit = {
	id: string;
	creditType:
		| "SLACK_WEEK"
		| "DELTA_REGISTRATION"
		| "GITHUB_COMMIT"
		| "GITHUB_PULL_REQUEST"
		| "SECURITY_EVENT_CONTRIBUTION";
	sourceReference: string;
	points: number;
	seasonStartsOn: string;
};
export type AdminParticipantScore = {
	participantId: string;
	fullName: string;
	email: string;
	active: boolean;
	points: number;
	level: "Novice" | "Apprentice" | "Adept" | "Expert";
};
export type AdminScoringOverview = {
	season: SeasonSummary;
	today: string;
	participants: AdminParticipantScore[];
};
export type AdminDashboardCreditTotal = {
	creditType: string;
	points: number;
};
export type AdminDashboardWeeklyTotals = {
	weekStarting: string;
	pointsByCreditType: Record<string, number>;
};
export type AdminDashboardIntegrationStatus = {
	enabled: boolean;
	lastAttemptAt: string | null;
	lastSuccessAt: string | null;
	outcome: "RUNNING" | "SUCCEEDED" | "PARTIAL_FAILURE" | "FAILED" | null;
	failureSummary: string | null;
};
export type AdminDashboardOverview = {
	season: SeasonSummary;
	today: string;
	activeParticipantCount: number;
	eventRegistrationCount: number;
	pointsByCreditType: AdminDashboardCreditTotal[];
	weeklyTotals: AdminDashboardWeeklyTotals[];
	slack: AdminDashboardIntegrationStatus & {
		messagesScanned: number;
		creditsAwarded: number;
		duplicateCredits: number;
		unmappedAuthors: number;
	};
	delta: AdminDashboardIntegrationStatus & {
		eventsScanned: number;
		creditsAwarded: number;
		duplicateCredits: number;
		unmatchedRegistrations: number;
		failedEvents: number;
	};
	github: AdminDashboardIntegrationStatus & {
		contributionsScanned: number;
		creditsAwarded: number;
		duplicateCredits: number;
		unmappedAuthors: number;
	};
};
export type SlackAccountMapping = {
	slackUserId: string;
	participantId: string;
	participantName: string;
	participantEmail: string;
	createdAt: string;
};
export type UnmappedSlackAuthor = {
	slackUserId: string;
	firstSeenAt: string;
	lastSeenAt: string;
};
export type SlackMappingOverview = {
	mappings: SlackAccountMapping[];
	unmappedAuthors: UnmappedSlackAuthor[];
};
export type SlackMembershipConfiguration = {
	enabled: boolean;
	dryRun: boolean;
};
export type SlackMembershipPreview = {
	version: string;
	addedUserIds: string[];
	removedUserIds: string[];
	unresolvedParticipantIds: string[];
	activeParticipants: number;
};
export type SlackMembershipAnnouncement = {
	id: string;
	participantId: string;
	slackUserId: string;
	kind: "WELCOME" | "REMOVAL";
	status: "PENDING" | "SENDING" | "SENT" | "SUPPRESSED" | "CANCELLED" | "UNCERTAIN";
};
export type DeltaEventMapping = {
	id: string;
	programEventName: string;
	deltaEventUuid: string;
	createdAt: string;
};
export type DeltaEligibleCategory = {
	categoryId: number;
	categoryName: string;
	createdAt: string;
};
export type DeltaCategory = {
	id: number;
	name: string;
};
export type SCData = { timestamp: string; amount: number };
export type Row = { year: number; count: number };

export interface SecurityEvent {
	id: string;
	name: string;
	description: string;
	startDate: string;
	endDate: string;
	location: string;
	type: SecurityEventType;
	externalEvent: boolean;
	deltaEvent: boolean;
	amountOfPeopleJoined?: number;
	link?: string | null;
	allDay?: boolean;
}

export type SecurityEventType = "meetup" | "workshop" | "event";

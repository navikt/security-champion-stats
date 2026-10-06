export const AUTHENTICATED_FAILED = "Authentication failed";
export const FAILED_FETCH = "Failed fetch data";
export const INTERNAL_ERROR = "Internal server error";
export const MISSING_VALUE = "Failed fetch, due to missing value";
export const MISSING_GROUP = "Missing group id value, failed to validate admin";
export const FAILED_TO_JOIN = "Failed to join member to program, backend error";

export type Me = {
	username: string;
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
};
export type AdminProgramParticipant = ProgramParticipant;
export type SeasonSummary = {
	id: string;
	startsOn: string;
	endsOn: string | null;
	nextResetDate: string;
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
}

export type SecurityEventType = "meetup" | "workshop";

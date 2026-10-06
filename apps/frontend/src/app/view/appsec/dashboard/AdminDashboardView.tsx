import { BodyShort, Button, Heading, Table, VStack } from "@navikt/ds-react";
import type {
	AdminDashboardIntegrationStatus,
	AdminDashboardOverview,
} from "@/app/utils/Variables";

const CREDIT_TYPE_LABELS: Record<string, string> = {
	SLACK_WEEK: "Slack participation",
	DELTA_REGISTRATION: "Event registration",
	GITHUB_COMMIT: "GitHub commit",
	GITHUB_PULL_REQUEST: "GitHub pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security event contribution",
	POINT_ADJUSTMENT: "Administrator adjustments",
};

const ADMIN_LINKS = [
	{ href: "/appsec/membership", label: "Manage participants" },
	{ href: "/appsec/scoring", label: "Manage scoring" },
	{ href: "/appsec/events", label: "Manage events" },
	{ href: "/appsec/slack", label: "Manage Slack mappings" },
	{ href: "/appsec/delta", label: "Manage Delta mappings" },
];

export type ScoringIntegration = "slack" | "delta" | "github";

export function AdminDashboardView({
	overview,
	onTriggerSync,
	triggeringSync,
	triggerError,
}: {
	overview: AdminDashboardOverview;
	onTriggerSync: (integration: ScoringIntegration) => void;
	triggeringSync: ScoringIntegration | null;
	triggerError: { integration: ScoringIntegration; message: string } | null;
}) {
	const creditTypes = overview.pointsByCreditType.map(
		(entry) => entry.creditType,
	);

	return (
		<VStack gap="space-24">
			<Heading level="1" size="xlarge">
				Program dashboard
			</Heading>

			<section aria-labelledby="program-metrics">
				<Heading level="2" size="large" id="program-metrics">
					Program metrics
				</Heading>
				<dl>
					<div>
						<dt>Active participants</dt>
						<dd>{overview.activeParticipantCount}</dd>
					</div>
					<div>
						<dt>Current-season event registrations</dt>
						<dd>{overview.eventRegistrationCount}</dd>
					</div>
				</dl>
			</section>

			<section aria-labelledby="season-points">
				<Heading level="2" size="large" id="season-points">
					Current-season points by activity
				</Heading>
				<ul>
					{overview.pointsByCreditType.map(({ creditType, points }) => (
						<li key={creditType}>
							{CREDIT_TYPE_LABELS[creditType] ?? creditType}: {points}
						</li>
					))}
				</ul>
				<Heading level="3" size="medium">
					Weekly points since {overview.season.startsOn}
				</Heading>
				<Table size="small">
					<Table.Header>
						<Table.Row>
							<Table.HeaderCell scope="col">Week starting</Table.HeaderCell>
							{creditTypes.map((creditType) => (
								<Table.HeaderCell key={creditType} scope="col">
									{CREDIT_TYPE_LABELS[creditType] ?? creditType}
								</Table.HeaderCell>
							))}
						</Table.Row>
					</Table.Header>
					<Table.Body>
						{overview.weeklyTotals.map((week) => (
							<Table.Row key={week.weekStarting}>
								<Table.HeaderCell scope="row">
									{week.weekStarting}
								</Table.HeaderCell>
								{creditTypes.map((creditType) => (
									<Table.DataCell key={creditType}>
										{week.pointsByCreditType[creditType] ?? 0}
									</Table.DataCell>
								))}
							</Table.Row>
						))}
					</Table.Body>
				</Table>
			</section>

			<section aria-labelledby="integration-health">
				<Heading level="2" size="large" id="integration-health">
					Integration health
				</Heading>
				<div>
					<SyncStatus
						name="Slack"
						integration="slack"
						status={overview.slack}
						onTriggerSync={onTriggerSync}
						triggeringSync={triggeringSync}
						triggerError={triggerError}
					/>
					<SyncStatus
						name="Delta"
						integration="delta"
						status={overview.delta}
						onTriggerSync={onTriggerSync}
						triggeringSync={triggeringSync}
						triggerError={triggerError}
					/>
					<SyncStatus
						name="GitHub"
						integration="github"
						status={overview.github}
						details={[
							["Contributions scanned", overview.github.contributionsScanned],
							["Credits awarded", overview.github.creditsAwarded],
							["Duplicate credits", overview.github.duplicateCredits],
							["Unmapped authors", overview.github.unmappedAuthors],
						]}
						onTriggerSync={onTriggerSync}
						triggeringSync={triggeringSync}
						triggerError={triggerError}
					/>
				</div>
			</section>

			<section aria-labelledby="admin-operations">
				<Heading level="2" size="large" id="admin-operations">
					Program administration
				</Heading>
				<ul>
					{ADMIN_LINKS.map(({ href, label }) => (
						<li key={href}>
							<a href={href}>{label}</a>
						</li>
					))}
				</ul>
			</section>
		</VStack>
	);
}

function SyncStatus({
	name,
	integration,
	status,
	details,
	onTriggerSync,
	triggeringSync,
	triggerError,
}: {
	name: string;
	details?: [string, number][];
	integration: ScoringIntegration;
	status: AdminDashboardIntegrationStatus;
	onTriggerSync: (integration: ScoringIntegration) => void;
	triggeringSync: ScoringIntegration | null;
	triggerError: { integration: ScoringIntegration; message: string } | null;
}) {
	const outcome = !status.enabled
		? "Sync is disabled"
		: status.outcome
			? {
					RUNNING: "Sync in progress",
					SUCCEEDED: "Last sync succeeded",
					PARTIAL_FAILURE: "Last sync partially failed",
					FAILED: "Last sync failed",
				}[status.outcome] ?? "Last sync status unknown"
			: "No sync recorded yet";

	return (
		<section aria-labelledby={`${name.toLowerCase()}-sync-status`}>
			<Heading level="3" size="medium" id={`${name.toLowerCase()}-sync-status`}>
				{name}
			</Heading>
			<BodyShort>{outcome}</BodyShort>
			<BodyShort>
				Last attempt: <FormattedDate value={status.lastAttemptAt} />
			</BodyShort>
			<BodyShort>
				Last successful sync: <FormattedDate value={status.lastSuccessAt} />
			</BodyShort>
			{details && (
				<dl>
					{details.map(([label, value]) => (
						<div key={label}>
							<dt>{label}</dt>
							<dd>{value}</dd>
						</div>
					))}
				</dl>
			)}
			<Button
				size="small"
				variant="secondary"
				loading={triggeringSync === integration}
				disabled={
					!status.enabled ||
					status.outcome === "RUNNING" ||
					triggeringSync !== null
				}
				onClick={() => onTriggerSync(integration)}
			>
				Run {name} sync now
			</Button>
			{triggerError?.integration === integration && (
				<BodyShort role="alert">{triggerError.message}</BodyShort>
			)}
			{status.failureSummary && (
				<BodyShort role="alert">{status.failureSummary}</BodyShort>
			)}
		</section>
	);
}

function FormattedDate({ value }: { value: string | null }) {
	if (value === null) return <>Not recorded</>;
	return <time dateTime={value}>{new Date(value).toLocaleString()}</time>;
}

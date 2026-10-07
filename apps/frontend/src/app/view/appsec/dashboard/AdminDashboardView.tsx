import Link from "next/link";
import {
	BodyShort,
	Box,
	Button,
	Checkbox,
	Heading,
	HGrid,
	HStack,
	Table,
	Tag,
	VStack,
} from "@navikt/ds-react";
import { useMemo, useState } from "react";
import type {
	AdminDashboardIntegrationStatus,
	AdminDashboardOverview,
	AdminDashboardWeeklyTotals,
} from "@/app/utils/Variables";

const CATEGORIES = [
	{ key: "SLACK_WEEK", label: "Slack", color: "slack" },
	{ key: "DELTA_REGISTRATION", label: "Event registration", color: "event" },
	{ key: "GITHUB_COMMIT", label: "Commit", color: "commit" },
	{ key: "GITHUB_PULL_REQUEST", label: "Pull request", color: "pullRequest" },
	{ key: "SECURITY_EVENT_CONTRIBUTION", label: "Sec. event", color: "securityEvent" },
	{ key: "POINT_ADJUSTMENT", label: "Admin adj.", color: "adjustment" },
] as const;

const CREDIT_TYPE_LABELS: Record<string, string> = {
	SLACK_WEEK: "Slack participation",
	DELTA_REGISTRATION: "Event registration",
	GITHUB_COMMIT: "GitHub commit",
	GITHUB_PULL_REQUEST: "GitHub pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security event contribution",
	POINT_ADJUSTMENT: "Administrator adjustments",
};

export type ScoringIntegration = "slack" | "delta" | "github";

type AttentionItem = {
	count: number;
	title: string;
	description: string;
	href: string;
	linkLabel: string;
	variant: "warning" | "danger" | "neutral";
};

function totalPoints(overview: AdminDashboardOverview): number {
	return overview.pointsByCreditType.reduce((total, entry) => total + entry.points, 0);
}

function integrationLabel(status: AdminDashboardIntegrationStatus): string {
	if (!status.enabled) return "Disabled";
	if (status.outcome === "RUNNING") return "Syncing…";
	if (status.outcome === "FAILED") return "Failing";
	if (status.outcome === "PARTIAL_FAILURE") return "Needs attention";
	if (status.outcome === "SUCCEEDED") return "Healthy";
	return "No sync recorded";
}

function integrationTone(status: AdminDashboardIntegrationStatus) {
	if (status.outcome === "FAILED") return "danger";
	if (status.outcome === "PARTIAL_FAILURE") return "warning";
	if (status.outcome === "SUCCEEDED") return "success";
	return "neutral";
}

function dateTime(value: string | null): string {
	if (!value) return "Not recorded";
	return new Date(value).toLocaleString(undefined, {
		day: "numeric",
		month: "short",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
	});
}

function weekDate(value: string): string {
	return new Date(`${value}T00:00:00`).toLocaleDateString(undefined, {
		day: "numeric",
		month: "short",
		year: "numeric",
	});
}

function weekTotal(week: AdminDashboardWeeklyTotals): number {
	return Object.values(week.pointsByCreditType).reduce((sum, value) => sum + value, 0);
}

function makeAttentionItems(overview: AdminDashboardOverview): AttentionItem[] {
	const items: AttentionItem[] = [];
	if (overview.github.unmappedAuthors > 0) {
		items.push({
			count: overview.github.unmappedAuthors,
			title: "Unmapped GitHub authors",
			description: "Contributions from these authors aren't credited to anyone.",
			href: "/appsec/audit?category=syncs&q=unmappedAuthors",
			linkLabel: "Review in audit →",
			variant: "warning",
		});
	}
	if (overview.delta.duplicateCredits > 0) {
		items.push({
			count: overview.delta.duplicateCredits,
			title: "Duplicate Delta credits skipped",
			description: "Duplicate credits are expected when events are re-scanned.",
			href: "/appsec/audit?category=syncs&q=duplicateCredits",
			linkLabel: "View in audit →",
			variant: "neutral",
		});
	}
	if (overview.delta.failedEvents > 0) {
		items.push({
			count: overview.delta.failedEvents,
			title: "Failed Delta events",
			description: "Some event registrations could not be processed.",
			href: "/appsec/audit?category=syncs&q=failedEvents",
			linkLabel: "View in audit →",
			variant: "warning",
		});
	}
	if (overview.delta.unmatchedRegistrations > 0) {
		items.push({
			count: overview.delta.unmatchedRegistrations,
			title: "Unmatched event registrations",
			description: "Registrations could not be matched to a program event.",
			href: "/appsec/delta",
			linkLabel: "Review Delta mappings →",
			variant: "warning",
		});
	}
	for (const integration of ["slack", "delta", "github"] as const) {
		const status = overview[integration];
		if (status.outcome !== "FAILED") continue;
		const name = integration === "github" ? "GitHub" : integration[0].toUpperCase() + integration.slice(1);
		items.push({
			count: 1,
			title: `${name} sync failed`,
			description: status.failureSummary || `The latest ${name} sync failed.`,
			href: `/appsec/audit?category=syncs&q=${encodeURIComponent(name)}`,
			linkLabel: "View sync in audit →",
			variant: "danger",
		});
	}
	return items;
}

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
	const [hideEmpty, setHideEmpty] = useState(true);
	const points = new Map(
		overview.pointsByCreditType.map(({ creditType, points: value }) => [
			creditType,
			value,
		]),
	);
	const maxPoints = Math.max(0, ...points.values());
	const integrations = [overview.slack, overview.delta, overview.github];
	const healthyCount = integrations.filter(
		(integration) => integration.enabled && integration.outcome === "SUCCEEDED",
	).length;
	const failedIntegration = integrations.some(
		(integration) => integration.outcome === "FAILED",
	);
	const attention = makeAttentionItems(overview);
	const sortedWeeks = useMemo(
		() =>
			[...overview.weeklyTotals].sort((a, b) =>
				b.weekStarting.localeCompare(a.weekStarting),
			),
		[overview.weeklyTotals],
	);
	const emptyWeekCount = sortedWeeks.filter((week) => weekTotal(week) === 0).length;
	const shownWeeks = sortedWeeks.filter(
		(week) => !hideEmpty || weekTotal(week) !== 0,
	);
	const firstWeek = sortedWeeks.at(-1)?.weekStarting;
	const lastWeek = sortedWeeks[0]?.weekStarting;

	return (
		<div className="hubRedesign dashboardView">
			<header className="hubRedesign__header dashboardView__header">
				<div>
					<BodyShort className="hubRedesign__eyebrow">Admin only</BodyShort>
					<Heading level="1" size="xlarge">Program dashboard</Heading>
				</div>
				<BodyShort className="hubRedesign__muted">
					Season {new Date(`${overview.season.startsOn}T00:00:00`).getFullYear()} · since {weekDate(overview.season.startsOn)}
				</BodyShort>
			</header>

			<HGrid className="dashboardView__kpis" columns={{ xs: 1, sm: 2, lg: 4 }} gap="space-12">
				<StatTile label="Active participants" value={String(overview.activeParticipantCount)} />
				<StatTile label="Event registrations" value={String(overview.eventRegistrationCount)} />
				<StatTile label="Points awarded" value={String(totalPoints(overview))} />
				<StatTile
					label="Integrations"
					value={`${healthyCount} / ${integrations.length}`}
					detail="healthy"
					variant={failedIntegration ? "danger" : healthyCount < integrations.length ? "warning" : "success"}
				/>
			</HGrid>

			<div className="hubRedesign__grid">
				<Box
					as="section"
					aria-labelledby="points-activity-heading"
					className="hubRedesign__card dashboardView__card"
					background="default"
					borderColor="neutral-subtle"
					borderWidth="1"
					borderRadius="8"
					padding="space-20"
				>
					<Heading level="2" size="medium" id="points-activity-heading">Points by activity</Heading>
					<VStack gap="space-12">
						{CATEGORIES.map((category) => {
							const value = points.get(category.key) ?? 0;
							const width = maxPoints > 0 ? Math.max(0, value / maxPoints * 100) : 0;
							return (
								<div className="dashboardView__activityRow" key={category.key}>
									<span className={`dashboardView__activityLabel dashboardView__activityLabel--${category.color}`}>
										<span aria-hidden="true" />
										{CREDIT_TYPE_LABELS[category.key] ?? category.label}
									</span>
									<span className="dashboardView__activityTrack" aria-hidden="true">
										<span className={`dashboardView__activityBar dashboardView__activityBar--${category.color}`} style={{ width: `${width}%` }} />
									</span>
									<strong className={value === 0 ? "hubRedesign__dim" : ""}>{value}</strong>
								</div>
							);
						})}
					</VStack>
				</Box>

				<Box
					as="section"
					aria-labelledby="attention-heading"
					className="hubRedesign__card dashboardView__card"
					background="default"
					borderColor="neutral-subtle"
					borderWidth="1"
					borderRadius="8"
					padding="space-20"
				>
					<Heading level="2" size="medium" id="attention-heading">Needs attention</Heading>
					{attention.length === 0 ? (
						<BodyShort>All clear — nothing needs attention.</BodyShort>
					) : (
						<VStack gap="space-8">
							{attention.map((item) => (
								<AttentionCard item={item} key={`${item.title}-${item.count}`} />
							))}
						</VStack>
					)}
					<BodyShort className="dashboardView__checks">
						{overview.delta.unmatchedRegistrations === 0 && "No unmatched registrations"}
						{overview.delta.unmatchedRegistrations === 0 && overview.delta.failedEvents === 0 && " or "}
						{overview.delta.failedEvents === 0 && "no failed events"}
						{overview.delta.unmatchedRegistrations === 0 || overview.delta.failedEvents === 0 ? "." : ""}
					</BodyShort>
				</Box>
			</div>

			<Box
				as="section"
				aria-labelledby="weekly-points-heading"
				className="hubRedesign__card dashboardView__card"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="8"
				padding="space-20"
			>
				<div className="hubRedesign__cardHeader">
					<div>
						<Heading level="2" size="medium" id="weekly-points-heading">Weekly points</Heading>
						<BodyShort className="hubRedesign__muted">
							Newest first · weeks starting {firstWeek ? weekDate(firstWeek) : "—"} – {lastWeek ? weekDate(lastWeek) : "—"}
						</BodyShort>
					</div>
				</div>
				<Checkbox
					checked={hideEmpty}
					onChange={(event) => setHideEmpty(event.target.checked)}
					value="hide-empty"
				>
					Hide weeks with no points ({emptyWeekCount} hidden)
				</Checkbox>
				<div className="hubRedesign__scrollTable">
					<Table size="small" className="dashboardView__weeklyTable">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Week of</Table.HeaderCell>
								{CATEGORIES.map((category) => (
									<Table.HeaderCell key={category.key} scope="col">{category.label}</Table.HeaderCell>
								))}
								<Table.HeaderCell className="dashboardView__total" scope="col">Total</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{shownWeeks.map((week) => {
								const total = weekTotal(week);
								return (
									<Table.Row key={week.weekStarting}>
										<Table.HeaderCell scope="row" className="hubRedesign__mono">{weekDate(week.weekStarting)}</Table.HeaderCell>
										{CATEGORIES.map((category) => {
											const value = week.pointsByCreditType[category.key] ?? 0;
											return (
												<Table.DataCell
													key={category.key}
													className={value === 0 ? "hubRedesign__dim" : ""}
												>
													{value}
												</Table.DataCell>
											);
										})}
										<Table.DataCell className="dashboardView__total">{total}</Table.DataCell>
									</Table.Row>
								);
							})}
						</Table.Body>
					</Table>
				</div>
			</Box>

			<section className="dashboardView__integrations" aria-labelledby="integration-health-heading">
				<Heading level="2" size="large" id="integration-health-heading">Integration health</Heading>
				<HGrid columns={{ xs: 1, md: 2, xl: 3 }} gap="space-12">
					<IntegrationCard
						name="Slack"
						integration="slack"
						status={overview.slack}
						details={[
							["Messages scanned", overview.slack.messagesScanned],
							["Credits awarded", overview.slack.creditsAwarded],
							["Duplicate credits", overview.slack.duplicateCredits],
							["Unmapped authors", overview.slack.unmappedAuthors],
						]}
						onTriggerSync={onTriggerSync}
						triggeringSync={triggeringSync}
						triggerError={triggerError}
					/>
					<IntegrationCard
						name="Delta"
						integration="delta"
						status={overview.delta}
						details={[
							["Events scanned", overview.delta.eventsScanned],
							["Credits awarded", overview.delta.creditsAwarded],
							["Duplicate credits", overview.delta.duplicateCredits],
							["Unmatched registrations", overview.delta.unmatchedRegistrations],
							["Failed events", overview.delta.failedEvents],
						]}
						onTriggerSync={onTriggerSync}
						triggeringSync={triggeringSync}
						triggerError={triggerError}
					/>
					<IntegrationCard
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
				</HGrid>
			</section>
		</div>
	);
}

function StatTile({
	label,
	value,
	detail,
	variant = "default",
}: {
	label: string;
	value: string;
	detail?: string;
	variant?: "default" | "success" | "warning" | "danger";
}) {
	return (
		<Box
			className={`dashboardView__stat dashboardView__stat--${variant}`}
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="8"
			padding="space-16"
		>
			<BodyShort className="hubRedesign__statLabel">{label}</BodyShort>
			<Heading level="2" size="large" className="hubRedesign__statValue">
				{value}{detail && <span className="dashboardView__statDetail"> {detail}</span>}
			</Heading>
		</Box>
	);
}

function AttentionCard({ item }: { item: AttentionItem }) {
	return (
		<div className={`hubRedesign__attention${item.variant === "neutral" ? "" : ` hubRedesign__attention--${item.variant}`}`}>
			<strong className={`dashboardView__attentionCount dashboardView__attentionCount--${item.variant}`}>{item.count}</strong>
			<div className="dashboardView__attentionContent">
				<BodyShort><strong>{item.title}</strong></BodyShort>
				<BodyShort className="hubRedesign__muted">{item.description}</BodyShort>
			</div>
			<Link href={item.href} className="hubRedesign__buttonLink">{item.linkLabel}</Link>
		</div>
	);
}

function IntegrationCard({
	name,
	integration,
	status,
	details,
	onTriggerSync,
	triggeringSync,
	triggerError,
}: {
	name: string;
	integration: ScoringIntegration;
	status: AdminDashboardIntegrationStatus;
	details: [string, number][];
	onTriggerSync: (integration: ScoringIntegration) => void;
	triggeringSync: ScoringIntegration | null;
	triggerError: { integration: ScoringIntegration; message: string } | null;
}) {
	const tone = integrationTone(status);
	const isBusy = triggeringSync === integration || status.outcome === "RUNNING";
	return (
		<Box
			as="article"
			className="hubRedesign__card dashboardView__integration"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="8"
			padding="space-16"
		>
			<div className="hubRedesign__cardHeader">
				<Heading level="3" size="small">{name}</Heading>
				<Tag size="xsmall" variant="moderate" data-color={tone}>
					<span className={`dashboardView__statusDot dashboardView__statusDot--${tone}`} aria-hidden="true" />
					{integrationLabel(status)}
				</Tag>
			</div>
			<dl className="dashboardView__integrationDates">
				<dt>Last success</dt><dd className="hubRedesign__mono">{dateTime(status.lastSuccessAt)}</dd>
				<dt>Last attempt</dt><dd className="hubRedesign__mono">{dateTime(status.lastAttemptAt)}</dd>
			</dl>
			<div className="dashboardView__integrationMetrics">
				{details.map(([label, value]) => (
					<div key={label} className={label.toLowerCase().includes("unmapped") && value > 0 ? "dashboardView__integrationMetric--warning" : ""}>
						<BodyShort>{label}</BodyShort>
						<strong>{value}</strong>
					</div>
				))}
			</div>
			{status.failureSummary && <BodyShort role="alert">{status.failureSummary}</BodyShort>}
			{triggerError?.integration === integration && (
				<BodyShort role="alert">{triggerError.message}</BodyShort>
			)}
			<Button
				className="dashboardView__syncButton"
				variant="secondary"
				disabled={!status.enabled || isBusy || triggeringSync !== null}
				loading={triggeringSync === integration}
				onClick={() => onTriggerSync(integration)}
			>
				Run {name} sync
			</Button>
		</Box>
	);
}

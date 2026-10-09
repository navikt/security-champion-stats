"use client";

import {
	BodyShort,
	Box,
	Button,
	CopyButton,
	Dialog,
	Heading,
	HStack,
	Select,
	Skeleton,
	ToggleGroup,
	VStack,
} from "@navikt/ds-react";
import {
	type RefObject,
	useCallback,
	useEffect,
	useMemo,
	useRef,
	useState,
} from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminScoreHistoryEntry,
	ParticipantScoreHistoryEntry,
	ScoreBreakdown,
	ScoreHistoryKind,
	ScoreHistorySeason,
	ScoreSummary,
} from "@/app/utils/Variables";

type HistoryFilter = "all" | "credit" | "adjustment" | "membership";
type HistoryVariant = "participant" | "admin";
type RawEntry = ParticipantScoreHistoryEntry | AdminScoreHistoryEntry;

type HistoryEntry = {
	id: string;
	kind: ScoreHistoryKind;
	recordedAt: string;
	activityAt: string | null;
	creditType: ParticipantScoreHistoryEntry["creditType"];
	points: number | null;
	displayName: string | null;
	action: ParticipantScoreHistoryEntry["action"];
	sourceRef: string | null;
	creditId: string | null;
	seasonId: string | null;
	reason: string | null;
	adminName: string | null;
	revokesCreditId: string | null;
	ruleChange: boolean;
};

type EntryGroup = { key: string; label: string; entries: HistoryEntry[] };

const PAGE_SIZE = 25;
const ACTIVITY_FILTERS: { value: HistoryFilter; label: string }[] = [
	{ value: "all", label: "All" },
	{ value: "credit", label: "Credits" },
	{ value: "adjustment", label: "Adjustments" },
	{ value: "membership", label: "Membership" },
];
const CREDIT_LABELS: Record<
	NonNullable<ParticipantScoreHistoryEntry["creditType"]>,
	string
> = {
	SLACK_WEEK: "Slack participation",
	DELTA_REGISTRATION: "Delta registration",
	GITHUB_COMMIT: "GitHub commit",
	GITHUB_PULL_REQUEST: "GitHub pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security event",
};
const SCORE_ROWS: {
	key: keyof ScoreBreakdown;
	label: string;
	className: string;
	signed: boolean;
}[] = [
	{
		key: "slack",
		label: "Slack participation",
		className: "slack",
		signed: false,
	},
	{
		key: "deltaRegistration",
		label: "Delta registration",
		className: "delta",
		signed: false,
	},
	{
		key: "githubCommit",
		label: "GitHub commit",
		className: "commit",
		signed: false,
	},
	{
		key: "githubPullRequest",
		label: "GitHub pull request",
		className: "pullRequest",
		signed: false,
	},
	{
		key: "securityEvent",
		label: "Security event",
		className: "securityEvent",
		signed: false,
	},
	{
		key: "adjustments",
		label: "Adjustments",
		className: "adjustment",
		signed: true,
	},
	{
		key: "ruleChanges",
		label: "Rule changes",
		className: "ruleChange",
		signed: true,
	},
];
const osloDayFormatter = new Intl.DateTimeFormat("en-CA", {
	timeZone: "Europe/Oslo",
	year: "numeric",
	month: "2-digit",
	day: "2-digit",
});
const osloDateFormatter = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	day: "numeric",
	month: "short",
	year: "numeric",
});
const osloShortDateFormatter = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	day: "numeric",
	month: "short",
});
const osloTimeFormatter = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	hour: "2-digit",
	minute: "2-digit",
});
const osloDateTimeFormatter = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	day: "numeric",
	month: "short",
	year: "numeric",
	hour: "2-digit",
	minute: "2-digit",
});

function readSearchParam(name: string): string | null {
	if (typeof window === "undefined") return null;
	return new URLSearchParams(window.location.search).get(name);
}

function readHistoryFilter(variant: HistoryVariant): HistoryFilter {
	const value = readSearchParam("type");
	return ACTIVITY_FILTERS.some(
		(filter) =>
			filter.value === value &&
			(variant === "participant" || filter.value !== "membership"),
	)
		? (value as HistoryFilter)
		: "all";
}

function updateHistoryUrl(key: "season" | "type", value: string): void {
	const params = new URLSearchParams(window.location.search);
	if (
		(key === "season" && value === "") ||
		(key === "type" && value === "all")
	) {
		params.delete(key);
	} else {
		params.set(key, value);
	}
	const query = params.toString();
	window.history.replaceState(
		null,
		"",
		`${window.location.pathname}${query ? `?${query}` : ""}${window.location.hash}`,
	);
}

function normalizeEntries(
	variant: HistoryVariant,
	entries: RawEntry[],
	indexOffset = 0,
): HistoryEntry[] {
	return entries.map((entry, index) => {
		if (variant === "participant") {
			const participantEntry = entry as ParticipantScoreHistoryEntry;
			return {
				id: `${participantEntry.kind}:${participantEntry.occurredAt}:${indexOffset + index}`,
				kind: participantEntry.kind,
				recordedAt: participantEntry.occurredAt,
				activityAt:
					participantEntry.kind === "credit"
						? participantEntry.occurredAt
						: null,
				creditType: participantEntry.creditType,
				points: participantEntry.points,
				displayName: participantEntry.displayName,
				action: participantEntry.action,
				sourceRef: null,
				creditId: null,
				seasonId: null,
				reason: null,
				adminName: null,
				revokesCreditId: null,
				ruleChange: false,
			};
		}

		const adminEntry = entry as AdminScoreHistoryEntry;
		return {
			id: adminEntry.id,
			kind: adminEntry.kind,
			recordedAt: adminEntry.recordedAt,
			activityAt: adminEntry.activityAt,
			creditType: adminEntry.creditType,
			points: adminEntry.points,
			displayName: adminEntry.displayName,
			action: adminEntry.action,
			sourceRef: adminEntry.sourceRef,
			creditId: adminEntry.creditId,
			seasonId: adminEntry.seasonId,
			reason: adminEntry.reason,
			adminName: adminEntry.adminName,
			revokesCreditId: adminEntry.revokesCreditId,
			ruleChange: adminEntry.ruleChange,
		};
	});
}

function osloDayKey(value: string): string {
	return osloDayFormatter.format(new Date(value));
}

function offsetDay(value: string, amount: number): string {
	const [year, month, day] = value.split("-").map(Number);
	const shifted = new Date(Date.UTC(year, month - 1, day + amount));
	return [
		shifted.getUTCFullYear(),
		String(shifted.getUTCMonth() + 1).padStart(2, "0"),
		String(shifted.getUTCDate()).padStart(2, "0"),
	].join("-");
}

function dayLabel(value: string): string {
	const date = new Date(value);
	const key = osloDayKey(value);
	const today = osloDayKey(new Date().toISOString());
	if (key === today) return `Today · ${osloDateFormatter.format(date)}`;
	if (key === offsetDay(today, -1)) {
		return `Yesterday · ${osloDateFormatter.format(date)}`;
	}
	return osloDateFormatter.format(date);
}

function groupEntries(entries: HistoryEntry[]): EntryGroup[] {
	const groups: EntryGroup[] = [];
	for (const entry of entries) {
		const key = osloDayKey(entry.recordedAt);
		let group = groups.find((candidate) => candidate.key === key);
		if (!group) {
			group = { key, label: dayLabel(entry.recordedAt), entries: [] };
			groups.push(group);
		}
		group.entries.push(entry);
	}
	return groups;
}

function signedPoints(value: number, alwaysSigned = false): string {
	if (value < 0) return `−${Math.abs(value)}`;
	if (value > 0 || alwaysSigned) return `+${value}`;
	return "0";
}

function seasonLabel(season: ScoreHistorySeason): string {
	return `Season ${season.startsOn.slice(0, 4)}`;
}

function sourceLink(reference: string): string | null {
	const match =
		/^([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+):(commit):([a-f0-9]+)$/i.exec(
			reference,
		) ?? /^([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+):(pr):(\d+)$/i.exec(reference);
	if (!match) return null;
	const [, owner, repo, kind, identity] = match;
	const suffix = kind.toLowerCase() === "pr" ? "pull" : "commit";
	return `https://github.com/${owner}/${repo}/${suffix}/${identity}`;
}

function creditTitle(entry: HistoryEntry): string {
	if (entry.ruleChange) return "Scoring-rule change";
	if (entry.kind === "adjustment") return "Point adjustment";
	if (entry.kind === "membership") {
		if (entry.action === "joined") return "Joined the program";
		if (entry.action === "rejoined") return "Rejoined the program";
		return "Left the program";
	}
	return entry.creditType ? CREDIT_LABELS[entry.creditType] : "Activity credit";
}

function initials(name: string): string {
	return name
		.trim()
		.split(/\s+/)
		.slice(0, 2)
		.map((part) => part[0]?.toUpperCase() ?? "")
		.join("");
}

export type ScoreHistoryProps = {
	variant: HistoryVariant;
	participantId?: string;
	participantName?: string;
	participantEmail?: string;
	onAdjust?: () => void;
	onClose?: () => void;
	returnFocusTo?: RefObject<HTMLElement | null> | (() => HTMLElement | null);
};

export function ScoreHistory({
	variant,
	participantId,
	participantName,
	participantEmail,
	onAdjust,
	onClose,
	returnFocusTo,
}: ScoreHistoryProps) {
	const [season, setSeason] = useState(() => readSearchParam("season") ?? "");
	const [filter, setFilter] = useState<HistoryFilter>(() =>
		readHistoryFilter(variant),
	);
	const [summary, setSummary] = useState<ScoreSummary | null>(null);
	const [entries, setEntries] = useState<HistoryEntry[]>([]);
	const [nextCursor, setNextCursor] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);
	const [loadingMore, setLoadingMore] = useState(false);
	const [failed, setFailed] = useState(false);
	const [attempt, setAttempt] = useState(0);
	const loadedRequest = useRef("");
	const currentRequest = useRef(0);
	const closeButtonRef = useRef<HTMLButtonElement>(null);

	useEffect(() => {
		const syncFromUrl = () => {
			setSeason(readSearchParam("season") ?? "");
			setFilter(readHistoryFilter(variant));
			if (variant === "admin" && readSearchParam("type") === "membership") {
				updateHistoryUrl("type", "all");
			}
		};
		syncFromUrl();
		window.addEventListener("popstate", syncFromUrl);
		return () => window.removeEventListener("popstate", syncFromUrl);
	}, [variant]);

	useEffect(() => {
		const requestKey = `${variant}:${participantId ?? ""}:${season}:${filter}:${attempt}`;
		if (loadedRequest.current === requestKey) return;
		const requestId = ++currentRequest.current;
		let active = true;
		setLoading(true);
		setFailed(false);
		setSummary(null);
		setEntries([]);
		setNextCursor(null);
		void (async () => {
			try {
				const resultSummary = await Apies.getScoreHistorySummary(
					variant,
					participantId,
					season || undefined,
				);
				let resolvedSeason = season;
				if (!resolvedSeason) {
					resolvedSeason =
						resultSummary.seasons.find((candidate) => candidate.endsOn === null)
							?.id ?? "all";
				}
				const resultPage =
					variant === "participant"
						? await Apies.getScoreHistoryPage<ParticipantScoreHistoryEntry>(
								variant,
								undefined,
								{
									season: resolvedSeason,
									type: filter,
									limit: PAGE_SIZE,
								},
							)
						: await Apies.getScoreHistoryPage<AdminScoreHistoryEntry>(
								variant,
								participantId,
								{
									season: resolvedSeason,
									type: filter,
									limit: PAGE_SIZE,
								},
							);
				if (!active || requestId !== currentRequest.current) return;
				setSummary(resultSummary);
				setEntries(normalizeEntries(variant, resultPage.entries));
				setNextCursor(resultPage.nextCursor);
				if (!season) {
					setSeason(resolvedSeason);
					updateHistoryUrl("season", resolvedSeason);
				}
				loadedRequest.current = `${variant}:${participantId ?? ""}:${resolvedSeason}:${filter}:${attempt}`;
			} catch (error) {
				console.error("Failed to load score history:", error);
				if (active && requestId === currentRequest.current) setFailed(true);
			} finally {
				if (active && requestId === currentRequest.current) setLoading(false);
			}
		})();
		return () => {
			active = false;
		};
	}, [attempt, filter, participantId, season, variant]);

	const groups = useMemo(() => groupEntries(entries), [entries]);
	const seasons = useMemo(() => {
		if (!summary) return [];
		return [...summary.seasons].sort((a, b) =>
			b.startsOn.localeCompare(a.startsOn),
		);
	}, [summary]);

	const changeSeason = useCallback((value: string) => {
		setSeason(value);
		updateHistoryUrl("season", value);
	}, []);

	const changeFilter = useCallback(
		(value: string) => {
			if (
				!ACTIVITY_FILTERS.some((candidate) => candidate.value === value) ||
				(variant === "admin" && value === "membership")
			)
				return;
			setFilter(value as HistoryFilter);
			updateHistoryUrl("type", value);
		},
		[variant],
	);

	const loadOlder = async () => {
		if (!nextCursor || loadingMore) return;
		setLoadingMore(true);
		try {
			const result =
				variant === "participant"
					? await Apies.getScoreHistoryPage<ParticipantScoreHistoryEntry>(
							variant,
							undefined,
							{
								season: season || "all",
								type: filter,
								cursor: nextCursor,
								limit: PAGE_SIZE,
							},
						)
					: await Apies.getScoreHistoryPage<AdminScoreHistoryEntry>(
							variant,
							participantId,
							{
								season: season || "all",
								type: filter,
								cursor: nextCursor,
								limit: PAGE_SIZE,
							},
						);
			setEntries((current) => [
				...current,
				...normalizeEntries(variant, result.entries, current.length),
			]);
			setNextCursor(result.nextCursor);
		} catch (error) {
			console.error("Failed to load older score history:", error);
			setFailed(true);
		} finally {
			setLoadingMore(false);
		}
	};

	const content = (
		<HistoryBody
			variant={variant}
			summary={summary}
			entries={entries}
			groups={groups}
			seasons={seasons}
			season={season}
			filter={filter}
			loading={loading}
			loadingMore={loadingMore}
			failed={failed}
			nextCursor={nextCursor}
			onSeasonChange={changeSeason}
			onFilterChange={changeFilter}
			onLoadOlder={loadOlder}
			onRetry={() => setAttempt((current) => current + 1)}
		/>
	);

	if (variant === "participant") {
		return (
			<main className="hubRedesign scoreHistory scoreHistory--participant">
				<header className="hubRedesign__header scoreHistory__pageHeader">
					<div>
						<Heading level="1" size="xlarge">
							My history
						</Heading>
						<BodyShort>
							Where your points come from. History starts when audit logging was
							introduced, so older activity may be missing.
						</BodyShort>
					</div>
				</header>
				{content}
			</main>
		);
	}

	const name = participantName || participantEmail || "Participant";
	return (
		<Dialog open onOpenChange={(open) => !open && onClose?.()}>
			<Dialog.Popup
				position="right"
				width="min(35rem, 100vw)"
				className="scoreHistory__drawer"
				initialFocusTo={closeButtonRef}
				returnFocusTo={returnFocusTo}
			>
				<Dialog.Header
					className="scoreHistory__drawerHeader"
					withClosebutton={false}
				>
					<div className="scoreHistory__identity">
						<div className="scoreHistory__avatar" aria-hidden="true">
							{initials(name)}
						</div>
						<div className="scoreHistory__identityText">
							<BodyShort className="scoreHistory__eyebrow">
								Score history
							</BodyShort>
							<Dialog.Title className="scoreHistory__drawerName">
								Score history for {name}
							</Dialog.Title>
							<BodyShort className="scoreHistory__muted">
								{participantEmail}
							</BodyShort>
						</div>
						<Dialog.CloseTrigger>
							<Button
								ref={closeButtonRef}
								type="button"
								variant="tertiary"
								data-color="neutral"
								className="scoreHistory__close"
								aria-label="Close score history"
							>
								×
							</Button>
						</Dialog.CloseTrigger>
					</div>
					{loading || !summary ? (
						<StatSkeleton />
					) : (
						<HeaderStats
							summary={summary}
							season={season}
							drawer
							onAdjust={onAdjust}
							onClose={onClose}
						/>
					)}
				</Dialog.Header>
				<Dialog.Body className="scoreHistory__drawerBody">
					{content}
				</Dialog.Body>
			</Dialog.Popup>
		</Dialog>
	);
}

function HistoryBody({
	variant,
	summary,
	entries,
	groups,
	seasons,
	season,
	filter,
	loading,
	loadingMore,
	failed,
	nextCursor,
	onSeasonChange,
	onFilterChange,
	onLoadOlder,
	onRetry,
}: {
	variant: HistoryVariant;
	summary: ScoreSummary | null;
	entries: HistoryEntry[];
	groups: EntryGroup[];
	seasons: ScoreHistorySeason[];
	season: string;
	filter: HistoryFilter;
	loading: boolean;
	loadingMore: boolean;
	failed: boolean;
	nextCursor: string | null;
	onSeasonChange: (value: string) => void;
	onFilterChange: (value: string) => void;
	onLoadOlder: () => void;
	onRetry: () => void;
}) {
	if (loading && !summary) {
		return <HistorySkeleton variant={variant} />;
	}
	if (failed) {
		return (
			<Box className="scoreHistory__message" role="alert">
				<BodyShort>We couldn't load score history. Try again.</BodyShort>
				<Button type="button" variant="tertiary" onClick={onRetry}>
					Retry
				</Button>
			</Box>
		);
	}
	if (!summary) return null;

	return (
		<VStack className="scoreHistory__sections" gap="space-20">
			{variant === "participant" ? (
				<SummaryCard
					summary={summary}
					season={season}
					seasons={seasons}
					onSeasonChange={onSeasonChange}
				/>
			) : (
				<section className="scoreHistory__breakdownSection">
					<div className="scoreHistory__sectionHeader">
						<Heading level="2" size="small">
							Breakdown
						</Heading>
						<SeasonSelect
							seasons={seasons}
							value={season}
							onChange={onSeasonChange}
						/>
					</div>
					<Breakdown summary={summary} variant={variant} />
					<BodyShort className="scoreHistory__footnote">
						Total = activity points + adjustments (incl. revocations) +
						scoring-rule changes. Revoked credits stay visible with their
						correcting adjustment.
					</BodyShort>
				</section>
			)}

			<section className="scoreHistory__activity">
				<div className="scoreHistory__sectionHeader scoreHistory__activityHeader">
					<div>
						<Heading level="2" size="medium">
							Activity
						</Heading>
						<BodyShort className="scoreHistory__muted">
							Newest first · Europe/Oslo
						</BodyShort>
					</div>
					<ToggleGroup
						label="Filter history entries"
						value={filter}
						onChange={onFilterChange}
						size="small"
						data-color="neutral"
						className="scoreHistory__filters"
					>
						{ACTIVITY_FILTERS.filter(
							(item) =>
								variant === "participant" || item.value !== "membership",
						).map(({ value, label }) => (
							<ToggleGroup.Item key={value} value={value} label={label} />
						))}
					</ToggleGroup>
				</div>
				{loading ? (
					<ActivitySkeleton />
				) : entries.length === 0 ? (
					<Box className="scoreHistory__empty">
						<BodyShort>
							{variant === "participant"
								? "Nothing here yet."
								: "No entries for this filter."}
						</BodyShort>
					</Box>
				) : (
					<VStack gap="space-16">
						{groups.map((group) => (
							<ActivityDayGroup
								key={group.key}
								group={group}
								variant={variant}
								seasons={seasons}
							/>
						))}
						{nextCursor && (
							<div className="scoreHistory__loadMore">
								<Button
									type="button"
									variant="secondary"
									data-color="neutral"
									onClick={onLoadOlder}
									loading={loadingMore}
								>
									Load older
								</Button>
							</div>
						)}
					</VStack>
				)}
			</section>
		</VStack>
	);
}

function SummaryCard({
	summary,
	season,
	seasons,
	onSeasonChange,
}: {
	summary: ScoreSummary;
	season: string;
	seasons: ScoreHistorySeason[];
	onSeasonChange: (value: string) => void;
}) {
	return (
		<Box
			className="scoreHistory__summary"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="12"
		>
			<div className="scoreHistory__statsPane">
				<HeaderStats summary={summary} season={season} />
			</div>
			<div className="scoreHistory__breakdownPane">
				<div className="scoreHistory__sectionHeader">
					<Heading level="2" size="small">
						Breakdown
					</Heading>
					<SeasonSelect
						seasons={seasons}
						value={season}
						onChange={onSeasonChange}
					/>
				</div>
				<Breakdown summary={summary} variant="participant" />
			</div>
		</Box>
	);
}

function HeaderStats({
	summary,
	season,
	drawer = false,
	onAdjust,
	onClose,
}: {
	summary: ScoreSummary;
	season: string;
	drawer?: boolean;
	onAdjust?: () => void;
	onClose?: () => void;
}) {
	return (
		<div
			className={`scoreHistory__stats${drawer ? " scoreHistory__stats--drawer" : ""}`}
		>
			<SummaryStat
				label={season === "all" ? "All-time points" : "Season points"}
				value={String(summary.points)}
			/>
			<SummaryStat label="Tier" value={summary.tier} />
			<SummaryStat
				label="Rank"
				value={summary.rank === null ? "—" : `#${summary.rank}`}
			/>
			{drawer && (
				<Button
					type="button"
					variant="secondary"
					data-color="neutral"
					onClick={() => {
						onClose?.();
						onAdjust?.();
					}}
				>
					Adjust points
				</Button>
			)}
		</div>
	);
}

function SummaryStat({ label, value }: { label: string; value: string }) {
	return (
		<div className="scoreHistory__stat">
			<BodyShort>{label}</BodyShort>
			<strong>{value}</strong>
		</div>
	);
}

function StatSkeleton() {
	return (
		<div className="scoreHistory__stats scoreHistory__stats--drawer">
			{[0, 1, 2].map((item) => (
				<Skeleton key={item} variant="text" width="4rem" height="2rem" />
			))}
			<Skeleton variant="rounded" width="8rem" height="2.5rem" />
		</div>
	);
}

function SeasonSelect({
	seasons,
	value,
	onChange,
}: {
	seasons: ScoreHistorySeason[];
	value: string;
	onChange: (value: string) => void;
}) {
	return (
		<Select
			label="Season"
			hideLabel
			size="small"
			className="scoreHistory__seasonSelect"
			value={value}
			onChange={(event) => onChange(event.target.value)}
		>
			{seasons.map((item) => (
				<option key={item.id} value={item.id}>
					{seasonLabel(item)}
					{item.endsOn === null ? " (current)" : ""}
				</option>
			))}
			<option value="all">All seasons</option>
		</Select>
	);
}

function Breakdown({
	summary,
	variant,
}: {
	summary: ScoreSummary;
	variant: HistoryVariant;
}) {
	const rows = SCORE_ROWS.filter(
		(row) => row.key !== "ruleChanges" || variant === "admin",
	).map((row) => ({
		...row,
		value: summary.breakdown[row.key] ?? 0,
	}));
	const positiveTotal = rows.reduce(
		(total, row) => total + Math.max(row.value, 0),
		0,
	);

	return (
		<div className="scoreHistory__breakdown">
			<div className="scoreHistory__bar" aria-hidden="true">
				{rows
					.filter((row) => row.value > 0 && positiveTotal > 0)
					.map((row) => (
						<div
							key={row.key}
							className={`scoreHistory__segment scoreHistory__category--${row.className}`}
							style={{ flexGrow: row.value }}
							title={`${row.label}: ${row.value}`}
						/>
					))}
			</div>
			<div className="scoreHistory__legend">
				{rows.map((row) => (
					<div className="scoreHistory__legendRow" key={row.key}>
						<span
							className={`scoreHistory__color scoreHistory__category--${row.className}`}
							aria-hidden="true"
						/>
						<BodyShort
							className={
								row.value === 0
									? "scoreHistory__muted scoreHistory__legendLabel"
									: "scoreHistory__legendLabel"
							}
						>
							{row.label}
						</BodyShort>
						<strong className={row.value === 0 ? "scoreHistory__dim" : ""}>
							{row.signed ? signedPoints(row.value, true) : String(row.value)}
						</strong>
					</div>
				))}
			</div>
		</div>
	);
}

function ActivityDayGroup({
	group,
	variant,
	seasons,
}: {
	group: EntryGroup;
	variant: HistoryVariant;
	seasons: ScoreHistorySeason[];
}) {
	return (
		<section className="scoreHistory__day" aria-label={group.label}>
			<BodyShort className="scoreHistory__dayLabel">{group.label}</BodyShort>
			<Box
				as="ul"
				className="scoreHistory__entries"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="12"
			>
				{group.entries.map((entry) => (
					<ActivityRow
						key={entry.id}
						entry={entry}
						variant={variant}
						seasons={seasons}
					/>
				))}
			</Box>
		</section>
	);
}

function ActivityRow({
	entry,
	variant,
	seasons,
}: {
	entry: HistoryEntry;
	variant: HistoryVariant;
	seasons: ScoreHistorySeason[];
}) {
	const [expanded, setExpanded] = useState(false);
	const detailsId = `score-history-details-${entry.id.replaceAll(/[^a-zA-Z0-9_-]/g, "-")}`;
	const meta = entryMeta(entry, variant);
	const points = entry.points;
	const displayPoints =
		points === null
			? null
			: entry.kind === "credit"
				? `+${points}`
				: signedPoints(points, true);

	return (
		<li
			className={`scoreHistory__entry${expanded ? " scoreHistory__entry--expanded" : ""}`}
		>
			{variant === "admin" ? (
				<>
					<button
						type="button"
						className="scoreHistory__entryButton"
						aria-expanded={expanded}
						aria-controls={detailsId}
						onClick={() => setExpanded((value) => !value)}
					>
						<EntryIcon entry={entry} />
						<span className="scoreHistory__entryText">
							<strong>{creditTitle(entry)}</strong>
							<span className="scoreHistory__meta">{meta}</span>
						</span>
						{displayPoints !== null && (
							<strong className={pointsColor(entry)}>{displayPoints}</strong>
						)}
						<span className="scoreHistory__chevron" aria-hidden="true">
							{expanded ? "▾" : "▸"}
						</span>
					</button>
					{expanded && (
						<EntryDetails entry={entry} seasons={seasons} id={detailsId} />
					)}
				</>
			) : (
				<div className="scoreHistory__entryStatic">
					<EntryIcon entry={entry} />
					<span className="scoreHistory__entryText">
						<strong>{creditTitle(entry)}</strong>
						<span className="scoreHistory__meta">{meta}</span>
					</span>
					{displayPoints !== null && (
						<strong className={pointsColor(entry)}>{displayPoints}</strong>
					)}
				</div>
			)}
		</li>
	);
}

function entryMeta(entry: HistoryEntry, variant: HistoryVariant): string {
	if (entry.kind === "credit") {
		const occurred = entry.activityAt ?? entry.recordedAt;
		const time = `Activity ${osloShortDateFormatter.format(new Date(occurred))}, ${osloTimeFormatter.format(new Date(occurred))}`;
		if (variant === "participant") {
			return entry.displayName ? `${time} · ${entry.displayName}` : time;
		}
		return `Activity ${osloShortDateFormatter.format(new Date(occurred))}, ${osloTimeFormatter.format(new Date(occurred))} · recorded ${osloTimeFormatter.format(new Date(entry.recordedAt))}`;
	}
	if (entry.kind === "membership") {
		if (entry.action === "joined") return "You became an active participant";
		if (entry.action === "rejoined") return "You rejoined the program";
		return "You left the program";
	}
	if (variant === "participant") return "Adjusted by a program administrator";
	return [entry.reason, entry.adminName ? `by ${entry.adminName}` : null]
		.filter(Boolean)
		.join(" · ");
}

function pointsColor(entry: HistoryEntry): string {
	if (entry.kind === "credit")
		return "scoreHistory__points scoreHistory__points--credit";
	if ((entry.points ?? 0) < 0)
		return "scoreHistory__points scoreHistory__points--negative";
	if (entry.kind === "adjustment")
		return "scoreHistory__points scoreHistory__points--adjustment";
	return "scoreHistory__points";
}

function EntryIcon({ entry }: { entry: HistoryEntry }) {
	const glyph =
		entry.kind === "credit" ? "+" : entry.kind === "adjustment" ? "±" : "★";
	const category =
		entry.kind === "credit"
			? entry.creditType === "SLACK_WEEK"
				? "slack"
				: entry.creditType === "DELTA_REGISTRATION"
					? "delta"
					: entry.creditType === "GITHUB_COMMIT"
						? "commit"
						: entry.creditType === "GITHUB_PULL_REQUEST"
							? "pullRequest"
							: "securityEvent"
			: entry.kind === "adjustment"
				? "adjustment"
				: "membership";
	return (
		<span
			className={`scoreHistory__entryIcon scoreHistory__entryIcon--${category}`}
			aria-hidden="true"
		>
			{glyph}
		</span>
	);
}

function EntryDetails({
	entry,
	seasons,
	id,
}: {
	entry: HistoryEntry;
	seasons: ScoreHistorySeason[];
	id: string;
}) {
	const season = seasons.find((candidate) => candidate.id === entry.seasonId);
	const seasonValue = season
		? `${season.startsOn.slice(0, 4)} · ${osloDateFormatter.format(new Date(`${season.startsOn}T12:00:00Z`))} – ${season.endsOn ? osloDateFormatter.format(new Date(`${season.endsOn}T12:00:00Z`)) : "ongoing"}`
		: "—";
	const fields: { label: string; value: React.ReactNode }[] = [];

	if (entry.kind === "credit") {
		fields.push(
			{
				label: "Activity date",
				value: entry.activityAt
					? osloDateTimeFormatter.format(new Date(entry.activityAt))
					: "—",
			},
			{
				label: "Recorded",
				value: osloDateTimeFormatter.format(new Date(entry.recordedAt)),
			},
			{ label: "Season", value: seasonValue },
		);
		if (entry.sourceRef) {
			fields.push({
				label: "Source",
				value: <CodeChip label="source reference" value={entry.sourceRef} />,
			});
		}
		if (entry.creditId) {
			fields.push({
				label: "Credit ID",
				value: <CodeChip label="credit ID" value={entry.creditId} />,
			});
		}
	} else if (entry.kind === "adjustment") {
		fields.push(
			{ label: "Reason", value: entry.reason || "—" },
			{ label: "By", value: entry.adminName || "—" },
			{
				label: "Recorded",
				value: osloDateTimeFormatter.format(new Date(entry.recordedAt)),
			},
			{ label: "Season", value: seasonValue },
		);
		if (entry.revokesCreditId) {
			fields.push({
				label: "Revokes",
				value: (
					<CodeChip label="revoked credit ID" value={entry.revokesCreditId} />
				),
			});
		}
	}

	return (
		<div className="scoreHistory__entryDetails" id={id}>
			{fields.map((field) => (
				<div className="scoreHistory__detailRow" key={field.label}>
					<span>{field.label}</span>
					<span>{field.value}</span>
				</div>
			))}
		</div>
	);
}

function CodeChip({ label, value }: { label: string; value: string }) {
	const link = sourceLink(value);
	return (
		<span className="scoreHistory__codeChip">
			<code title={value}>{value}</code>
			{link && (
				<a href={link} target="_blank" rel="noreferrer">
					View
				</a>
			)}
			<CopyButton
				copyText={value}
				text={`Copy ${label}`}
				activeText={`Copied ${label}`}
				activeDuration={1400}
				size="xsmall"
			/>
		</span>
	);
}

function HistorySkeleton({ variant }: { variant: HistoryVariant }) {
	return (
		<VStack
			className="scoreHistory__sections"
			gap="space-20"
			aria-hidden="true"
		>
			{variant === "participant" && (
				<Box className="scoreHistory__summarySkeleton">
					<div>
						{[0, 1, 2].map((item) => (
							<Skeleton key={item} variant="text" width="5rem" height="2rem" />
						))}
					</div>
					<Skeleton variant="rounded" width="100%" height="12rem" />
				</Box>
			)}
			<ActivitySkeleton />
		</VStack>
	);
}

function ActivitySkeleton() {
	return (
		<VStack gap="space-16" aria-hidden="true">
			{[0, 1].map((group) => (
				<VStack gap="space-8" key={group}>
					<Skeleton variant="text" width="8rem" />
					<Box className="scoreHistory__skeletonGroup">
						{[0, 1, 2].map((row) => (
							<HStack
								align="center"
								gap="space-12"
								className="scoreHistory__skeletonRow"
								key={row}
							>
								<Skeleton variant="rounded" width="1.75rem" height="1.75rem" />
								<VStack gap="space-4">
									<Skeleton variant="text" width="12rem" />
									<Skeleton variant="text" width="9rem" />
								</VStack>
							</HStack>
						))}
					</Box>
				</VStack>
			))}
		</VStack>
	);
}

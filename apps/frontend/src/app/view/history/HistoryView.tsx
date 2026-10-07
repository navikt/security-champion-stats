"use client";

import { useEffect, useMemo, useState } from "react";
import {
	BodyShort,
	Box,
	Button,
	Heading,
	HStack,
	Tag,
	ToggleGroup,
	VStack,
} from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import type {
	HistoryEntry,
	HistoryPage,
	ParticipantSeasonScore,
	ProgramParticipant,
} from "@/app/utils/Variables";

type HistoryFilter = "all" | "credits" | "adjustments" | "membership";

const HISTORY_FILTERS: { value: HistoryFilter; label: string }[] = [
	{ value: "all", label: "All" },
	{ value: "credits", label: "Credits" },
	{ value: "adjustments", label: "Adjustments" },
	{ value: "membership", label: "Membership" },
];

const CREDIT_LABELS: Record<string, string> = {
	SLACK_WEEK: "Slack participation",
	DELTA_REGISTRATION: "Event registration",
	GITHUB_COMMIT: "GitHub commit",
	GITHUB_PULL_REQUEST: "GitHub pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security event contribution",
};

function classify(entry: HistoryEntry): Exclude<HistoryFilter, "all"> {
	if (entry.action.includes("CREDIT")) return "credits";
	if (entry.action.includes("ADJUST")) return "adjustments";
	return "membership";
}

function readable(value: string): string {
	const text = value
		.replaceAll("_", " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase();
	return text.charAt(0).toUpperCase() + text.slice(1);
}

function entryTitle(entry: HistoryEntry): string {
	if (entry.action === "CREDIT_AWARDED") return "Credit awarded";
	if (entry.action === "POINTS_ADJUSTED") return "Points adjusted";
	if (entry.action === "PARTICIPANT_ENROLLED") return "Joined program";
	if (entry.action === "PARTICIPANT_LEFT") return "Left program";
	if (entry.action === "PARTICIPANT_REJOINED") return "Rejoined program";
	return readable(entry.action);
}

function entryDetail(entry: HistoryEntry): string {
	const creditType = entry.details.creditType;
	const reason = entry.details.reason;
	const eventName = entry.details.eventName;
	const values = [
		typeof creditType === "string"
			? CREDIT_LABELS[creditType] ?? readable(creditType)
			: null,
		typeof eventName === "string" ? eventName : null,
		typeof reason === "string" ? reason : null,
	].filter((value): value is string => Boolean(value));
	return values.join(" · ");
}

function entryPoints(entry: HistoryEntry): number | null {
	const value = entry.details.points;
	return typeof value === "number" ? value : null;
}

function signedPoints(value: number): string {
	return `${value > 0 ? "+" : value < 0 ? "−" : ""}${Math.abs(value)}`;
}

function localDayKey(value: string): string {
	const date = new Date(value);
	return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

function dayLabel(value: string): string {
	const date = new Date(value);
	const today = new Date();
	const yesterday = new Date(today);
	yesterday.setDate(today.getDate() - 1);
	const format = (day: Date) =>
		day.toLocaleDateString(undefined, {
			day: "numeric",
			month: "short",
			year: "numeric",
		});

	if (localDayKey(date.toISOString()) === localDayKey(today.toISOString())) {
		return `Today · ${format(date)}`;
	}
	if (localDayKey(date.toISOString()) === localDayKey(yesterday.toISOString())) {
		return `Yesterday · ${format(date)}`;
	}
	return format(date);
}

function sourceLink(reference: string): string | null {
	const match = /^([^:]+):commit:([a-f0-9]+)$/i.exec(reference);
	if (!match) return null;
	return `https://github.com/${match[1]}/commit/${match[2]}`;
}

function dateTime(value: string): string {
	return new Date(value).toLocaleTimeString(undefined, {
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
	});
}

function getFilterFromUrl(): HistoryFilter {
	const value = new URLSearchParams(window.location.search).get("type");
	return HISTORY_FILTERS.find((filter) => filter.value === value)?.value ?? "all";
}

function updateFilterInUrl(filter: HistoryFilter) {
	const params = new URLSearchParams(window.location.search);
	if (filter === "all") params.delete("type");
	else params.set("type", filter);
	const query = params.toString();
	window.history.replaceState(
		null,
		"",
		`${window.location.pathname}${query ? `?${query}` : ""}${window.location.hash}`,
	);
}

function isInSeason(entry: HistoryEntry, score: ParticipantSeasonScore | null) {
	if (!score) return false;
	return new Date(entry.recordedAt).getTime() >=
		new Date(score.season.startsOn).getTime();
}

export function HistoryView() {
	const [page, setPage] = useState<HistoryPage | null>(null);
	const [score, setScore] = useState<ParticipantSeasonScore | null>(null);
	const [participant, setParticipant] = useState<ProgramParticipant | null>(null);
	const [filter, setFilter] = useState<HistoryFilter>("all");
	const [visibleCount, setVisibleCount] = useState(50);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [copied, setCopied] = useState<string | null>(null);
	const [copyFailed, setCopyFailed] = useState(false);

	useEffect(() => {
		setFilter(getFilterFromUrl());
	}, []);

	useEffect(() => {
		let current = true;
		setLoading(true);
		setFailed(false);
		Promise.allSettled([
			Apies.getHistory(false, "", null),
			Apies.getParticipantSeasonScore(),
			Apies.fetchMembership(),
		])
			.then(([historyResult, scoreResult, membershipResult]) => {
				if (!current) return;
				if (historyResult.status === "rejected") {
					throw historyResult.reason;
				}
				setPage(historyResult.value);
				const seasonScore =
					scoreResult.status === "fulfilled" ? scoreResult.value : null;
				const membership =
					membershipResult.status === "fulfilled" ? membershipResult.value : null;
				if (scoreResult.status === "rejected")
					console.error("Failed to load participant season score:", scoreResult.reason);
				if (membershipResult.status === "rejected")
					console.error("Failed to load participant membership:", membershipResult.reason);
				setScore(seasonScore);
				setParticipant(membership);
			})
			.catch((error) => {
				console.error("Failed to load participant history:", error);
				if (current) setFailed(true);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, []);

	const filteredEntries = useMemo(
		() =>
			(page?.entries ?? []).filter(
				(entry) => filter === "all" || classify(entry) === filter,
			),
		[filter, page],
	);
	const visibleEntries = filteredEntries.slice(0, visibleCount);
	const groups = useMemo(() => {
		const result: { key: string; label: string; entries: HistoryEntry[] }[] = [];
		for (const entry of visibleEntries) {
			const key = localDayKey(entry.recordedAt);
			let group = result.find((candidate) => candidate.key === key);
			if (!group) {
				group = {
					key,
					label: dayLabel(entry.recordedAt),
					entries: [],
				};
				result.push(group);
			}
			group.entries.push(entry);
		}
		return result;
	}, [visibleEntries]);

	const pointsEarned = (page?.entries ?? [])
		.filter((entry) => classify(entry) !== "membership" && isInSeason(entry, score))
		.reduce((total, entry) => total + (entryPoints(entry) ?? 0), 0);

	const selectFilter = (value: string) => {
		if (!HISTORY_FILTERS.some((candidate) => candidate.value === value)) return;
		const nextFilter = value as HistoryFilter;
		setFilter(nextFilter);
		setVisibleCount(50);
		setCopyFailed(false);
		updateFilterInUrl(nextFilter);
	};

	const copyReference = async (entryId: string, reference: string) => {
		try {
			await navigator.clipboard.writeText(reference);
			setCopied(entryId);
			setCopyFailed(false);
			window.setTimeout(() => setCopied((current) => current === entryId ? null : current), 1400);
		} catch (error) {
			console.error("Failed to copy source reference:", error);
			setCopyFailed(true);
		}
	};

	return (
		<div className="hubRedesign historyView">
			<header className="hubRedesign__header">
				<Heading level="1" size="xlarge">My history</Heading>
				<BodyShort>
					Everything recorded about your participation. History starts when audit logging was introduced and capture is best-effort, so some changes may be missing.
				</BodyShort>
			</header>

			<Box
				className="hubRedesign__card historyView__summary"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="8"
				padding="space-20"
			>
				<div className="hubRedesign__stats">
					<SummaryStat
						label="Points earned"
						value={score ? `${pointsEarned >= 0 ? "+" : "−"}${Math.abs(pointsEarned)}` : "—"}
					/>
					<SummaryStat label="Entries" value={String(filteredEntries.length)} />
					<SummaryStat
						label="Member since"
						value={
							!participant
								? "—"
								: new Date(participant.joinedAt).toLocaleDateString(undefined, {
										day: "numeric",
										month: "short",
										year: "numeric",
									})
						}
					/>
				</div>
				<ToggleGroup
					label="Filter history entries"
					value={filter}
					onChange={selectFilter}
					data-color="neutral"
				>
					{HISTORY_FILTERS.map(({ value, label }) => (
						<ToggleGroup.Item key={value} value={value} label={label} />
					))}
				</ToggleGroup>
			</Box>

			{loading ? (
				<BodyShort role="status">Loading history…</BodyShort>
			) : failed ? (
				<BodyShort role="alert">We couldn't fetch history. Try again later.</BodyShort>
			) : filteredEntries.length === 0 ? (
				<BodyShort>No {filter === "all" ? "history" : filter} entries yet.</BodyShort>
			) : (
				<VStack gap="space-24">
					{groups.map((group) => (
						<section key={group.key} className="historyView__day">
							<BodyShort className="hubRedesign__eyebrow">{group.label}</BodyShort>
							<Box
								as="ol"
								className="hubRedesign__card historyView__entries"
								background="default"
								borderColor="neutral-subtle"
								borderWidth="1"
								borderRadius="8"
							>
								{group.entries.map((entry) => {
									const category = classify(entry);
									const points = entryPoints(entry);
									const reference = entry.details.sourceReference;
									const source = typeof reference === "string" ? reference : null;
									const link = source ? sourceLink(source) : null;
									return (
										<li className="historyView__entry" key={entry.id}>
											<div
												className={`historyView__kind historyView__kind--${category}`}
												aria-hidden="true"
											>
												{category === "credits" ? "+" : category === "adjustments" ? "±" : "★"}
											</div>
											<div className="historyView__entryContent">
												<div className="historyView__entryHeading">
													<Heading level="2" size="small">{entryTitle(entry)}</Heading>
													<BodyShort className="hubRedesign__muted">
														<time dateTime={entry.recordedAt}>{dateTime(entry.recordedAt)}</time>
														{" · "}
														<span className={entry.outcome === "SUCCEEDED" ? "historyView__success" : "historyView__failure"}>
															{entry.outcome === "SUCCEEDED" ? "Succeeded" : readable(entry.outcome)}
														</span>
													</BodyShort>
												</div>
												{entryDetail(entry) && (
													<BodyShort className="hubRedesign__muted">{entryDetail(entry)}</BodyShort>
												)}
												{source && (
													<HStack className="historyView__source" gap="space-8" align="center">
														<Tag size="xsmall" variant="outline" data-color="neutral">Source</Tag>
														<code className="hubRedesign__mono historyView__code" title={source}>{source}</code>
														{link && (
															<a className="hubRedesign__buttonLink" href={link} target="_blank" rel="noreferrer">
																View commit
															</a>
														)}
														<Button
															size="xsmall"
															variant="tertiary"
															data-color="neutral"
															onClick={() => void copyReference(entry.id, source)}
														>
															{copied === entry.id ? "Copied" : "Copy"}
														</Button>
													</HStack>
												)}
											</div>
											{points !== null && (
												<BodyShort
													className={`historyView__points${points < 0 ? " historyView__points--negative" : ""}`}
												>
													{signedPoints(points)}
												</BodyShort>
											)}
										</li>
									);
								})}
							</Box>
						</section>
					))}
					{copyFailed && (
						<BodyShort role="alert">We couldn't copy that source reference.</BodyShort>
					)}
					<div className="historyView__live" aria-live="polite">
						{copied ? "Source reference copied." : ""}
					</div>
					{visibleCount < filteredEntries.length && (
						<div className="historyView__loadMore">
							<Button variant="secondary" onClick={() => setVisibleCount((count) => count + 50)}>
								Load older
							</Button>
						</div>
					)}
				</VStack>
			)}
		</div>
	);
}

function SummaryStat({ label, value }: { label: string; value: string }) {
	return (
		<div className="hubRedesign__stat">
			<BodyShort className="hubRedesign__statLabel">{label}</BodyShort>
			<Heading level="2" size="medium" className="hubRedesign__statValue">{value}</Heading>
		</div>
	);
}

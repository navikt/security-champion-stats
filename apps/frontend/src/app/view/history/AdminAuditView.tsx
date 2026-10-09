"use client";

import { useEffect, useState } from "react";
import {
	BodyShort,
	Box,
	Button,
	Heading,
	HStack,
	TextField,
	ToggleGroup,
} from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import type { AuditCategory, AuditResponse } from "@/app/utils/Variables";
import { CreditSource } from "./CreditSource";

const FILTERS: { value: AuditCategory; label: string }[] = [
	{ value: "all", label: "All" },
	{ value: "syncs", label: "Syncs" },
	{ value: "credits", label: "Credits" },
	{ value: "admin", label: "Admin actions" },
];

const WARNING_METRICS = new Set([
	"unmapped_authors",
	"unmappedAuthors",
	"failed_events",
	"failedEvents",
	"unmatched_registrations",
	"unmatchedRegistrations",
]);

function readable(value: string): string {
	const text = value
		.replaceAll("_", " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase();
	return text.charAt(0).toUpperCase() + text.slice(1);
}

function dateParts(value: string) {
	const date = new Date(value);
	return {
		time: date.toLocaleTimeString(undefined, {
			hour: "2-digit",
			minute: "2-digit",
			second: "2-digit",
		}),
		date: date.toLocaleDateString(undefined, {
			day: "numeric",
			month: "short",
			year: "numeric",
		}),
	};
}

function numericValue(value: string): number | null {
	const normalized = value.replaceAll(",", "");
	if (!/^-?\d+(\.\d+)?$/.test(normalized)) return null;
	const number = Number(normalized);
	return Number.isFinite(number) ? number : null;
}

function summary(details: Record<string, string>): string {
	const metrics = Object.entries(details)
		.filter(([, value]) => numericValue(value) !== null)
		.slice(0, 3)
		.map(([key, value]) => `${value} ${readable(key).toLowerCase()}`)
		.join(" · ");
	return [details.sourceName, metrics].filter(Boolean).join(" · ");
}

function initialQuery() {
	return (new URLSearchParams(window.location.search).get("q") ?? "").slice(
		0,
		100,
	);
}

function initialCategory(): AuditCategory {
	const value = new URLSearchParams(window.location.search).get("category");
	return FILTERS.find((filter) => filter.value === value)?.value ?? "all";
}

function isAuditCategory(value: string): value is AuditCategory {
	return FILTERS.some((filter) => filter.value === value);
}

function setAuditUrl(query: string, category: AuditCategory) {
	const params = new URLSearchParams(window.location.search);
	if (query) params.set("q", query);
	else params.delete("q");
	if (category === "all") params.delete("category");
	else params.set("category", category);
	const search = params.toString();
	window.history.replaceState(
		null,
		"",
		`${window.location.pathname}${search ? `?${search}` : ""}${window.location.hash}`,
	);
}

export function AdminAuditView() {
	const [query, setQuery] = useState("");
	const [search, setSearch] = useState("");
	const [category, setCategory] = useState<AuditCategory>("all");
	const [pageNumber, setPageNumber] = useState(0);
	const [result, setResult] = useState<AuditResponse | null>(null);
	const [openItems, setOpenItems] = useState<Set<string>>(new Set());
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		const initial = initialQuery();
		setQuery(initial);
		setSearch(initial);
		setCategory(initialCategory());
	}, []);

	useEffect(() => {
		const timer = window.setTimeout(() => {
			const normalized = query.trim();
			if (normalized !== search) {
				setPageNumber(0);
				setSearch(normalized);
			}
			setAuditUrl(normalized, category);
		}, 250);
		return () => window.clearTimeout(timer);
	}, [query]);

	useEffect(() => {
		let current = true;
		setLoading(true);
		setFailed(false);
		Apies.getAdminAudit(search, category, pageNumber)
			.then((response) => {
				if (!current) return;
				setResult(response);
				setOpenItems(
					response.items[0] ? new Set([response.items[0].id]) : new Set(),
				);
			})
			.catch((error) => {
				console.error("Failed to load admin audit events:", error);
				if (current) setFailed(true);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, [search, category, pageNumber]);

	const start = result && result.total > 0 ? result.page * result.size + 1 : 0;
	const end = result
		? Math.min((result.page + 1) * result.size, result.total)
		: 0;

	const chooseCategory = (next: AuditCategory) => {
		setCategory(next);
		setPageNumber(0);
		setAuditUrl(query.trim(), next);
	};

	const filterRun = (correlationId: string) => {
		setCategory("all");
		setQuery(correlationId);
		setPageNumber(0);
		setAuditUrl(correlationId, "all");
	};

	return (
		<div className="hubRedesign auditView">
			<header className="hubRedesign__header">
				<BodyShort size="small" className="hubRedesign__eyebrow">
					Admin only
				</BodyShort>
				<Heading level="1" size="xlarge">
					Audit trail
				</Heading>
				<BodyShort>
					History starts when audit logging was introduced; capture is
					best-effort. Operational records are retained for 12 months.
				</BodyShort>
			</header>

			<div className="auditView__toolbar">
				<div className="auditView__search">
					<TextField
						label="Search events, actors, run or participant IDs"
						hideLabel
						maxLength={100}
						value={query}
						onChange={(event) => setQuery(event.target.value)}
					/>
					{query && (
						<Button
							variant="tertiary"
							data-color="neutral"
							onClick={() => setQuery("")}
						>
							Clear
						</Button>
					)}
				</div>
				<ToggleGroup
					className="auditView__filters"
					label="Filter audit events"
					value={category}
					onChange={(value) => {
						if (isAuditCategory(value)) chooseCategory(value);
					}}
					data-color="neutral"
					size="small"
				>
					{FILTERS.map((filter) => (
						<ToggleGroup.Item
							key={filter.value}
							value={filter.value}
							label={filter.label}
						/>
					))}
				</ToggleGroup>
			</div>

			<Box
				as="section"
				aria-label="Audit events"
				className="hubRedesign__card auditView__results"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="8"
			>
				{loading ? (
					<BodyShort className="auditView__message" role="status">
						Loading audit events…
					</BodyShort>
				) : failed ? (
					<BodyShort className="auditView__message" role="alert">
						We couldn't fetch audit events. Try again later.
					</BodyShort>
				) : !result || result.items.length === 0 ? (
					<BodyShort className="auditView__message">
						No events match your search.
					</BodyShort>
				) : (
					<>
						<ol className="auditView__list">
							{result.items.map((item) => {
								const { time, date } = dateParts(item.createdAt);
								const metrics = Object.entries(item.details).filter(
									([key]) =>
										!["sourceName", "sourceUrl", "sourceOccurredAt"].includes(
											key,
										),
								);
								const hasWarning = metrics.some(
									([key, value]) =>
										WARNING_METRICS.has(key) && numericValue(value) !== 0,
								);
								const status =
									item.outcome === "FAILED"
										? "danger"
										: hasWarning || item.outcome === "PARTIAL"
											? "warning"
											: "success";
								const expanded = openItems.has(item.id);
								const panelId = `audit-panel-${item.id}`;
								return (
									<li className="auditView__event" key={item.id}>
										<button
											className="auditView__row"
											type="button"
											aria-expanded={expanded}
											aria-controls={panelId}
											onClick={() =>
												setOpenItems((current) => {
													const next = new Set(current);
													if (next.has(item.id)) next.delete(item.id);
													else next.add(item.id);
													return next;
												})
											}
										>
											<span className="auditView__date">
												<time
													className="hubRedesign__mono"
													dateTime={item.createdAt}
												>
													{time}
												</time>
												<span>{date}</span>
											</span>
											<span className="auditView__summary">
												<span
													className={`auditView__statusDot auditView__statusDot--${status}`}
													aria-hidden="true"
												/>
												<span className="auditView__title">
													{readable(item.action)}
												</span>
												<span className="hubRedesign__muted">
													{summary(item.details)}
												</span>
												<span className="auditView__subline">
													{item.actorNavNoEmail || "System"}
													{item.correlationId && (
														<code className="hubRedesign__mono">
															run {item.correlationId.slice(0, 8)}
														</code>
													)}
												</span>
											</span>
											<span className="auditView__chevron" aria-hidden="true">
												{expanded ? "▾" : "▸"}
											</span>
										</button>
										{expanded && (
											<div className="auditView__panel" id={panelId}>
												{(item.details.sourceName ||
													item.details.sourceUrl ||
													item.details.creditType) && (
													<BodyShort>
														<CreditSource
															displayName={item.details.sourceName}
															sourceUrl={item.details.sourceUrl}
															sourceOccurredAt={item.details.sourceOccurredAt}
															creditType={
																item.details.creditType ??
																(item.action.startsWith("EVENT_CLAIM_")
																	? "SECURITY_EVENT_CONTRIBUTION"
																	: null)
															}
														/>
													</BodyShort>
												)}
												{metrics.length > 0 && (
													<dl className="auditView__metrics">
														{metrics.map(([key, value]) => {
															const warning =
																WARNING_METRICS.has(key) &&
																numericValue(value) !== 0;
															return (
																<div
																	className={`auditView__metric${warning ? " auditView__metric--warning" : ""}`}
																	key={key}
																>
																	<dt>{readable(key)}</dt>
																	<dd>
																		<BodyShort
																			as="span"
																			size="large"
																			weight="semibold"
																		>
																			{value}
																		</BodyShort>
																	</dd>
																</div>
															);
														})}
													</dl>
												)}
												<dl className="auditView__details">
													<dt>Status</dt>
													<dd
														className={`auditView__statusText auditView__statusText--${status}`}
													>
														{readable(item.outcome)}
													</dd>
													<dt>Actor</dt>
													<dd>{item.actorNavNoEmail || "System"}</dd>
													{item.correlationId && (
														<>
															<dt>Run ID</dt>
															<dd>
																<code className="hubRedesign__mono">
																	{item.correlationId}
																</code>
																<Button
																	variant="tertiary"
																	size="small"
																	onClick={() =>
																		item.correlationId &&
																		filterRun(item.correlationId)
																	}
																>
																	Show all events in this run
																</Button>
															</dd>
														</>
													)}
													{item.targetParticipantId && (
														<>
															<dt>Participant</dt>
															<dd>
																{item.targetParticipantName && (
																	<span>{item.targetParticipantName} </span>
																)}
																<code className="hubRedesign__mono">
																	{item.targetParticipantId}
																</code>
															</dd>
														</>
													)}
												</dl>
											</div>
										)}
									</li>
								);
							})}
						</ol>
						<footer className="auditView__footer">
							<BodyShort>
								Showing {start}–{end} of {result.total} events
							</BodyShort>
							<HStack gap="space-8">
								<Button
									variant="secondary"
									size="small"
									disabled={pageNumber === 0}
									onClick={() =>
										setPageNumber((current) => Math.max(current - 1, 0))
									}
								>
									← Newer
								</Button>
								<Button
									variant="secondary"
									size="small"
									disabled={(pageNumber + 1) * result.size >= result.total}
									onClick={() => setPageNumber((current) => current + 1)}
								>
									Older →
								</Button>
							</HStack>
						</footer>
					</>
				)}
			</Box>
		</div>
	);
}

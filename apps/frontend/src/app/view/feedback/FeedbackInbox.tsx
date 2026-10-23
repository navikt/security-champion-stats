"use client";

import { ArrowCirclepathIcon } from "@navikt/aksel-icons";
import {
	BodyShort,
	Button,
	Heading,
	Select,
	Skeleton,
	TextField,
	ToggleGroup,
} from "@navikt/ds-react";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useMe } from "@/app/shared/hooks/UseMe";
import {
	type Feedback,
	FeedbackApi,
	type FeedbackCounts,
	type FeedbackPage,
} from "./FeedbackApi";
import { FeedbackDetail } from "./FeedbackDetail";
import {
	ownerLabel,
	receivedLabel,
	scoreLabels,
	statusLabels,
} from "./FeedbackPresentation";
import "@/app/style/feedback/FeedbackInbox.css";

const BASE_PATH = "/appsec/feedback";
const statusFilters: {
	value: string;
	label: string;
	count: keyof FeedbackCounts;
}[] = [
	{ value: "active", label: "Active", count: "active" },
	{ value: "OPEN", label: "Open", count: "open" },
	{ value: "IN_PROGRESS", label: "In progress", count: "inProgress" },
	{ value: "CLOSED", label: "Resolved", count: "resolved" },
	{ value: "all", label: "All", count: "all" },
];

function updateUrl(params: URLSearchParams, id: string | null, push = false) {
	const path = id ? `${BASE_PATH}/${encodeURIComponent(id)}` : BASE_PATH;
	const query = params.toString();
	window.history[push ? "pushState" : "replaceState"](
		null,
		"",
		`${path}${query ? `?${query}` : ""}`,
	);
}

function Inbox() {
	const { me, loading } = useMe();
	const searchParams = useSearchParams();
	const pathname = usePathname();
	const selected = pathname.startsWith(`${BASE_PATH}/`)
		? pathname.slice(BASE_PATH.length + 1)
		: null;
	const params = new URLSearchParams();
	params.set("status", searchParams.get("status") ?? "active");
	params.set("owner", searchParams.get("owner") ?? "all");
	if (searchParams.get("q")) params.set("q", searchParams.get("q") ?? "");
	if (searchParams.get("scoreDecision"))
		params.set("scoreDecision", searchParams.get("scoreDecision") ?? "");
	const filterKey = params.toString();
	const [query, setQuery] = useState(searchParams.get("q") ?? "");
	const urlQuery = searchParams.get("q") ?? "";
	const [page, setPage] = useState<FeedbackPage | null>(null);
	const [pageKey, setPageKey] = useState("");
	const [error, setError] = useState("");
	const [attempt, setAttempt] = useState(0);
	const [fetching, setFetching] = useState(false);
	const [loadingMore, setLoadingMore] = useState(false);
	const [updated, setUpdated] = useState(false);
	const searchRef = useRef<HTMLInputElement>(null);
	const listRef = useRef<HTMLDivElement>(null);
	const moreRef = useRef<HTMLButtonElement>(null);
	const loadedLimit = useRef(50);
	const previousKey = useRef(filterKey);
	const latestSelection = useRef(selected);
	latestSelection.current = selected;
	const latestParams = useRef(filterKey);
	latestParams.current = filterKey;
	const initialDeepLink = useRef(selected);
	const pageRequest = useRef(0);
	const paginationController = useRef<AbortController | null>(null);
	const pageCurrent = pageKey === filterKey;

	useEffect(() => {
		setQuery(urlQuery);
	}, [urlQuery]);

	useEffect(() => {
		if (query === urlQuery) return;
		const timeout = window.setTimeout(() => {
			const next = new URLSearchParams(latestParams.current);
			if (query.trim()) next.set("q", query.trim());
			else next.delete("q");
			updateUrl(next, latestSelection.current);
		}, 250);
		return () => window.clearTimeout(timeout);
	}, [query, urlQuery]);

	const refresh = useCallback(() => {
		setAttempt((value) => value + 1);
	}, []);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		const interval = window.setInterval(refresh, 60_000);
		return () => window.clearInterval(interval);
	}, [loading, me.isAdmin, refresh]);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		const controller = new AbortController();
		const generation = ++pageRequest.current;
		paginationController.current?.abort();
		paginationController.current = null;
		if (previousKey.current !== filterKey) {
			loadedLimit.current = 50;
			initialDeepLink.current = null;
		}
		previousKey.current = filterKey;
		setFetching(true);
		setLoadingMore(false);
		setError("");
		async function load() {
			try {
				let result: FeedbackPage;
				const items: Feedback[] = [];
				const next = new URLSearchParams(filterKey);
				do {
					result = await FeedbackApi.search(next, controller.signal);
					items.push(...result.items);
					if (result.nextCursor) next.set("cursor", result.nextCursor);
				} while (result.nextCursor && items.length < loadedLimit.current);
				if (controller.signal.aborted || generation !== pageRequest.current)
					return;
				setPage({ ...result, items });
				setPageKey(filterKey);
				const id = latestSelection.current;
				if (
					!items.some((item) => item.id === id) &&
					!(initialDeepLink.current && initialDeepLink.current === id)
				) {
					updateUrl(new URLSearchParams(filterKey), items[0]?.id ?? null);
				}
				if (attempt > 0) setUpdated(true);
			} catch (cause) {
				if (controller.signal.aborted || generation !== pageRequest.current)
					return;
				console.error("Failed to load feedback inbox", cause);
				setError(
					cause instanceof Error
						? cause.message
						: "The inbox could not be loaded.",
				);
			} finally {
				if (!controller.signal.aborted && generation === pageRequest.current)
					setFetching(false);
			}
		}
		void load();
		return () => {
			controller.abort();
			paginationController.current?.abort();
		};
	}, [loading, me.isAdmin, filterKey, attempt]);

	useEffect(() => {
		if (!updated) return;
		const timeout = window.setTimeout(() => setUpdated(false), 4000);
		return () => window.clearTimeout(timeout);
	}, [updated]);

	const loadMore = useCallback(async () => {
		if (
			!page?.nextCursor ||
			loadingMore ||
			fetching ||
			!pageCurrent ||
			paginationController.current
		)
			return;
		const generation = pageRequest.current;
		const controller = new AbortController();
		paginationController.current = controller;
		setLoadingMore(true);
		setError("");
		try {
			const next = new URLSearchParams(filterKey);
			next.set("cursor", page.nextCursor);
			const result = await FeedbackApi.search(next, controller.signal);
			if (controller.signal.aborted || generation !== pageRequest.current)
				return;
			setPage((current) =>
				current
					? {
							...result,
							items: [
								...current.items,
								...result.items.filter(
									(item) =>
										!current.items.some((existing) => existing.id === item.id),
								),
							],
						}
					: result,
			);
			loadedLimit.current += 50;
		} catch (cause) {
			if (controller.signal.aborted || generation !== pageRequest.current)
				return;
			console.error("Failed to load more feedback", cause);
			setError(
				cause instanceof Error
					? cause.message
					: "More feedback could not be loaded.",
			);
		} finally {
			if (paginationController.current === controller) {
				paginationController.current = null;
				if (!controller.signal.aborted && generation === pageRequest.current)
					setLoadingMore(false);
			}
		}
	}, [page, loadingMore, fetching, pageCurrent, filterKey]);

	useEffect(() => {
		if (
			error ||
			!moreRef.current ||
			!page?.nextCursor ||
			!window.IntersectionObserver
		)
			return;
		const observer = new IntersectionObserver(
			(entries) => {
				if (entries.some((entry) => entry.isIntersecting)) void loadMore();
			},
			{ root: listRef.current },
		);
		observer.observe(moreRef.current);
		return () => observer.disconnect();
	}, [loadMore, page?.nextCursor, error]);

	function filter(name: string, value: string) {
		const next = new URLSearchParams(filterKey);
		if (value) next.set(name, value);
		else next.delete(name);
		initialDeepLink.current = null;
		updateUrl(next, selected);
	}
	const select = useCallback((id: string) => {
		initialDeepLink.current = null;
		updateUrl(new URLSearchParams(latestParams.current), id, true);
	}, []);
	const move = useCallback(
		(direction: number, focus = false) => {
			if (!pageCurrent || !page?.items.length) return;
			const index = page.items.findIndex((item) => item.id === selected);
			const next =
				page.items[
					Math.max(0, Math.min(page.items.length - 1, index + direction))
				];
			select(next.id);
			if (focus) document.getElementById(`feedback-${next.id}`)?.focus();
			document
				.getElementById(`feedback-${next.id}`)
				?.scrollIntoView?.({ block: "nearest" });
		},
		[page, selected, pageCurrent, select],
	);

	useEffect(() => {
		function keydown(event: KeyboardEvent) {
			if (
				event.defaultPrevented ||
				event.metaKey ||
				event.ctrlKey ||
				event.altKey
			)
				return;
			const target = event.target;
			if (
				target instanceof HTMLElement &&
				target.closest("input, textarea, select, [contenteditable='true']")
			) {
				if (event.key === "Escape") target.blur();
				return;
			}
			if (event.key === "/") {
				event.preventDefault();
				searchRef.current?.focus();
			} else if (event.key === "j" || event.key === "k") {
				event.preventDefault();
				move(
					event.key === "j" ? 1 : -1,
					target instanceof HTMLElement &&
						target.getAttribute("role") === "option",
				);
			}
		}
		document.addEventListener("keydown", keydown);
		return () => document.removeEventListener("keydown", keydown);
	}, [move]);

	const opened = useCallback((id: string, wasUnread: boolean) => {
		setPage((current) => {
			if (!current) return current;
			const item = current.items.find((item) => item.id === id);
			const unread = item ? !item.readByMe : wasUnread;
			return {
				...current,
				items: current.items.map((item) =>
					item.id === id ? { ...item, readByMe: true } : item,
				),
				counts: {
					...current.counts,
					unread: Math.max(0, current.counts.unread - (unread ? 1 : 0)),
				},
			};
		});
	}, []);
	const changed = useCallback(
		(feedback: Feedback) => {
			setPage((current) =>
				current
					? {
							...current,
							items: current.items.map((item) =>
								item.id === feedback.id ? feedback : item,
							),
						}
					: current,
			);
			initialDeepLink.current = null;
			refresh();
		},
		[refresh],
	);

	if (loading) return <p role="status">Loading...</p>;
	if (!me.isAdmin) return <p>Administrator access is required.</p>;
	const counts = page?.counts;
	const items = pageCurrent ? (page?.items ?? []) : [];
	const index = items.findIndex((item) => item.id === selected);
	const showDetail =
		selected && (items.length > 0 || initialDeepLink.current === selected);

	return (
		<div className="hubRedesign feedbackInbox">
			<div className="feedbackInbox__header">
				<header className="hubRedesign__header">
					<BodyShort size="small" className="hubRedesign__eyebrow">
						Admin only
					</BodyShort>
					<Heading level="1" size="large">
						Feedback inbox
					</Heading>
					<BodyShort>
						Read without claiming, or take ownership to follow up. Scoring and
						handling are independent.
					</BodyShort>
				</header>
				<dl className="feedbackInbox__counters">
					{(["unread", "unassigned", "mine"] as const).map((key) => (
						<div
							key={key}
							className={`hubRedesign__stat feedbackInbox__counter--${key}`}
							data-attention={
								(key === "unassigned" && (counts?.unassigned ?? 0) > 0) ||
								undefined
							}
						>
							<dt className="hubRedesign__statLabel">
								{key === "unread"
									? "Unread"
									: key === "mine"
										? "Mine"
										: "Unassigned"}
							</dt>
							<dd className="hubRedesign__statValue">{counts?.[key] ?? "–"}</dd>
						</div>
					))}
				</dl>
			</div>
			<section className="feedbackInbox__toolbar" aria-label="Inbox filters">
				<div className="feedbackInbox__search">
					<TextField
						ref={searchRef}
						label="Search feedback"
						size="small"
						hideLabel
						placeholder="Search feedback"
						maxLength={200}
						value={query}
						onChange={(event) => setQuery(event.target.value)}
					/>
					{query && (
						<Button
							size="xsmall"
							variant="tertiary"
							onClick={() => setQuery("")}
						>
							Clear
						</Button>
					)}
				</div>
				<div className="feedbackInbox__statusFilter">
					<ToggleGroup
						size="small"
						aria-label="Status"
						aria-controls="feedback-list"
						value={params.get("status") ?? "active"}
						onChange={(value) => filter("status", value)}
					>
						{statusFilters.map(({ value, label, count }) => (
							<ToggleGroup.Item
								key={value}
								value={value}
								label={`${label} ${counts?.[count] ?? "–"}`}
							/>
						))}
					</ToggleGroup>
				</div>
				<Select
					label="Status filter"
					size="small"
					className="feedbackInbox__mobileStatus"
					value={params.get("status") ?? "active"}
					onChange={(event) => filter("status", event.target.value)}
				>
					{statusFilters.map(({ value, label, count }) => (
						<option key={value} value={value}>
							{label} {counts?.[count] ?? "–"}
						</option>
					))}
				</Select>
				<Select
					label="Owner filter"
					hideLabel
					size="small"
					value={params.get("owner") ?? "all"}
					onChange={(event) => filter("owner", event.target.value)}
				>
					<option value="all">All owners</option>
					<option value="mine">Assigned to me</option>
					<option value="unassigned">Unassigned</option>
				</Select>
				<Select
					label="Scoring filter"
					hideLabel
					size="small"
					value={params.get("scoreDecision") ?? ""}
					onChange={(event) => filter("scoreDecision", event.target.value)}
				>
					<option value="">All scoring</option>
					{Object.entries(scoreLabels).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</Select>
				<Button
					size="small"
					variant="secondary"
					icon={<ArrowCirclepathIcon aria-hidden />}
					title="Refresh inbox"
					aria-label="Refresh inbox"
					loading={fetching}
					onClick={refresh}
				/>
			</section>
			{error && (
				<div
					className="hubRedesign__attention hubRedesign__attention--danger"
					role="alert"
				>
					{error}{" "}
					<Button size="small" variant="tertiary" onClick={refresh}>
						Retry inbox
					</Button>
				</div>
			)}
			<div className="feedbackInbox__panes">
				<section
					className="hubRedesign__card feedbackInbox__listPane"
					aria-label="Feedback list"
				>
					<div className="feedbackInbox__listHeader">
						<span>
							{items.length} items
							{pageCurrent && page?.nextCursor ? " loaded" : ""}
						</span>
						<span role="status">
							{updated ? "Updated just now" : "j / k to move"}
						</span>
					</div>
					<div ref={listRef} className="feedbackInbox__scroll">
						{fetching && !pageCurrent && (
							<div role="status" aria-label="Loading feedback">
								{[0, 1, 2, 3, 4, 5].map((key) => (
									<div key={key} className="feedbackInbox__skeleton">
										<Skeleton variant="text" width="80%" />
										<Skeleton variant="text" width="60%" />
										<Skeleton variant="text" />
									</div>
								))}
							</div>
						)}
						{!fetching && !error && items.length === 0 && (
							<p className="feedbackInbox__empty">
								No feedback matches these filters.
							</p>
						)}
						<div
							id="feedback-list"
							role="listbox"
							aria-label="Feedback"
							aria-busy={fetching}
						>
							{items.map((feedback) => (
								<button
									key={feedback.id}
									id={`feedback-${feedback.id}`}
									type="button"
									role="option"
									aria-selected={selected === feedback.id}
									aria-label={`${feedback.readByMe ? "" : "Unread: "}${feedback.title}, ${feedback.submitterName}, ${statusLabels[feedback.status]}, ${ownerLabel(feedback, page?.actorEmail ?? "")}, ${scoreLabels[feedback.scoreDecision]}`}
									tabIndex={
										selected === feedback.id ||
										(index < 0 && feedback.id === items[0]?.id)
											? 0
											: -1
									}
									className={`feedbackInbox__row${feedback.readByMe ? "" : " feedbackInbox__row--unread"}`}
									onClick={() => select(feedback.id)}
									onKeyDown={(event) => {
										if (event.key === "ArrowDown" || event.key === "ArrowUp") {
											event.preventDefault();
											move(event.key === "ArrowDown" ? 1 : -1, true);
										}
									}}
								>
									<span className="feedbackInbox__unreadDot" aria-hidden />
									<span className="feedbackInbox__rowContent">
										<span className="feedbackInbox__subject">
											{feedback.title}
										</span>
										<span>{feedback.submitterName}</span>
										<span className="hubRedesign__muted">
											{feedback.description.replace(/\s+/g, " ").slice(0, 160)}
										</span>
										<span className="feedbackInbox__meta">
											<span
												className={`feedbackInbox__status--${feedback.status}`}
											>
												{statusLabels[feedback.status]}
											</span>
											{" · "}
											{
												ownerLabel(feedback, page?.actorEmail ?? "").split(
													" ",
												)[0]
											}
											{" · "}
											<span
												className={`feedbackInbox__score--${feedback.scoreDecision}`}
											>
												{scoreLabels[feedback.scoreDecision]}
											</span>
										</span>
									</span>
									<time
										dateTime={feedback.createdAt}
										className="feedbackInbox__received"
									>
										{receivedLabel(feedback.createdAt)}
									</time>
								</button>
							))}
						</div>
						{pageCurrent && page?.nextCursor && (
							<div className="feedbackInbox__more">
								<Button
									ref={moreRef}
									size="small"
									variant="tertiary"
									loading={loadingMore}
									disabled={fetching || loadingMore}
									onClick={() => void loadMore()}
								>
									Load more feedback
								</Button>
							</div>
						)}
					</div>
				</section>
				{showDetail && (
					<FeedbackDetail
						key={selected}
						id={selected}
						actor={page?.actorEmail ?? me.username}
						refreshKey={attempt}
						onUpdated={changed}
						onOpened={opened}
						onMove={move}
						hasPrevious={index > 0}
						hasNext={index >= 0 && index < items.length - 1}
					/>
				)}
			</div>
		</div>
	);
}

export function FeedbackInbox() {
	return (
		<Suspense fallback={<p role="status">Loading feedback inbox...</p>}>
			<Inbox />
		</Suspense>
	);
}

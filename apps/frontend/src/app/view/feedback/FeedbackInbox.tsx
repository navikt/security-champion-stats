"use client";

import { Button, Select, Textarea, TextField, VStack } from "@navikt/ds-react";
import { type FormEvent, useEffect, useState } from "react";
import { useMe } from "@/app/shared/hooks/UseMe";
import {
	type Feedback,
	type FeedbackAction,
	FeedbackApi,
	type FeedbackPage,
} from "./FeedbackApi";

const statusLabels = {
	OPEN: "Open",
	IN_PROGRESS: "In progress",
	CLOSED: "Closed",
};
const scoreLabels = {
	PENDING: "Not assessed",
	QUALIFIED: "Qualified",
	NOT_QUALIFIED: "Does not qualify",
};
const actionLabels: Record<FeedbackAction, string> = {
	CLAIM: "Take ownership",
	RELEASE: "Release ownership",
	NOTE: "Add internal note",
	INTERNAL_DISCUSSION: "Record internal discussion",
	EXTERNAL_TICKET: "Record external ticket",
	CONTACT_SUBMITTER: "Record contact with submitter",
	CLOSE: "Close feedback",
	REOPEN: "Reopen feedback",
	QUALIFY: "Qualify and award feedback credit",
	DECLINE: "Mark as not qualifying",
};
const dateTime = new Intl.DateTimeFormat("en-GB", {
	dateStyle: "medium",
	timeStyle: "short",
	timeZone: "Europe/Oslo",
});

function FeedbackDetail({
	id,
	actor,
	onUpdated,
}: {
	id: string;
	actor: string;
	onUpdated: () => void;
}) {
	const [feedback, setFeedback] = useState<Feedback | null>(null);
	const [attempt, setAttempt] = useState(0);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [message, setMessage] = useState("");
	const [action, setAction] = useState<FeedbackAction>("NOTE");
	const [note, setNote] = useState("");
	const [ticketUrl, setTicketUrl] = useState("");

	// biome-ignore lint/correctness/useExhaustiveDependencies: attempt triggers an explicit reload.
	useEffect(() => {
		let current = true;
		setFeedback(null);
		setError("");
		setMessage("");
		setAction("NOTE");
		FeedbackApi.detail(id)
			.then((result) => {
				if (current) setFeedback(result);
			})
			.catch((error: unknown) => {
				console.error("Failed to load feedback", error);
				if (current)
					setError(
						error instanceof Error
							? error.message
							: "The feedback could not be loaded.",
					);
			});
		return () => {
			current = false;
		};
	}, [id, attempt]);

	async function act(nextAction: FeedbackAction) {
		if (!feedback || busy) return;
		setError("");
		setMessage("");
		if (
			!["CLAIM", "RELEASE", "REOPEN"].includes(nextAction) &&
			(note.trim().length < 1 || note.trim().length > 3000)
		) {
			setError("Provide a note of up to 3000 characters.");
			return;
		}
		setBusy(true);
		try {
			const result = await FeedbackApi.act(
				feedback,
				nextAction,
				["CLAIM", "RELEASE", "REOPEN"].includes(nextAction) ? "" : note,
				nextAction === "EXTERNAL_TICKET" ? ticketUrl : undefined,
			);
			setFeedback(result);
			setNote("");
			setTicketUrl("");
			setAction("NOTE");
			setMessage("The action has been saved.");
			onUpdated();
		} catch (error) {
			console.error("Feedback action failed", error);
			setError(
				error instanceof Error
					? error.message
					: "The action could not be saved.",
			);
		} finally {
			setBusy(false);
		}
	}

	const owned =
		feedback?.ownerEmail === actor && feedback.status === "IN_PROGRESS";
	const actions: FeedbackAction[] = [
		"NOTE",
		"INTERNAL_DISCUSSION",
		"EXTERNAL_TICKET",
		"CONTACT_SUBMITTER",
		"CLOSE",
	];
	if (feedback?.scoreDecision === "PENDING") {
		if (
			feedback.submitterActive &&
			feedback.submitterEmail.toLowerCase() !== actor.toLowerCase()
		)
			actions.push("QUALIFY");
		actions.push("DECLINE");
	}
	return (
		<VStack
			as="section"
			gap="space-16"
			aria-label="Selected feedback"
			aria-busy={busy}
		>
			<Button
				variant="secondary"
				disabled={busy}
				onClick={() => setAttempt((value) => value + 1)}
			>
				Reload feedback
			</Button>
			{error && <p role="alert">{error}</p>}
			{message && <p role="status">{message}</p>}
			{!feedback && !error && <p role="status">Loading feedback...</p>}
			{feedback && (
				<>
					<h2>{feedback.title}</h2>
					<p>
						From {feedback.submitterName} ({feedback.submitterEmail}) on{" "}
						{dateTime.format(new Date(feedback.createdAt))}
					</p>
					<p className="feedback__content">{feedback.description}</p>
					<p>
						Status: {statusLabels[feedback.status]} | Owner:{" "}
						{feedback.ownerEmail ?? "Unassigned"} | Scoring:{" "}
						{scoreLabels[feedback.scoreDecision]}
					</p>
					{feedback.status === "OPEN" && (
						<Button disabled={busy} onClick={() => act("CLAIM")}>
							Take ownership
						</Button>
					)}
					{feedback.status === "CLOSED" && (
						<Button disabled={busy} onClick={() => act("REOPEN")}>
							Reopen feedback
						</Button>
					)}
					{owned && (
						<>
							<Button
								variant="secondary"
								disabled={busy}
								onClick={() => act("RELEASE")}
							>
								Release ownership
							</Button>
							<VStack
								as="form"
								gap="space-16"
								noValidate
								onSubmit={(event: FormEvent) => {
									event.preventDefault();
									void act(action);
								}}
							>
								<Select
									label="Follow-up action"
									value={action}
									disabled={busy}
									onChange={(event) => {
										const selected = actions.find(
											(value) => value === event.target.value,
										);
										if (selected) setAction(selected);
									}}
								>
									{actions.map((value) => (
										<option key={value} value={value}>
											{actionLabels[value]}
										</option>
									))}
								</Select>
								<Textarea
									label="Internal note"
									description="Only administrators can read this note. Record what happened; this does not send messages or create tickets."
									value={note}
									maxLength={3000}
									aria-required
									readOnly={busy}
									onChange={(event) => setNote(event.target.value)}
								/>
								{action === "EXTERNAL_TICKET" && (
									<TextField
										label="Ticket URL"
										type="url"
										aria-required
										maxLength={1000}
										value={ticketUrl}
										readOnly={busy}
										onChange={(event) => setTicketUrl(event.target.value)}
									/>
								)}
								<Button type="submit" loading={busy} disabled={busy}>
									Save action
								</Button>
							</VStack>
						</>
					)}
					<h3>Handling history</h3>
					{feedback.history.length === 0 ? (
						<p>No actions recorded. Reading feedback does not claim it.</p>
					) : (
						<ol>
							{feedback.history.map((entry) => (
								<li key={entry.id}>
									<p>
										{actionLabels[entry.action]} |{" "}
										{entry.actorEmail ?? "Deleted administrator"} |{" "}
										{dateTime.format(new Date(entry.createdAt))}
									</p>
									{entry.note && (
										<p className="feedback__content">{entry.note}</p>
									)}
									{entry.ticketUrl && (
										<a href={entry.ticketUrl} target="_blank" rel="noreferrer">
											Open external ticket
										</a>
									)}
								</li>
							))}
						</ol>
					)}
				</>
			)}
		</VStack>
	);
}

export function FeedbackInbox() {
	const { me, loading } = useMe();
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState("");
	const [owner, setOwner] = useState("all");
	const [score, setScore] = useState("");
	const [params, setParams] = useState(() => new URLSearchParams());
	const [page, setPage] = useState<FeedbackPage | null>(null);
	const [selected, setSelected] = useState<string | null>(null);
	const [error, setError] = useState("");
	const [attempt, setAttempt] = useState(0);
	const [fetching, setFetching] = useState(false);
	// biome-ignore lint/correctness/useExhaustiveDependencies: attempt refreshes the inbox after a saved action or retry.
	useEffect(() => {
		if (loading || !me.isAdmin) return;
		let current = true;
		setFetching(true);
		setError("");
		FeedbackApi.search(params)
			.then((result) => {
				if (current) setPage(result);
			})
			.catch((error: unknown) => {
				console.error("Feedback search failed", error);
				if (current)
					setError(
						error instanceof Error
							? error.message
							: "Feedback could not be loaded.",
					);
			})
			.finally(() => {
				if (current) setFetching(false);
			});
		return () => {
			current = false;
		};
	}, [loading, me.isAdmin, params, attempt]);

	function search(event: FormEvent) {
		event.preventDefault();
		const next = new URLSearchParams({ q: query, owner });
		if (status) next.set("status", status);
		if (score) next.set("scoreDecision", score);
		setParams(next);
	}
	function paginate(offset: number) {
		const next = new URLSearchParams(params);
		next.set("offset", String(offset));
		setParams(next);
	}

	if (loading) return <p role="status">Loading...</p>;
	if (!me.isAdmin) return <p>Administrator access is required.</p>;
	const offset = Number(params.get("offset") ?? 0);
	return (
		<VStack gap="space-24">
			<h1>Feedback inbox</h1>
			<p>
				Read without claiming, or take ownership to follow up. Scoring and
				handling are independent.
			</p>
			<VStack as="form" gap="space-16" onSubmit={search}>
				<TextField
					label="Search feedback"
					description="Search submissions, submitters, internal notes, or ticket URLs."
					value={query}
					maxLength={200}
					onChange={(event) => setQuery(event.target.value)}
				/>
				<Select
					label="Status"
					value={status}
					onChange={(event) => setStatus(event.target.value)}
				>
					<option value="">All statuses</option>
					{Object.entries(statusLabels).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</Select>
				<Select
					label="Owner"
					value={owner}
					onChange={(event) => setOwner(event.target.value)}
				>
					<option value="all">All owners</option>
					<option value="mine">Assigned to me</option>
					<option value="unassigned">Unassigned</option>
				</Select>
				<Select
					label="Scoring decision"
					value={score}
					onChange={(event) => setScore(event.target.value)}
				>
					<option value="">All decisions</option>
					{Object.entries(scoreLabels).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</Select>
				<Button type="submit" loading={fetching}>
					Search
				</Button>
			</VStack>
			{error && <p role="alert">{error}</p>}
			<Button
				variant="secondary"
				disabled={fetching}
				onClick={() => setAttempt((value) => value + 1)}
			>
				Refresh inbox
			</Button>
			{fetching && <p role="status">Loading feedback...</p>}
			{!fetching && !error && page?.items.length === 0 && (
				<p>No feedback matches your search.</p>
			)}
			{!fetching && !error && page && (
				<>
					<ul>
						{page.items.map((feedback) => (
							<li key={feedback.id}>
								<Button
									variant="tertiary"
									aria-pressed={selected === feedback.id}
									onClick={() => setSelected(feedback.id)}
								>
									{feedback.title}
								</Button>
								<p>
									{feedback.submitterName} | {statusLabels[feedback.status]} |{" "}
									{feedback.ownerEmail ?? "Unassigned"} |{" "}
									{scoreLabels[feedback.scoreDecision]} |{" "}
									{dateTime.format(new Date(feedback.createdAt))}
								</p>
							</li>
						))}
					</ul>
					{offset > 0 && (
						<Button
							variant="secondary"
							onClick={() => paginate(Math.max(0, offset - 25))}
						>
							Previous page
						</Button>
					)}
					{page.nextOffset !== null && (
						<Button
							variant="secondary"
							onClick={() => paginate(page.nextOffset ?? 0)}
						>
							Next page
						</Button>
					)}
				</>
			)}
			{selected && page && (
				<FeedbackDetail
					key={selected}
					id={selected}
					actor={page.actorEmail}
					onUpdated={() => setAttempt((value) => value + 1)}
				/>
			)}
		</VStack>
	);
}

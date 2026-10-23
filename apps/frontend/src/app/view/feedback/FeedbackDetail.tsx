"use client";

import { ArrowDownIcon, ArrowUpIcon } from "@navikt/aksel-icons";
import {
	BodyShort,
	Button,
	Heading,
	Select,
	Tabs,
	Textarea,
	TextField,
} from "@navikt/ds-react";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type Feedback,
	type FeedbackAction,
	FeedbackApi,
	type FeedbackStatus,
} from "./FeedbackApi";
import {
	dateTime,
	historyLabel,
	ownerLabel,
	owns,
	statusLabels,
	validTicketUrl,
} from "./FeedbackPresentation";

function FeedbackText({ text }: { text: string }) {
	const content = [];
	let end = 0;
	for (const match of text.matchAll(/https?:\/\/[^\s<>]+/g)) {
		content.push(text.slice(end, match.index));
		const url = match[0].replace(/[.,;!?)\]]+$/, "");
		content.push(
			validTicketUrl(url) ? (
				<a key={match.index} href={url} target="_blank" rel="noreferrer">
					{url}
				</a>
			) : (
				url
			),
		);
		content.push(match[0].slice(url.length));
		end = match.index + match[0].length;
	}
	content.push(text.slice(end));
	return <>{content}</>;
}

export function FeedbackDetail({
	id,
	actor,
	refreshKey,
	onUpdated,
	onOpened,
	onMove,
	hasPrevious,
	hasNext,
}: {
	id: string;
	actor: string;
	refreshKey: number;
	onUpdated: (feedback: Feedback) => void;
	onOpened: (id: string, wasUnread: boolean) => void;
	onMove: (direction: number) => void;
	hasPrevious: boolean;
	hasNext: boolean;
}) {
	const [feedback, setFeedback] = useState<Feedback | null>(null);
	const [attempt, setAttempt] = useState(0);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [message, setMessage] = useState("");
	const [tab, setTab] = useState("note");
	const [note, setNote] = useState("");
	const [ticketUrl, setTicketUrl] = useState("");
	const busyRef = useRef(false);
	const generation = useRef(0);
	const loadController = useRef<AbortController | null>(null);

	// biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey and attempt explicitly reload detail without discarding drafts.
	useEffect(() => {
		if (busyRef.current) return;
		const controller = new AbortController();
		loadController.current = controller;
		const request = ++generation.current;
		setError("");
		async function load() {
			try {
				const result = await FeedbackApi.detail(id, controller.signal);
				if (controller.signal.aborted || generation.current !== request) return;
				setFeedback(result);
				if (!result.readByMe) await FeedbackApi.markRead(id, controller.signal);
				if (controller.signal.aborted || generation.current !== request) return;
				setFeedback((current) =>
					current ? { ...current, readByMe: true } : current,
				);
				onOpened(id, !result.readByMe);
			} catch (cause) {
				if (controller.signal.aborted || generation.current !== request) return;
				console.error("Failed to load feedback or save read state", cause);
				setError(
					cause instanceof Error
						? cause.message
						: "Feedback or its read state could not be loaded.",
				);
			}
		}
		void load();
		return () => controller.abort();
	}, [id, refreshKey, attempt, onOpened]);

	const act = useCallback(
		async (action: FeedbackAction, status?: FeedbackStatus) => {
			if (!feedback || busyRef.current) return;
			if (action === "NOTE" && (!note.trim() || note.length > 3000)) return;
			if (action === "EXTERNAL_TICKET" && !validTicketUrl(ticketUrl.trim()))
				return;
			const previous = feedback;
			const request = ++generation.current;
			loadController.current?.abort();
			busyRef.current = true;
			setBusy(true);
			setError("");
			setMessage("");
			let optimistic = feedback;
			if (action === "CLAIM")
				optimistic = {
					...feedback,
					ownerEmail: actor,
					ownerName: actor,
					status: feedback.status === "OPEN" ? "IN_PROGRESS" : feedback.status,
				};
			if (action === "RELEASE")
				optimistic = { ...feedback, ownerEmail: null, ownerName: null };
			if (action === "STATUS" && status) optimistic = { ...feedback, status };
			if (action === "QUALIFY")
				optimistic = { ...feedback, scoreDecision: "QUALIFIED" };
			if (action === "DECLINE")
				optimistic = { ...feedback, scoreDecision: "NOT_QUALIFIED" };
			setFeedback(optimistic);
			try {
				const result = await FeedbackApi.act(
					previous,
					action,
					action === "NOTE" ? note : "",
					action === "EXTERNAL_TICKET" ? ticketUrl.trim() : undefined,
					status,
				);
				if (generation.current !== request) return;
				setFeedback(result);
				if (action === "NOTE") setNote("");
				if (action === "EXTERNAL_TICKET") setTicketUrl("");
				setMessage("The action has been saved.");
				onUpdated(result);
			} catch (cause) {
				if (generation.current !== request) return;
				console.error("Feedback action failed", cause);
				setFeedback(previous);
				setError(
					cause instanceof Error
						? cause.message
						: "The action could not be saved.",
				);
			} finally {
				busyRef.current = false;
				setBusy(false);
			}
		},
		[feedback, actor, note, ticketUrl, onUpdated],
	);

	const owned = feedback ? owns(feedback, actor) : false;
	const canAward =
		feedback?.submitterActive &&
		feedback.submitterEmail.toLowerCase() !== actor.toLowerCase();
	const ticket = feedback?.history.find((entry) => entry.ticketUrl)?.ticketUrl;
	const validNote = Boolean(note.trim()) && note.length <= 3000;
	const validTicket = validTicketUrl(ticketUrl.trim());
	const ownershipLabel = feedback?.ownerEmail ? "Take over" : "Take ownership";

	return (
		<section
			className="hubRedesign__card feedbackInbox__detail"
			aria-labelledby={feedback ? "feedback-subject" : undefined}
			aria-label={feedback ? undefined : "Selected feedback"}
			aria-live="polite"
			aria-busy={busy}
		>
			{error && (
				<div
					className="hubRedesign__attention hubRedesign__attention--danger"
					role="alert"
				>
					{error}{" "}
					<Button
						size="small"
						variant="tertiary"
						onClick={() => setAttempt((value) => value + 1)}
					>
						Reload feedback
					</Button>
				</div>
			)}
			{!feedback && !error && (
				<p className="feedbackInbox__empty" role="status">
					Loading feedback...
				</p>
			)}
			{message && (
				<p className="feedbackInbox__message" role="status">
					{message}
				</p>
			)}
			{feedback && (
				<>
					<header className="feedbackInbox__detailHeader">
						<div className="feedbackInbox__subjectHeader">
							<Heading id="feedback-subject" level="2" size="medium">
								{feedback.title}
							</Heading>
							<div className="feedbackInbox__actions">
								<Button
									size="xsmall"
									variant="tertiary"
									icon={<ArrowUpIcon aria-hidden />}
									aria-label="Previous (k)"
									title="Previous (k)"
									disabled={!hasPrevious || busy}
									onClick={() => onMove(-1)}
								/>
								<Button
									size="xsmall"
									variant="tertiary"
									icon={<ArrowDownIcon aria-hidden />}
									aria-label="Next (j)"
									title="Next (j)"
									disabled={!hasNext || busy}
									onClick={() => onMove(1)}
								/>
							</div>
						</div>
						<div className="feedbackInbox__from">
							<span className="feedbackInbox__avatar" aria-hidden>
								{feedback.submitterName
									.split(/\s+/)
									.filter(Boolean)
									.slice(0, 2)
									.map((part) => part[0])
									.join("")}
							</span>
							<strong>{feedback.submitterName}</strong>
							<a href={`mailto:${feedback.submitterEmail}`}>
								{feedback.submitterEmail}
							</a>
							<span>·</span>
							<time dateTime={feedback.createdAt}>
								{dateTime.format(new Date(feedback.createdAt))}
							</time>
						</div>
					</header>
					<div className="feedbackInbox__properties">
						<div className="feedbackInbox__property">
							<span className="feedbackInbox__propertyLabel">Owner</span>
							<div className="feedbackInbox__actions">
								<span>{ownerLabel(feedback, actor)}</span>
								<Button
									size="xsmall"
									variant={owned ? "tertiary" : "secondary"}
									disabled={busy}
									onClick={() => void act(owned ? "RELEASE" : "CLAIM")}
									onKeyDown={(event) => {
										if (
											!owned &&
											event.key === "t" &&
											!event.metaKey &&
											!event.ctrlKey &&
											!event.altKey
										) {
											event.preventDefault();
											void act("CLAIM");
										}
									}}
								>
									{owned ? "Release" : ownershipLabel}
								</Button>
							</div>
						</div>
						<div
							className={`feedbackInbox__property feedbackInbox__status--${feedback.status}`}
						>
							<Select
								label="Status"
								size="small"
								value={feedback.status}
								disabled={!owned || busy}
								aria-describedby={!owned ? "feedback-status-help" : undefined}
								title={!owned ? "Take ownership to change status" : undefined}
								onChange={(event) => {
									const status = Object.keys(statusLabels).find(
										(value): value is FeedbackStatus =>
											value === event.target.value,
									);
									if (status && status !== feedback.status)
										void act("STATUS", status);
								}}
							>
								{Object.entries(statusLabels).map(([value, label]) => (
									<option key={value} value={value}>
										{label}
									</option>
								))}
							</Select>
							{!owned && (
								<small id="feedback-status-help">
									Take ownership to change status.
								</small>
							)}
						</div>
						<div
							className={`feedbackInbox__property feedbackInbox__score--${feedback.scoreDecision}`}
						>
							<Select
								label="Scoring"
								size="small"
								value={feedback.scoreDecision}
								disabled={feedback.scoreDecision !== "PENDING" || busy}
								onChange={(event) => {
									if (event.target.value === "QUALIFIED" && canAward)
										void act("QUALIFY");
									if (event.target.value === "NOT_QUALIFIED")
										void act("DECLINE");
								}}
							>
								<option value="PENDING">Not assessed</option>
								<option value="QUALIFIED" disabled={!canAward}>
									Points awarded
									{feedback.awardedPoints !== null
										? ` · +${feedback.awardedPoints}`
										: ""}
								</option>
								<option value="NOT_QUALIFIED">No points</option>
							</Select>
							{feedback.scoreDecision !== "PENDING" ? (
								<small>
									Decision recorded. Use point adjustments for corrections.
								</small>
							) : (
								!canAward && (
									<small>
										{feedback.submitterActive
											? "Another administrator must award your feedback credit."
											: "The submitter is no longer an active participant."}
									</small>
								)
							)}
						</div>
					</div>
					<div className="feedbackInbox__detailBody">
						<p className="feedbackInbox__bodyText">
							<FeedbackText text={feedback.description} />
						</p>
						{ticket && (
							<div>
								<BodyShort
									size="small"
									className="feedbackInbox__propertyLabel"
								>
									Ticket
								</BodyShort>
								<a
									className="hubRedesign__mono"
									href={ticket}
									target="_blank"
									rel="noreferrer"
								>
									{ticket}
								</a>
							</div>
						)}
						{owned ? (
							<div className="feedbackInbox__composer">
								<Tabs size="small" value={tab} onChange={setTab}>
									<Tabs.List>
										<Tabs.Tab value="note" label="Internal note" />
										<Tabs.Tab value="ticket" label="Link ticket" />
									</Tabs.List>
									{(["note", "ticket"] as const).map((value) => (
										<Tabs.Panel key={value} value={value}>
											<form
												noValidate
												className="feedbackInbox__composerForm"
												onSubmit={(event) => {
													event.preventDefault();
													void act(
														value === "note" ? "NOTE" : "EXTERNAL_TICKET",
													);
												}}
												onKeyDown={(event) => {
													if (
														(event.metaKey || event.ctrlKey) &&
														event.key === "Enter"
													) {
														event.preventDefault();
														event.currentTarget.requestSubmit();
													}
												}}
											>
												{value === "note" ? (
													<Textarea
														label="Internal note"
														hideLabel
														value={note}
														minRows={3}
														resize="vertical"
														readOnly={busy}
														placeholder="Record what happened — only administrators can read this"
														onChange={(event) => setNote(event.target.value)}
														error={
															note.length > 3000
																? "Use no more than 3000 characters."
																: undefined
														}
													/>
												) : (
													<TextField
														label="Ticket URL"
														hideLabel
														type="url"
														value={ticketUrl}
														maxLength={1000}
														readOnly={busy}
														placeholder="https://github.com/… or Jira URL"
														onChange={(event) =>
															setTicketUrl(event.target.value)
														}
													/>
												)}
												<div className="feedbackInbox__composerFooter">
													<small>
														{value === "note" ? (
															<>
																Admins only · doesn't notify the submitter ·{" "}
																<span aria-live="polite">
																	{3000 - note.length} characters left
																</span>
															</>
														) : (
															"Records the link in history · doesn't create the ticket"
														)}
													</small>
													<Button
														size="small"
														type="submit"
														loading={busy}
														disabled={
															busy ||
															!(value === "note" ? validNote : validTicket)
														}
													>
														{value === "note" ? "Add note" : "Link ticket"}
													</Button>
												</div>
											</form>
										</Tabs.Panel>
									))}
								</Tabs>
							</div>
						) : (
							<div className="feedbackInbox__ownershipHint">
								<BodyShort size="small">
									Take ownership to add notes, link a ticket or change status.
								</BodyShort>
								<Button
									size="small"
									variant="secondary"
									disabled={busy}
									onClick={() => void act("CLAIM")}
								>
									{ownershipLabel}
								</Button>
							</div>
						)}
						<section aria-labelledby="feedback-history">
							<Heading id="feedback-history" level="3" size="small">
								Handling history
							</Heading>
							{feedback.history.length === 0 ? (
								<p className="hubRedesign__muted">
									No actions yet. Reading feedback doesn't claim it.
								</p>
							) : (
								<ol className="feedbackInbox__timeline">
									{feedback.history.map((entry) => (
										<li
											key={entry.id}
											className={`feedbackInbox__historyEntry feedbackInbox__historyEntry--${entry.action}`}
										>
											<span
												className="feedbackInbox__timelineDot"
												aria-hidden
											/>
											<div>
												<p>
													<strong>{historyLabel(entry)}</strong>
													{" · "}
													{entry.actorName ??
														entry.actorEmail ??
														"Deleted administrator"}
													{" · "}
													<time dateTime={entry.createdAt}>
														{dateTime.format(new Date(entry.createdAt))}
													</time>
												</p>
												{entry.note && (
													<p className="feedbackInbox__note">{entry.note}</p>
												)}
												{entry.ticketUrl && (
													<a
														className="hubRedesign__mono"
														href={entry.ticketUrl}
														target="_blank"
														rel="noreferrer"
													>
														{entry.ticketUrl}
													</a>
												)}
											</div>
										</li>
									))}
								</ol>
							)}
						</section>
					</div>
				</>
			)}
		</section>
	);
}

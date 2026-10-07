"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import { getPastEvents, getUpcomingEvents } from "@/app/utils/eventUtils";
import type {
	LeaderboardEntry,
	Me,
	ParticipantSeasonScore,
	ProgramParticipant,
	SecurityEvent,
} from "@/app/utils/Variables";
import { MembershipView } from "@/app/view/member/components/MembershipView";
import { hackerCopy } from "./hackerCopy";
import { hackerAlias } from "./hackerUtils";
import "../style/hackerOverview.css";

const levelThresholds = [
	{ level: "Novice", label: "SCRIPT KIDDIE", points: 0 },
	{ level: "Apprentice", label: "PACKET SNIFFER", points: 100 },
	{ level: "Adept", label: "SHELL JOCKEY", points: 250 },
	{ level: "Expert", label: "ELITE / 1337", points: 500 },
] as const;

function stableIndex(value: string, length: number): number {
	return (
		[...value].reduce(
			(hash, character) => (hash * 31 + character.charCodeAt(0)) >>> 0,
			7,
		) % length
	);
}

function formatDate(value: string): string {
	const date = new Date(value);
	return Number.isNaN(date.getTime())
		? "DATE UNKNOWN"
		: date.toLocaleDateString("nb-NO", {
				day: "2-digit",
				month: "2-digit",
				year: "numeric",
				timeZone: "Europe/Oslo",
			});
}

function eventDate(event: SecurityEvent): string {
	const start = new Date(event.startDate);
	const end = new Date(event.endDate);
	if (
		event.allDay &&
		event.endDate !== event.startDate &&
		!Number.isNaN(end.getTime())
	) {
		const startDay = start.toLocaleDateString("nb-NO", {
			day: "2-digit",
			month: "2-digit",
			timeZone: "UTC",
		});
		return `${startDay}–${formatDate(event.endDate)}`;
	}
	const date = formatDate(event.startDate);
	if (event.allDay) return date;
	return `${date} ${start.toLocaleTimeString("nb-NO", {
		hour: "2-digit",
		minute: "2-digit",
		timeZone: "Europe/Oslo",
	})}`;
}

function eventFileName(title: string): string {
	const slug = title
		.toLocaleLowerCase("en")
		.replace(/[\s-]+/g, "_")
		.replace(/[^a-z0-9_]/g, "")
		.replace(/_+/g, "_")
		.replace(/^_|_$/g, "");
	return `${slug || hackerCopy.events.unknownFile}.evt`;
}

function TerminalPanel({
	title,
	highlight = false,
	children,
}: {
	title: string;
	highlight?: boolean;
	children: React.ReactNode;
}) {
	return (
		<section
			className={`hackerPanel${highlight ? " hackerPanel--highlight" : ""}`}
		>
			<div className="hackerPanel__titlebar">
				<span className="hackerPanel__dot hackerPanel__dot--red" aria-hidden />
				<span
					className="hackerPanel__dot hackerPanel__dot--yellow"
					aria-hidden
				/>
				<span
					className="hackerPanel__dot hackerPanel__dot--green"
					aria-hidden
				/>
				<span className="hackerPanel__title">{title}</span>
			</div>
			<div className="hackerPanel__body">{children}</div>
		</section>
	);
}

function EventRows({
	events,
	past = false,
}: {
	events: SecurityEvent[];
	past?: boolean;
}) {
	return (
		<div className="hackerEventRows">
			{events.length === 0 ? (
				<span className="hackerEmpty">
					{past ? hackerCopy.events.pastEmpty : hackerCopy.events.upcomingEmpty}
				</span>
			) : (
				events.map((event) => (
					<Link
						key={event.id}
						className={`hackerEvent${past ? " hackerEvent--past" : ""}`}
						href={event.link || "/events"}
						title={event.name}
						aria-label={`${event.name}${hackerCopy.labels.eventLinkSuffix}${eventDate(event)}`}
					>
						<span className="hackerEvent__file">
							{eventFileName(event.name)}
						</span>
						<span className="hackerEvent__type">
							{event.type === "meetup"
								? hackerCopy.labels.meetup
								: hackerCopy.labels.event}
						</span>
						<span className="hackerEvent__date">
							{past
								? hackerCopy.events.pastPermissions
								: hackerCopy.events.upcomingPermissions}{" "}
							{eventDate(event)}
						</span>
					</Link>
				))
			)}
		</div>
	);
}

function OpsListing({
	events,
	failed,
	loading,
}: {
	events: SecurityEvent[];
	failed: boolean;
	loading: boolean;
}) {
	const upcoming = getUpcomingEvents(events);
	const past = getPastEvents(events);
	const archived = past.slice(0, 5);
	return (
		<TerminalPanel title={hackerCopy.events.title}>
			{loading ? (
				<div className="hackerEmpty" role="status">
					{hackerCopy.labels.loadingEvents}
				</div>
			) : failed ? (
				<div className="hackerEmpty" role="alert">
					{hackerCopy.events.unavailable}
				</div>
			) : (
				<>
					<div className="hackerCommand">
						<span>{hackerCopy.events.upcomingCommand}</span>
						<span>total {upcoming.length}</span>
					</div>
					<EventRows events={upcoming} />
					<div className="hackerCommand hackerCommand--archive">
						<span>{hackerCopy.events.pastCommand}</span>
						<span>total {past.length}</span>
					</div>
					<EventRows events={archived} past />
				</>
			)}
		</TerminalPanel>
	);
}

function PersonalProgress({ score }: { score: ParticipantSeasonScore }) {
	const progressLabelId = useId();
	const currentIndex = levelThresholds.findIndex(
		(item) => item.level === score.level,
	);
	const current = levelThresholds[currentIndex];
	if (!current) return null;
	const next = levelThresholds[currentIndex + 1];
	const nextPoints = next ? next.points - score.points : 0;
	const span = next ? next.points - current.points : 1;
	const progress = next
		? Math.min(100, Math.max(0, ((score.points - current.points) / span) * 100))
		: 100;
	const filledCells = Math.round(progress / 2);
	const bar = `[${"█".repeat(filledCells)}${"░".repeat(50 - filledCells)}] ${Math.round(progress)}%`;
	const positionCopy =
		score.rank && score.rank <= 3
			? hackerCopy.rankJokes[score.rank - 1]
			: hackerCopy.rankJokes[3];

	return (
		<section className="hackerStats">
			<div className="hackerCommand">{hackerCopy.score.command}</div>
			<div className="hackerStats__grid">
				<div className="hackerStat">
					<span>{hackerCopy.score.points}</span>
					<strong>
						<span role="img" aria-label={`${score.points} points`}>
							0x{score.points.toString(16).toUpperCase().padStart(2, "0")}
						</span>
					</strong>
					<small>
						{hackerCopy.score.humanPointsPrefix}
						{score.points}
						{hackerCopy.score.humanPointsSuffix}
					</small>
				</div>
				<div className="hackerStat">
					<span>{hackerCopy.score.level}</span>
					<strong>{current.label}</strong>
					<small>a.k.a. {score.level}</small>
				</div>
				<div className="hackerStat">
					<span>{hackerCopy.score.position}</span>
					<strong className="hackerStat__rank">#{score.rank ?? "—"}</strong>
					<small>{positionCopy}</small>
				</div>
			</div>
			{next ? (
				<div className="hackerProgress">
					<div id={progressLabelId}>
						{hackerCopy.score.nextLevelPrefix}
						{next.label}
						{hackerCopy.score.nextLevelSuffix}{" "}
						<span>
							{Math.max(0, nextPoints)}
							{hackerCopy.score.remainingSuffix}
						</span>
					</div>
					<div
						className="hackerProgress__bar"
						role="progressbar"
						aria-labelledby={progressLabelId}
						aria-valuenow={Math.round(progress)}
						aria-valuemin={0}
						aria-valuemax={100}
					>
						{bar}
					</div>
				</div>
			) : (
				<div className="hackerProgress">{hackerCopy.score.topLevel}</div>
			)}
		</section>
	);
}

function ThreatBar({ points, max }: { points: number; max: number }) {
	const cells = Math.min(
		20,
		max > 0 ? Math.max(1, Math.round((points / max) * 20)) : 0,
	);
	return (
		<span
			role="img"
			aria-label={`${hackerCopy.leaderboard.threatPrefix}${cells}${hackerCopy.leaderboard.threatOf}`}
			className="hackerThreat"
		>
			<span aria-hidden>{`${"▮".repeat(cells)}${"▯".repeat(20 - cells)}`}</span>
		</span>
	);
}

function MostWanted({
	entries,
	score,
}: {
	entries: LeaderboardEntry[];
	score: ParticipantSeasonScore | null;
}) {
	const max = Math.max(...entries.map((entry) => entry.points), 0);
	return (
		<section className="hackerStats hackerLeaderboard">
			<div className="hackerCommand">
				<span>{hackerCopy.leaderboard.command}</span>
				<span>
					{hackerCopy.leaderboard.seasonLabel}{" "}
					{score
						? new Date(score.season.startsOn).getFullYear()
						: new Date().getFullYear()}{" "}
					{hackerCopy.onlineMarker} {entries.length}{" "}
					{hackerCopy.leaderboard.operativeCount}
				</span>
			</div>
			<div className="hackerLeaderboard__scroll">
				<div className="hackerLeaderboard__grid">
					<span className="hackerLeaderboard__head">
						{hackerCopy.leaderboard.rank}
					</span>
					<span className="hackerLeaderboard__head">
						{hackerCopy.leaderboard.operative}
					</span>
					<span className="hackerLeaderboard__head">
						{hackerCopy.leaderboard.threat}
					</span>
					<span className="hackerLeaderboard__head">
						{hackerCopy.leaderboard.xp}
					</span>
					<span className="hackerLeaderboard__head">
						{hackerCopy.leaderboard.class}
					</span>
					{entries.map((entry) => {
						const isMe = entry.isCurrentUser;
						return (
							<div
								key={`${entry.rank}-${entry.fullName}`}
								className={`hackerLeaderboard__row${isMe ? " hackerLeaderboard__row--me" : ""}`}
							>
								<span className="hackerLeaderboard__rank">
									{String(entry.rank).padStart(2, "0")}
								</span>
								<span className="hackerLeaderboard__operative">
									<strong>{hackerAlias(entry.fullName, entry.fullName)}</strong>
									<small>{entry.fullName}</small>
								</span>
								<ThreatBar points={entry.points} max={max} />
								<span>{entry.points}</span>
								<span>
									{levelThresholds.find((item) => item.level === entry.level)
										?.label ?? entry.level}
								</span>
							</div>
						);
					})}
				</div>
			</div>
			<div className="hackerLeaderboard__note">
				{hackerCopy.leaderboard.note}
			</div>
		</section>
	);
}

function HackOverlay({ onClose }: { onClose: () => void }) {
	const [visibleLines, setVisibleLines] = useState<string[]>([]);
	const [complete, setComplete] = useState(false);
	const [reducedMotion, setReducedMotion] = useState(false);
	const dialogRef = useRef<HTMLDivElement>(null);
	const closeRef = useRef<HTMLButtonElement>(null);

	useEffect(() => {
		setReducedMotion(
			window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false,
		);
		dialogRef.current?.focus();
		if (
			window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ??
			false
		) {
			setVisibleLines([...hackerCopy.hackLines]);
			setComplete(true);
			return;
		}
		let index = 0;
		const timer = window.setInterval(() => {
			index += 1;
			setVisibleLines(hackerCopy.hackLines.slice(0, index));
			if (index >= hackerCopy.hackLines.length) {
				window.clearInterval(timer);
				setComplete(true);
			}
		}, 380);
		return () => window.clearInterval(timer);
	}, []);

	const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
		if (event.key === "Escape" && complete) {
			event.stopPropagation();
			onClose();
		} else if (event.key === "Tab") {
			if (complete && closeRef.current) {
				event.preventDefault();
				closeRef.current.focus();
			} else {
				event.preventDefault();
				dialogRef.current?.focus();
			}
		}
	};

	return (
		<div
			className={`hackerHackOverlay${reducedMotion ? " hackerHackOverlay--instant" : ""}`}
		>
			<button
				className="hackerHackOverlay__backdrop"
				type="button"
				aria-label={hackerCopy.hack.closeLabel}
				disabled={!complete}
				tabIndex={-1}
				onClick={onClose}
			/>
			<div
				ref={dialogRef}
				className="hackerHackOverlay__box"
				role="dialog"
				aria-modal="true"
				aria-label={hackerCopy.hack.label}
				tabIndex={-1}
				onClick={(event) => event.stopPropagation()}
				onKeyDown={handleKeyDown}
			>
				{visibleLines.map((line) => (
					<div key={line}>{line}</div>
				))}
				{complete && (
					<>
						<div className="hackerHackOverlay__success">
							{hackerCopy.hack.success}
						</div>
						<div className="hackerHackOverlay__message">
							{hackerCopy.hack.message}
						</div>
						<button ref={closeRef} type="button" onClick={onClose}>
							{hackerCopy.hack.close}
						</button>
					</>
				)}
			</div>
		</div>
	);
}

export function HackerOverview({ info }: { info: Me }) {
	const [me, setMe] = useState(info);
	const [events, setEvents] = useState<SecurityEvent[]>([]);
	const [eventsLoading, setEventsLoading] = useState(true);
	const [eventsFailed, setEventsFailed] = useState(false);
	const [participant, setParticipant] = useState<ProgramParticipant | null>(
		null,
	);
	const [score, setScore] = useState<ParticipantSeasonScore | null>(null);
	const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null);
	const [scoreFailed, setScoreFailed] = useState(false);
	const [leaderboardFailed, setLeaderboardFailed] = useState(false);
	const [hackOpen, setHackOpen] = useState(false);
	const hackButtonRef = useRef<HTMLButtonElement>(null);
	const operative = me.displayName || hackerCopy.dossier.realNameUnavailable;
	const alias = hackerAlias(me.displayName, me.username);
	const firstName = (me.displayName || me.username.split("@")[0] || "operative")
		.split(/\s+/)[0]
		.toLocaleLowerCase();
	const specialty =
		hackerCopy.missions[
			stableIndex(me.username || operative, hackerCopy.missions.length)
		];
	const level = score?.level ?? "Novice";
	const clearance =
		level === "Novice"
			? hackerCopy.clearance.novice
			: level === "Apprentice"
				? hackerCopy.clearance.apprentice
				: level === "Expert"
					? hackerCopy.clearance.expert
					: hackerCopy.clearance.adept;
	const recruited = participant
		? formatDate(participant.joinedAt)
		: me.isParticipant
			? hackerCopy.dossier.unknownRecruitment
			: hackerCopy.dossier.pendingRecruitment;
	const isActive = me.isParticipant && me.isActive;
	const showPersonal = isActive;
	const showLeaderboard = me.isAdmin || isActive;

	useEffect(() => {
		let current = true;
		Apies.fetchEvents()
			.then((result) => {
				if (!current) return;
				setEvents(result);
				setEventsLoading(false);
			})
			.catch((error) => {
				console.error("Failed to fetch events:", error);
				if (current) {
					setEventsFailed(true);
					setEventsLoading(false);
				}
			});
		if (me.isParticipant) {
			Apies.fetchMembership()
				.then((result) => {
					if (current) setParticipant(result);
				})
				.catch((error) =>
					console.error("Failed to fetch participant details:", error),
				);
		}
		if (showPersonal) {
			Apies.getParticipantSeasonScore()
				.then((result) => {
					if (!current) return;
					setScore(result);
					setScoreFailed(result === null);
				})
				.catch((error) => {
					console.error("Failed to fetch current-season score:", error);
					if (current) setScoreFailed(true);
				});
		}
		if (showLeaderboard) {
			Apies.getLeaderboard()
				.then((result) => {
					if (!current) return;
					setEntries(result);
					setLeaderboardFailed(result === null);
				})
				.catch((error) => {
					console.error("Failed to fetch leaderboard:", error);
					if (current) setLeaderboardFailed(true);
				});
		}
		return () => {
			current = false;
		};
	}, [me.isParticipant, showPersonal, showLeaderboard]);

	const closeHack = () => {
		setHackOpen(false);
		window.requestAnimationFrame(() => hackButtonRef.current?.focus());
	};
	return (
		<main className="hackerOverview">
			<header className="hackerOverview__header">
				<div className="hackerOverview__welcome">
					<div className="hackerOverview__prompt">
						{hackerCopy.header.promptPrefix}
						{firstName}
					</div>
					<h1>
						{hackerCopy.header.title}
						<span className="hackerCursor" aria-hidden="true">
							█
						</span>
					</h1>
					<p>{hackerCopy.subtitle}</p>
				</div>
				<button
					ref={hackButtonRef}
					className="hackerMainframeButton"
					type="button"
					onClick={() => setHackOpen(true)}
				>
					{hackerCopy.header.button}
				</button>
			</header>

			<div className="hackerOverview__top">
				<TerminalPanel title={hackerCopy.dossier.title} highlight>
					<div className="hackerDossier">
						<div className="hackerDossier__badges">
							<span
								className={
									isActive
										? "hackerBadge hackerBadge--active"
										: "hackerBadge hackerBadge--pending"
								}
							>
								{isActive
									? hackerCopy.dossier.active
									: hackerCopy.dossier.pending}
							</span>
							<span className="hackerBadge hackerBadge--secret">
								{hackerCopy.dossier.secret}
							</span>
						</div>
						<h2>{hackerCopy.dossier.heading}</h2>
						<div className="hackerDossier__facts">
							<span>{hackerCopy.dossier.fields[0]}</span>
							<strong>{alias}</strong>
							<span>{hackerCopy.dossier.fields[1]}</span>
							<strong>
								{operative} <small>{hackerCopy.dossier.allegedly}</small>
							</strong>
							<span>{hackerCopy.dossier.fields[2]}</span>
							<strong>{recruited}</strong>
							<span>{hackerCopy.dossier.fields[3]}</span>
							<strong>{clearance}</strong>
							<span>{hackerCopy.dossier.fields[4]}</span>
							<strong>{specialty}</strong>
						</div>
						<div className="hackerMission">
							<div>&gt; {hackerCopy.dossier.mission}</div>
							<div>&gt; {hackerCopy.dossier.selfDestruct}</div>
						</div>
						<div className="hackerMembership">
							<MembershipView
								me={me}
								onMembershipChanged={(updated) => {
									setMe(updated);
									if (!updated.isActive)
										setParticipant((currentParticipant) =>
											currentParticipant
												? {
														...currentParticipant,
														active: false,
														status: "LEFT",
													}
												: null,
										);
								}}
							/>
						</div>
					</div>
				</TerminalPanel>
				<OpsListing
					events={events}
					failed={eventsFailed}
					loading={eventsLoading}
				/>
			</div>

			{showPersonal &&
				(score ? (
					<PersonalProgress score={score} />
				) : scoreFailed ? (
					<section className="hackerStats" role="alert">
						{hackerCopy.score.unavailable}
					</section>
				) : (
					<section className="hackerStats" role="status">
						{hackerCopy.score.loading}
					</section>
				))}
			{showLeaderboard &&
				(entries ? (
					<MostWanted entries={entries} score={score} />
				) : leaderboardFailed ? (
					<section className="hackerStats" role="alert">
						{hackerCopy.leaderboard.unavailable}
					</section>
				) : (
					<section className="hackerStats" role="status">
						{hackerCopy.leaderboard.loading}
					</section>
				))}
			<footer className="hackerOverview__footer">{hackerCopy.footer}</footer>
			{hackOpen && <HackOverlay onClose={closeHack} />}
		</main>
	);
}

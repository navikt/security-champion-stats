"use client";

import { useEffect, useState } from "react";
import type {
	HistoryEntry,
	Me,
	ParticipantSeasonScore,
	SecurityEvent,
} from "../utils/Variables";
import "../style/home/HomeView.css";
import { BodyShort, Heading } from "@navikt/ds-react";
import { useTheme } from "next-themes";
import { Apies } from "@/app/shared/hooks/Apies";
import { HackerOverview } from "./HackerOverview";
import { MembershipView } from "./member/components/MembershipView";
import { ScoringOverview } from "./member/components/ScoringOverview";
import { OverviewEvents } from "./events/OverviewEvents";

function View({ me }: { me: Me }) {
	const [userData, setUserData] = useState(me);
	const [events, updateEvents] = useState<SecurityEvent[]>([]);
	const [score, setScore] = useState<ParticipantSeasonScore | null>(null);
	const [scoreLoading, setScoreLoading] = useState(me.isParticipant && me.isActive);
	const [activities, setActivities] = useState<HistoryEntry[]>([]);
	const [activitiesLoading, setActivitiesLoading] = useState(me.isParticipant);
	const [eventsLoading, setEventsLoading] = useState(true);

	useEffect(() => {
		let current = true;
		Apies.fetchEvents()
			.then((res) => {
				if (current) updateEvents(res);
			})
			.catch((error) => {
				console.error("Failed to load overview events:", error);
			})
			.finally(() => {
				if (current) setEventsLoading(false);
			});
		return () => {
			current = false;
		};
	}, []);

	useEffect(() => {
		if (!userData.isParticipant) {
			setActivities([]);
			setActivitiesLoading(false);
			setScore(null);
			setScoreLoading(false);
			return;
		}
		let current = true;
		setActivitiesLoading(true);
		setScoreLoading(userData.isActive);
		const historyRequest = Apies.getHistory(false, "", null);
		const scoreRequest = userData.isActive
			? Apies.getParticipantSeasonScore()
			: Promise.resolve(null);
		Promise.allSettled([historyRequest, scoreRequest])
			.then(([historyResult, scoreResult]) => {
				if (!current) return;
				if (historyResult.status === "fulfilled") {
					setActivities(historyResult.value.entries);
				} else {
					console.error("Failed to load recent activity:", historyResult.reason);
				}
				if (scoreResult.status === "fulfilled") setScore(scoreResult.value);
				else console.error("Failed to load overview season score:", scoreResult.reason);
			})
			.finally(() => {
				if (!current) return;
				setActivitiesLoading(false);
				setScoreLoading(false);
			});
		return () => {
			current = false;
		};
	}, [userData.isParticipant, userData.isActive]);

	return (
		<main className="hubRedesign homeView">
			<header className="hubRedesign__header">
				<Heading level="1" size={"xlarge"}>
					Security Champion Hub
				</Heading>
				<BodyShort className={"homeView__subtitle"}>
					Here's what's happening in the Security Champion program.
				</BodyShort>
			</header>

			<MembershipView
				me={me}
				onMembershipChanged={setUserData}
				overview={{
					score: userData.isActive ? score : null,
					scoreLoading,
					activities,
					activitiesLoading,
				}}
			/>
			<div className="hubRedesign__grid overviewLowerRow">
				<OverviewEvents
					events={events}
					seasonStartsOn={score?.season.startsOn ?? null}
					loading={eventsLoading}
				/>
				<ScoringOverview
					key={`${userData.isParticipant}:${userData.isActive}`}
					showPersonalProgress={false}
					showLeaderboard={
						userData.isAdmin || (userData.isParticipant && userData.isActive)
					}
					currentUserName={me.displayName}
				/>
			</div>
		</main>
	);
}

export function MainView({ info }: { info: Me }) {
	const { theme } = useTheme();
	if (theme === "hacker") return <HackerOverview info={info} />;
	return <View me={info} />;
}

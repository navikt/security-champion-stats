import "../../style/events/EventsView.css";
import { SecurityEvent } from "@/app/utils/Variables";
import { Heading } from "@navikt/ds-react";
import { EventListItem } from "@/app/view/events/EventListItem";

interface EventListProps {
	title: string;
	events: SecurityEvent[];
	emptyMessage?: string;
	muted?: boolean;
	limit?: number;
	scrollAfter?: number;
}

export function EventList({
	title,
	events,
	emptyMessage,
	muted = false,
	limit,
	scrollAfter,
}: EventListProps) {
	const visibleEvents = limit !== undefined ? events.slice(0, limit) : events;
	const isScrollable =
		scrollAfter !== undefined && visibleEvents.length > scrollAfter;

	return (
		<section className={"eventSection"}>
			<div className={"eventSection__header"}>
				<Heading level={"2"} size={"medium"}>
					{title}
				</Heading>

				{visibleEvents.length > 0 && (
					<span className={"eventSection__count"}>{visibleEvents.length}</span>
				)}
			</div>
			{visibleEvents.length === 0 ? (
				<div className={"eventSection__empty"}>{emptyMessage}</div>
			) : (
				<div
					className={["eventList", isScrollable ? "eventList--scrollable" : ""]
						.filter(Boolean)
						.join(" ")}
				>
					{visibleEvents.map((event) => (
						<EventListItem event={event} muted={muted} key={event.id} />
					))}
				</div>
			)}
		</section>
	);
}

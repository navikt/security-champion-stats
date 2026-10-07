import Link from "next/link";
import { BodyShort, Box, Heading, HStack, Tag, ToggleGroup } from "@navikt/ds-react";
import { useMemo, useState } from "react";
import type { SecurityEvent } from "@/app/utils/Variables";
import { formatEventDate, getPastEvents, getUpcomingEvents } from "@/app/utils/eventUtils";

type EventTab = "upcoming" | "past";

function eventDateTile(event: SecurityEvent) {
	const timeZone = event.allDay ? "UTC" : "Europe/Oslo";
	const date = new Date(event.startDate);
	return {
		day: new Intl.DateTimeFormat(undefined, {
			day: "2-digit",
			timeZone,
		}).format(date),
		month: new Intl.DateTimeFormat(undefined, {
			month: "short",
			timeZone,
		}).format(date),
	};
}

function eventWhen(event: SecurityEvent) {
	if (event.allDay) return formatEventDate(event);
	const date = formatEventDate(event);
	const time = new Date(event.startDate).toLocaleTimeString(undefined, {
		hour: "2-digit",
		minute: "2-digit",
		timeZone: "Europe/Oslo",
	});
	return `${date} · ${time}`;
}

export function OverviewEvents({
	events,
	seasonStartsOn,
	loading,
}: {
	events: SecurityEvent[];
	seasonStartsOn: string | null;
	loading: boolean;
}) {
	const [tab, setTab] = useState<EventTab>("upcoming");
	const upcoming = useMemo(() => getUpcomingEvents(events), [events]);
	const past = useMemo(() => {
		const seasonStart = seasonStartsOn ? new Date(seasonStartsOn).getTime() : null;
		return getPastEvents(events).filter(
			(event) =>
				seasonStart === null || new Date(event.startDate).getTime() >= seasonStart,
		);
	}, [events, seasonStartsOn]);
	const displayed = tab === "upcoming" ? upcoming : past.slice(0, 6);

	return (
		<Box
			as="section"
			aria-labelledby="overview-events-heading"
			className="hubRedesign__card overviewEvents"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="8"
			padding="space-20"
		>
			<div className="hubRedesign__cardHeader">
				<Heading level="2" size="medium" id="overview-events-heading">Events</Heading>
				<ToggleGroup
					label="Show events"
					value={tab}
					onChange={(value) => {
						if (value === "upcoming" || value === "past") setTab(value);
					}}
					size="small"
					data-color="neutral"
				>
					<ToggleGroup.Item value="upcoming" label={`Upcoming ${upcoming.length}`} />
					<ToggleGroup.Item value="past" label={`Past ${past.length}`} />
				</ToggleGroup>
			</div>

			{loading ? (
				<BodyShort role="status">Loading events…</BodyShort>
			) : displayed.length === 0 ? (
				<BodyShort className="hubRedesign__muted">
					{tab === "upcoming" ? "No upcoming events." : "No past events this season."}
				</BodyShort>
			) : (
				<ul className="hubRedesign__dividerList overviewEvents__list">
					{displayed.map((event) => {
						const tile = eventDateTile(event);
						return (
							<li key={event.id}>
								<Link href={event.link || "/events"} className="overviewEvents__row">
									<span className="overviewEvents__date">
										<strong>{tile.day}</strong>
										<span>{tile.month}</span>
									</span>
									<span className="overviewEvents__content">
										<strong className="overviewEvents__title">{event.name}</strong>
										<span className="hubRedesign__muted">{eventWhen(event)}</span>
									</span>
									<Tag size="xsmall" variant="outline" data-color="neutral">
										{event.type}
									</Tag>
								</Link>
							</li>
						);
					})}
				</ul>
			)}
			{tab === "past" && past.length > 6 && (
				<HStack justify="center">
					<Link href="/events" className="hubRedesign__buttonLink">
						Show all past events
					</Link>
				</HStack>
			)}
		</Box>
	);
}

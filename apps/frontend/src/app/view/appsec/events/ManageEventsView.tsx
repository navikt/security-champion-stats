"use client";

import { SecurityEvent } from "@/app/utils/Variables";
import { useState } from "react";
import { BodyShort, Button, Heading, Select, VStack } from "@navikt/ds-react";
import { EventList } from "@/app/view/events/EventList";
import { AddEventModal } from "@/app/view/appsec/events/AddEventModal";
import { Apies } from "@/app/shared/hooks/Apies";
import { EventRemindersPanel } from "./EventRemindersPanel";
import { getUpcomingEvents } from "@/app/utils/eventUtils";

interface ManageEventsViewProps {
	events: SecurityEvent[];
}

export function ManageEventsView({ events }: ManageEventsViewProps) {
	const [eventsList, setEventsList] = useState<SecurityEvent[]>(events);
	const [mods, setMods] = useState(false);
	const [create, setCreate] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [refreshError, setRefreshError] = useState<string | null>(null);
	const [formVersion, setFormVersion] = useState(0);
	const [reminderEventId, setReminderEventId] = useState("");
	const reminderEvents = getUpcomingEvents(eventsList).filter(
		(event) => event.signupSupported,
	);
	const reminderEvent = reminderEvents.find(
		(event) => event.id === reminderEventId,
	);
	const handleCreate = async (event: Omit<SecurityEvent, "id">) => {
		if (create) return;
		setCreate(true);
		setError(null);
		setRefreshError(null);
		try {
			const created = await Apies.createEvent({
				...event,
				id: crypto.randomUUID(),
				amountOfPeopleJoined: 0,
			});
			setEventsList((current) => [created, ...current]);
			setMods(false);
			setFormVersion((current) => current + 1);
			try {
				const refreshed = await Apies.fetchEvents();
				if (!refreshed.some((item) => item.id === created.id)) {
					throw new Error(
						"The refreshed catalog did not include the saved event",
					);
				}
				setEventsList(refreshed);
			} catch (error) {
				console.error("Event saved but catalog refresh failed:", error);
				setRefreshError(
					"Event saved, but the event list could not be refreshed. Reload the page to update playbook matches.",
				);
			}
		} catch (error) {
			console.error("Event creation failed:", error);
			setError(
				error instanceof Error &&
					!(error instanceof TypeError) &&
					!(error instanceof SyntaxError)
					? error.message
					: "We couldn't create the event. Try again.",
			);
		} finally {
			setCreate(false);
		}
	};

	return (
		<VStack gap={"space-24"}>
			<VStack gap={"space-4"}>
				<Heading level={"1"} size={"xlarge"}>
					Manage events
				</Heading>
				<BodyShort>
					Add and review meetings, workshops and external events.
				</BodyShort>
			</VStack>

			<Button
				onClick={() => {
					setError(null);
					setMods(true);
				}}
				style={{ alignSelf: "flex-start" }}
			>
				Add event
			</Button>

			{refreshError && <BodyShort role="alert">{refreshError}</BodyShort>}

			<VStack gap="space-24">
				<Heading level="2" size="medium">
					Delta signup reminders
				</Heading>
				<BodyShort>
					Preview and confirm Slack DMs to active participants who have not
					signed up. Each participant receives at most one successfully
					delivered reminder per event.
				</BodyShort>
				{reminderEvents.length > 0 && (
					<Select
						label="Event to remind participants about"
						value={reminderEvent?.id ?? ""}
						onChange={(event) => setReminderEventId(event.target.value)}
					>
						<option value="">Select an upcoming event</option>
						{reminderEvents.map((event) => (
							<option key={event.id} value={event.id}>
								{event.name} - {new Date(event.startDate).toLocaleString()}
							</option>
						))}
					</Select>
				)}
				{reminderEvent && (
					<EventRemindersPanel key={reminderEvent.id} event={reminderEvent} />
				)}
				{reminderEvents.length === 0 && (
					<BodyShort>
						No upcoming Delta events available for reminders.
					</BodyShort>
				)}
			</VStack>

			<EventList
				title="All events"
				events={eventsList}
				emptyMessage="No events yet. Add one to get started."
			/>

			<AddEventModal
				key={formVersion}
				open={mods}
				onClose={() => {
					if (!create) setMods(false);
				}}
				onCreate={handleCreate}
				loading={create}
				error={error}
			/>
		</VStack>
	);
}

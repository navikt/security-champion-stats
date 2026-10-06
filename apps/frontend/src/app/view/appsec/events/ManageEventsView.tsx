"use client";

import { SecurityEvent } from "@/app/utils/Variables";
import { useState } from "react";
import { BodyShort, Button, Heading, VStack } from "@navikt/ds-react";
import { EventList } from "@/app/view/events/EventList";
import { AddEventModal } from "@/app/view/appsec/events/AddEventModal";
import { Apies } from "@/app/shared/hooks/Apies";

interface ManageEventsViewProps {
	events: SecurityEvent[];
}

export function ManageEventsView({ events }: ManageEventsViewProps) {
	const [eventsList, setEventsList] = useState<SecurityEvent[]>(events);
	const [mods, setMods] = useState(false);
	const [create, setCreate] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [formVersion, setFormVersion] = useState(0);
	const handleCreate = async (event: Omit<SecurityEvent, "id">) => {
		if (create) return;
		setCreate(true);
		setError(null);
		try {
			const created = await Apies.createEvent({
				...event,
				id: crypto.randomUUID(),
				amountOfPeopleJoined: 0,
			});
			setEventsList((current) => [created, ...current]);
			setMods(false);
			setFormVersion((current) => current + 1);
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

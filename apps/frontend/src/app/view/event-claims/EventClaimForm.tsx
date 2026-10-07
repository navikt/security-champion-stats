"use client";

import { useState } from "react";
import {
	BodyShort,
	Button,
	ErrorMessage,
	Heading,
	Select,
	Textarea,
	TextField,
	VStack,
} from "@navikt/ds-react";
import type { SecurityEvent } from "@/app/utils/Variables";
import {
	EventClaimsApi,
	type EventClaim,
	type EventClaimOverview,
	type EventClaimRequest,
} from "./EventClaimsApi";

function localDateTime(instant: string): string {
	const date = new Date(instant);
	return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
		.toISOString()
		.slice(0, 16);
}

export function EventClaimForm({
	overview,
	events,
	claim,
	onSaved,
	onCancel,
}: {
	overview: EventClaimOverview;
	events: SecurityEvent[];
	claim?: EventClaim;
	onSaved: (claim: EventClaim) => void;
	onCancel: () => void;
}) {
	const [form, setForm] = useState<EventClaimRequest>(
		claim
			? {
					...claim,
					expectedVersion: claim.version,
					contributors: claim.contributors.map(
						({ participantId, contribution }) => ({
							participantId,
							contribution,
						}),
					),
				}
			: {
					name: "",
					description: "",
					startDate: "",
					endDate: "",
					location: "",
					type: "meetup",
					externalEvent: false,
					links: [],
					invitationEvidence: "",
					eventId: null,
					contributors: [
						{
							participantId: overview.currentParticipantId ?? "",
							contribution: "",
						},
					],
				},
	);
	const [start, setStart] = useState(
		claim ? localDateTime(claim.startDate) : "",
	);
	const [end, setEnd] = useState(claim ? localDateTime(claim.endDate) : "");
	const [links, setLinks] = useState(form.links.join("\n"));
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);
	const existingEvents = events.filter(
		(event) =>
			/^[0-9a-f-]{36}$/i.test(event.id) &&
			new Date(event.endDate) <= new Date(),
	);
	const update = <K extends keyof EventClaimRequest>(
		key: K,
		value: EventClaimRequest[K],
	) => setForm((current) => ({ ...current, [key]: value }));
	const linked = !!form.eventId;

	async function submit(event: React.FormEvent) {
		event.preventDefault();
		if (saving) return;
		setError(null);
		if (
			form.contributors.some(
				(contributor) =>
					!contributor.participantId || !contributor.contribution.trim(),
			)
		) {
			setError("Choose each contributor and describe their contribution.");
			return;
		}
		if (
			form.contributors.some(
				(contributor) => contributor.contribution.length > 2000,
			) ||
			form.description.length > 5000 ||
			form.invitationEvidence.length > 3000
		) {
			setError("Shorten the descriptions to the indicated character limits.");
			return;
		}
		setSaving(true);
		try {
			const saved = await EventClaimsApi.save(
				{
					...form,
					startDate: claim
						? claim.startDate
						: linked
							? form.startDate
							: new Date(start).toISOString(),
					endDate: claim
						? claim.endDate
						: linked
							? form.endDate
							: new Date(end).toISOString(),
					links: links
						.split("\n")
						.map((link) => link.trim())
						.filter(Boolean),
				},
				claim?.id,
			);
			onSaved(saved);
		} catch (error) {
			console.error("Event claim submission failed", error);
			setError(
				error instanceof Error
					? error.message
					: "The claim could not be submitted.",
			);
		} finally {
			setSaving(false);
		}
	}

	return (
		<form onSubmit={submit}>
			<VStack gap="space-16">
				<Heading size="medium" level="2">
					{claim ? "Edit and resubmit claim" : "Claim event credit"}
				</Heading>
				<BodyShort>
					Describe the security content delivered and how you invited the
					network beforehand. Each approved organizer or presenter receives{" "}
					{overview.points} points in the event&apos;s season. New claims must
					be for this season (from {overview.seasonStartsOn}), after enrollment.
				</BodyShort>
				{error && <ErrorMessage role="alert">{error}</ErrorMessage>}
				{!claim && (
					<Select
						label="Existing event (optional)"
						value={form.eventId ?? ""}
						onChange={(event) => {
							const selected = existingEvents.find(
								(item) => item.id === event.target.value,
							);
							update("eventId", selected?.id ?? null);
							if (selected) {
								setForm((current) => ({
									...current,
									eventId: selected.id,
									name: selected.name,
									description: selected.description ?? "",
									location: selected.location ?? "",
									type: selected.type,
									externalEvent: selected.externalEvent,
									startDate: selected.startDate,
									endDate: selected.endDate,
								}));
								setStart(localDateTime(selected.startDate));
								setEnd(localDateTime(selected.endDate));
								setLinks(selected.link ?? "");
							}
						}}
					>
						<option value="">Create a new event with this claim</option>
						{existingEvents.map((event) => (
							<option key={event.id} value={event.id}>
								{event.name}
							</option>
						))}
					</Select>
				)}
				<TextField
					label="Event name"
					required
					maxLength={100}
					value={form.name}
					readOnly={linked}
					onChange={(event) => update("name", event.target.value)}
				/>
				<Textarea
					label="Security content delivered"
					required
					maxLength={5000}
					value={form.description}
					onChange={(event) => update("description", event.target.value)}
				/>
				<label>
					Start date and time (your local time)
					<input
						type="datetime-local"
						required
						value={start}
						readOnly={!!claim || linked}
						onChange={(event) => setStart(event.target.value)}
					/>
				</label>
				<label>
					End date and time (your local time)
					<input
						type="datetime-local"
						required
						value={end}
						readOnly={!!claim || linked}
						onChange={(event) => setEnd(event.target.value)}
					/>
				</label>
				<TextField
					label="Location"
					maxLength={100}
					value={form.location}
					readOnly={linked}
					onChange={(event) => update("location", event.target.value)}
				/>
				<Select
					label="Event type"
					value={form.type}
					readOnly={linked}
					onChange={(event) => update("type", event.target.value)}
				>
					<option value="meetup">Meeting or presentation</option>
					<option value="workshop">Workshop</option>
				</Select>
				<Select
					label="Internal or external event"
					value={form.externalEvent ? "external" : "internal"}
					readOnly={linked}
					onChange={(event) =>
						update("externalEvent", event.target.value === "external")
					}
				>
					<option value="internal">Nav-internal</option>
					<option value="external">External</option>
				</Select>
				<Textarea
					label="Event and resource links"
					required
					description="One HTTP or HTTPS link per line, up to 10. The first link will appear in Past events."
					value={links}
					onChange={(event) => setLinks(event.target.value)}
				/>
				<Textarea
					label="Advance invitation evidence"
					required
					maxLength={3000}
					description="Link to the network invitation, or explain when and where you informed the network and offered others to join. Only contributors and administrators see this evidence."
					value={form.invitationEvidence}
					onChange={(event) => update("invitationEvidence", event.target.value)}
				/>
				{form.contributors.map((contributor, index) => (
					<fieldset key={index}>
						<legend>Contributor {index + 1}</legend>
						<VStack gap="space-12">
							<Select
								label={`Participant ${index + 1}`}
								value={contributor.participantId}
								required
								onChange={(event) =>
									update(
										"contributors",
										form.contributors.map((item, position) =>
											position === index
												? { ...item, participantId: event.target.value }
												: item,
										),
									)
								}
							>
								<option value="">Choose a participant</option>
								{overview.participants
									.filter(
										(participant) =>
											participant.id === contributor.participantId ||
											!form.contributors.some(
												(item) => item.participantId === participant.id,
											),
									)
									.map((participant) => (
										<option key={participant.id} value={participant.id}>
											{participant.fullName || participant.id}
										</option>
									))}
							</Select>
							<Textarea
								label={`Substantive organizing or presenting contribution ${index + 1}`}
								required
								maxLength={2000}
								value={contributor.contribution}
								onChange={(event) =>
									update(
										"contributors",
										form.contributors.map((item, position) =>
											position === index
												? { ...item, contribution: event.target.value }
												: item,
										),
									)
								}
							/>
							{index > 0 && (
								<Button
									type="button"
									variant="tertiary"
									onClick={() =>
										update(
											"contributors",
											form.contributors.filter(
												(_, position) => position !== index,
											),
										)
									}
								>
									Remove contributor {index + 1}
								</Button>
							)}
						</VStack>
					</fieldset>
				))}
				<Button
					type="button"
					variant="secondary"
					disabled={form.contributors.length >= 20}
					onClick={() =>
						update("contributors", [
							...form.contributors,
							{ participantId: "", contribution: "" },
						])
					}
				>
					Add co-host or presenter
				</Button>
				<Button type="submit" loading={saving}>
					Submit for review
				</Button>
				<Button
					type="button"
					variant="tertiary"
					disabled={saving}
					onClick={onCancel}
				>
					Cancel
				</Button>
			</VStack>
		</form>
	);
}

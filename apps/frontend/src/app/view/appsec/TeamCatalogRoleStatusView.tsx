"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminProgramParticipant,
	TeamCatalogChampionStatusOverview,
} from "@/app/utils/Variables";
import { BodyShort, Heading, Table, VStack } from "@navikt/ds-react";
import { useEffect, useState } from "react";

export function TeamCatalogRoleStatusView() {
	const [participants, setParticipants] = useState<AdminProgramParticipant[]>([]);
	const [overview, setOverview] = useState<TeamCatalogChampionStatusOverview | null>(null);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		let mounted = true;
		Promise.all([
			Apies.getAdminParticipants(),
			Apies.getTeamCatalogChampionStatus(),
		]).then(([participantResult, statusResult]) => {
			if (!mounted) return;
			if (!participantResult) {
				setFailed(true);
				return;
			}
			setParticipants(participantResult);
			setOverview(statusResult);
		}).catch(() => {
			if (mounted) setFailed(true);
		});
		return () => {
			mounted = false;
		};
	}, []);

	if (failed) {
		return <BodyShort role="alert">We couldn't fetch Teamkatalogen role status. Try again later.</BodyShort>;
	}
	if (!overview) return <BodyShort>Loading Teamkatalogen role status…</BodyShort>;

	const statuses = new Map(overview.participants.map((participant) => [participant.participantId, participant.status]));
	const activeParticipants = participants.filter((participant) => participant.active);
	const missing = activeParticipants.filter((participant) => statuses.get(participant.id) === "ABSENT");
	const unknown = activeParticipants.filter((participant) => statuses.get(participant.id) !== "PRESENT" && statuses.get(participant.id) !== "ABSENT");

	return (
		<main className="hubRedesign">
			<VStack gap="space-24">
				<VStack gap="space-8">
					<Heading level="1" size="xlarge">Teamkatalogen role status</Heading>
					<BodyShort>
						Active participants with no Security Champion tag in any active team are listed as missing.
						Unknown means Teamkatalogen does not currently confirm a role status.
					</BodyShort>
				</VStack>
				<Heading level="2" size="large">Missing Security Champion tag ({missing.length})</Heading>
				{missing.length === 0 ? (
					<BodyShort>No active participants are confirmed missing the tag.</BodyShort>
				) : (
					<ParticipantStatusTable participants={missing} status="Missing" />
				)}
				<Heading level="2" size="large">Unknown status ({unknown.length})</Heading>
				{unknown.length === 0 ? (
					<BodyShort>All active participants have a confirmed Teamkatalogen status.</BodyShort>
				) : (
					<ParticipantStatusTable participants={unknown} status="Unknown" />
				)}
			</VStack>
		</main>
	);
}

function ParticipantStatusTable({
	participants,
	status,
}: {
	participants: AdminProgramParticipant[];
	status: string;
}) {
	return (
		<Table>
			<Table.Header>
				<Table.Row>
					<Table.HeaderCell scope="col">Name</Table.HeaderCell>
					<Table.HeaderCell scope="col">Email</Table.HeaderCell>
					<Table.HeaderCell scope="col">Teamkatalogen status</Table.HeaderCell>
				</Table.Row>
			</Table.Header>
			<Table.Body>
				{participants.map((participant) => (
					<Table.Row key={participant.id}>
						<Table.HeaderCell scope="row">{participant.fullname || "Name unavailable"}</Table.HeaderCell>
						<Table.DataCell>{participant.email}</Table.DataCell>
						<Table.DataCell>{status}</Table.DataCell>
					</Table.Row>
				))}
			</Table.Body>
		</Table>
	);
}

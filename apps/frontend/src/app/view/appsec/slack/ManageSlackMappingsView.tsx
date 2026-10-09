"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import {
	AdminProgramParticipant,
	SlackMappingOverview,
} from "@/app/utils/Variables";
import { BodyShort, Button, Heading, Select, Table, VStack } from "@navikt/ds-react";
import { useState } from "react";

export function ManageSlackMappingsView({
	participants,
	overview,
	onRefresh,
}: {
	participants: AdminProgramParticipant[];
	overview: SlackMappingOverview;
	onRefresh: () => Promise<void>;
}) {
	const [selectedParticipants, setSelectedParticipants] = useState<Record<string, string>>({});
	const [busySlackUserId, setBusySlackUserId] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	const addMapping = async (slackUserId: string) => {
		const participantId = selectedParticipants[slackUserId];
		if (busySlackUserId) return;
		if (!participantId) {
			setError("Choose a participant before mapping this Slack account.");
			return;
		}
		setBusySlackUserId(slackUserId);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.addSlackMapping(slackUserId, participantId);
			if (status === 409) {
				setError("This Slack account or participant already has a mapping. Review the existing mappings.");
				return;
			}
			if (status !== 201) {
				setError("We couldn't save the mapping. Try again.");
				return;
			}
			setNotice(`The Slack account ${slackUserId} was mapped.`);
			setSelectedParticipants((current) => {
				const next = { ...current };
				delete next[slackUserId];
				return next;
			});
			await onRefresh();
		} catch {
			setError("We couldn't save the mapping. Try again.");
		} finally {
			setBusySlackUserId(null);
		}
	};

	const removeMapping = async (slackUserId: string) => {
		if (busySlackUserId) return;
		setBusySlackUserId(slackUserId);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.removeSlackMapping(slackUserId);
			if (status !== 204) {
				setError("We couldn't remove the mapping. Try again.");
				return;
			}
			setNotice(`The Slack account ${slackUserId} was unmapped.`);
			await onRefresh();
		} catch {
			setError("We couldn't remove the mapping. Try again.");
		} finally {
			setBusySlackUserId(null);
		}
	};

	return (
		<VStack gap="space-24">
			<VStack gap="space-4">
				<Heading level="2" size="large">Account mappings</Heading>
				<BodyShort>
					Explicitly map Slack account IDs to program participants. Names are not used to infer identity.
				</BodyShort>
			</VStack>
			{notice && <BodyShort role="status">{notice}</BodyShort>}
			{error && <BodyShort role="alert">{error}</BodyShort>}

			<section aria-labelledby="unmapped-heading">
				<VStack gap="space-16">
					<Heading level="3" size="medium" id="unmapped-heading">Unmapped Slack accounts</Heading>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Slack account ID</Table.HeaderCell>
								<Table.HeaderCell scope="col">First seen</Table.HeaderCell>
								<Table.HeaderCell scope="col">Participant</Table.HeaderCell>
								<Table.HeaderCell scope="col">Action</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{overview.unmappedAuthors.map((author) => (
								<Table.Row key={author.slackUserId}>
									<Table.HeaderCell scope="row">{author.slackUserId}</Table.HeaderCell>
									<Table.DataCell>{new Date(author.firstSeenAt).toLocaleString()}</Table.DataCell>
									<Table.DataCell>
										<Select
											label={`Participant for Slack account ${author.slackUserId}`}
											hideLabel
											value={selectedParticipants[author.slackUserId] ?? ""}
											onChange={(event) =>
												setSelectedParticipants((current) => ({
													...current,
													[author.slackUserId]: event.target.value,
												}))
											}
										>
											<option value="">Select participant</option>
											{participants.map((participant) => (
												<option key={participant.id} value={participant.id}>
													{participant.fullname || "Name unavailable"} — {participant.email}
													{participant.active ? "" : " (deactivated)"}
												</option>
											))}
										</Select>
									</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											variant="secondary"
											loading={busySlackUserId === author.slackUserId}
											onClick={() => addMapping(author.slackUserId)}
										>
											Map account
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{overview.unmappedAuthors.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={4}>No unmapped Slack accounts need review.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>

			<section aria-labelledby="mapped-heading">
				<VStack gap="space-16">
					<Heading level="3" size="medium" id="mapped-heading">Approved mappings</Heading>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Slack account ID</Table.HeaderCell>
								<Table.HeaderCell scope="col">Participant</Table.HeaderCell>
								<Table.HeaderCell scope="col">Email</Table.HeaderCell>
								<Table.HeaderCell scope="col">Action</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{overview.mappings.map((mapping) => (
								<Table.Row key={mapping.slackUserId}>
									<Table.HeaderCell scope="row">{mapping.slackUserId}</Table.HeaderCell>
									<Table.DataCell>{mapping.participantName || "Name unavailable"}</Table.DataCell>
									<Table.DataCell>{mapping.participantEmail}</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											data-color="danger"
											variant="secondary"
											loading={busySlackUserId === mapping.slackUserId}
											onClick={() => removeMapping(mapping.slackUserId)}
										>
											Remove mapping
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{overview.mappings.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={4}>No Slack accounts are mapped.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>
		</VStack>
	);
}

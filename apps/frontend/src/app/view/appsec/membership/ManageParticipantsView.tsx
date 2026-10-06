"use client";

import { AdminProgramParticipant } from "@/app/utils/Variables";
import { Apies } from "@/app/shared/hooks/Apies";
import { BodyShort, Button, Heading, Modal, Table, TextField, VStack } from "@navikt/ds-react";
import { useRef, useState } from "react";

export function ManageParticipantsView({ participants }: { participants: AdminProgramParticipant[] }) {
	const [participantList, setParticipantList] = useState(participants);
	const [selectedForDeletion, setSelectedForDeletion] =
		useState<AdminProgramParticipant | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [reasonError, setReasonError] = useState(false);
	const reasonRef = useRef<HTMLInputElement>(null);

	const changeStatus = async (participant: AdminProgramParticipant, active = !participant.active) => {
		if (busy) return;
		setBusy(true);
		setError(null);
		try {
			const status = await Apies.updateParticipantStatus(participant.id, active);
			if (status !== 204) {
				setError("We couldn't change the status. Try again.");
				return;
			}
			setParticipantList((current) =>
				current.map<AdminProgramParticipant>((entry) =>
					entry.id === participant.id ? { ...entry, active, status: active ? "ACTIVE" : "DEACTIVATED" } : entry,
				),
			);
		} catch {
			setError("We couldn't change the status. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const deleteParticipant = async () => {
		const reason = reasonRef.current?.value.trim() ?? "";
		if (!selectedForDeletion || busy) return;
		if (!reason) {
			setReasonError(true);
			return;
		}

		setBusy(true);
		setError(null);
		try {
			const status = await Apies.deleteParticipant(selectedForDeletion.id, reason);
			if (status !== 204) {
				setError("We couldn't delete the participant. Try again.");
				return;
			}
			setParticipantList((current) =>
				current.filter((entry) => entry.id !== selectedForDeletion.id),
			);
			setSelectedForDeletion(null);
		} catch {
			setError("We couldn't delete the participant. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const closeDeletionDialog = () => {
		setSelectedForDeletion(null);
		setError(null);
	};

	const openDeletionDialog = (participant: AdminProgramParticipant) => {
		setError(null);
		setReasonError(false);
		if (reasonRef.current) reasonRef.current.value = "";
		setSelectedForDeletion(participant);
	};

	return (
		<VStack gap="space-24">
			<VStack gap="space-4">
				<Heading level="1" size="xlarge">
					Manage participants
				</Heading>
				<BodyShort>
					Deactivation keeps participant data. Permanent deletion removes all
					information and history linked to the participant.
				</BodyShort>
			</VStack>
			{error && !selectedForDeletion && <BodyShort role="alert">{error}</BodyShort>}
			<Table>
				<Table.Header>
					<Table.Row>
						<Table.HeaderCell scope="col">Name</Table.HeaderCell>
						<Table.HeaderCell scope="col">Email</Table.HeaderCell>
						<Table.HeaderCell scope="col">Teams</Table.HeaderCell>
						<Table.HeaderCell scope="col">Status</Table.HeaderCell>
						<Table.HeaderCell scope="col">Actions</Table.HeaderCell>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{participantList.map((participant) => (
						<Table.Row key={participant.id}>
							<Table.HeaderCell scope="row">
								{participant.fullname || "Name unavailable"}
							</Table.HeaderCell>
							<Table.DataCell>{participant.email}</Table.DataCell>
							<Table.DataCell>{participant.teams.join(", ")}</Table.DataCell>
							<Table.DataCell>
								{participant.active ? "Active" : participant.status === "LEFT" ? "Left program" : "Deactivated"}
							</Table.DataCell>
							<Table.DataCell>
								<Button
									size="small"
									data-color="neutral"
									variant="secondary"
									onClick={() => changeStatus(participant)}
								>
									{participant.active ? "Deactivate" : "Reactivate"}
								</Button>
								{participant.status === "LEFT" && (
									<Button size="small" variant="secondary" onClick={() => changeStatus(participant, false)}>
										Deactivate
									</Button>
								)}
								<Button
									size="small"
									data-color="danger"
									variant="secondary"
									onClick={() => openDeletionDialog(participant)}
								>
									Delete permanently
								</Button>
							</Table.DataCell>
						</Table.Row>
					))}
					{participantList.length === 0 && (
						<Table.Row>
							<Table.DataCell colSpan={5}>No participants.</Table.DataCell>
						</Table.Row>
					)}
				</Table.Body>
			</Table>

			<Modal
				open={selectedForDeletion !== null}
				onClose={closeDeletionDialog}
				header={{ heading: "Delete participant" }}
			>
				<Modal.Body>
					<BodyShort>
						This cannot be undone. Enter a reason before deleting the
						participant.
					</BodyShort>
					{error && <BodyShort role="alert">{error}</BodyShort>}
					<TextField
						label="Reason for deletion"
						size="small"
						ref={reasonRef}
						error={reasonError ? "Enter a reason." : undefined}
						onChange={() => setReasonError(false)}
					/>
				</Modal.Body>
				<Modal.Footer>
					<Button
						type="button"
						variant="tertiary"
						onClick={closeDeletionDialog}
					>
						Cancel
					</Button>
					<Button
						type="button"
						data-color="danger"
						variant="primary"
						onClick={deleteParticipant}
					>
						Delete participant
					</Button>
				</Modal.Footer>
			</Modal>
		</VStack>
	);
}

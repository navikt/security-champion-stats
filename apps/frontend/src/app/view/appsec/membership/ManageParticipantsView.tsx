"use client";

import { AdminProgramParticipant } from "@/app/utils/Variables";
import { Apies } from "@/app/shared/hooks/Apies";
import {
	BodyShort,
	Button,
	Heading,
	Modal,
	Table,
	TextField,
	VStack,
} from "@navikt/ds-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";

export function ManageParticipantsView({
	participants,
}: {
	participants: AdminProgramParticipant[];
}) {
	const t = useTranslations("appsec.membership");
	const [participantList, setParticipantList] = useState(participants);
	const [selectedForDeletion, setSelectedForDeletion] =
		useState<AdminProgramParticipant | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [reasonError, setReasonError] = useState(false);
	const reasonRef = useRef<HTMLInputElement>(null);

	const changeStatus = async (participant: AdminProgramParticipant) => {
		if (busy) return;
		setBusy(true);
		setError(null);
		try {
			const status = await Apies.updateParticipantStatus(
				participant.id,
				!participant.active,
			);
			if (status !== 204) {
				setError(t("statusError"));
				return;
			}
			setParticipantList((current) =>
				current.map((entry) =>
					entry.id === participant.id
						? { ...entry, active: !participant.active }
						: entry,
				),
			);
		} catch {
			setError(t("statusError"));
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
				setError(t("deleteError"));
				return;
			}
			setParticipantList((current) =>
				current.filter((entry) => entry.id !== selectedForDeletion.id),
			);
			setSelectedForDeletion(null);
		} catch {
			setError(t("deleteError"));
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
					{t("title")}
				</Heading>
				<BodyShort>{t("description")}</BodyShort>
			</VStack>
			{error && !selectedForDeletion && <BodyShort role="alert">{error}</BodyShort>}
			<Table>
				<Table.Header>
					<Table.Row>
						<Table.HeaderCell scope="col">{t("name")}</Table.HeaderCell>
						<Table.HeaderCell scope="col">{t("email")}</Table.HeaderCell>
						<Table.HeaderCell scope="col">{t("teams")}</Table.HeaderCell>
						<Table.HeaderCell scope="col">{t("status")}</Table.HeaderCell>
						<Table.HeaderCell scope="col">{t("actions")}</Table.HeaderCell>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{participantList.map((participant) => (
						<Table.Row key={participant.id}>
							<Table.HeaderCell scope="row">
								{participant.fullname || t("nameUnavailable")}
							</Table.HeaderCell>
							<Table.DataCell>{participant.email}</Table.DataCell>
							<Table.DataCell>{participant.teams.join(", ")}</Table.DataCell>
							<Table.DataCell>
								{participant.active ? t("active") : t("deactivated")}
							</Table.DataCell>
							<Table.DataCell>
								<Button
									size="small"
									data-color="neutral"
									variant="secondary"
									onClick={() => changeStatus(participant)}
								>
									{participant.active ? t("deactivate") : t("reactivate")}
								</Button>
								<Button
									size="small"
									data-color="danger"
									variant="secondary"
									onClick={() => openDeletionDialog(participant)}
								>
									{t("delete")}
								</Button>
							</Table.DataCell>
						</Table.Row>
					))}
					{participantList.length === 0 && (
						<Table.Row>
							<Table.DataCell colSpan={5}>{t("empty")}</Table.DataCell>
						</Table.Row>
					)}
				</Table.Body>
			</Table>

			<Modal
				open={selectedForDeletion !== null}
				onClose={closeDeletionDialog}
				header={{ heading: t("deleteTitle") }}
			>
				<Modal.Body>
					<BodyShort>{t("deleteDescription")}</BodyShort>
					{error && <BodyShort role="alert">{error}</BodyShort>}
					<TextField
						label={t("reason")}
						size="small"
						ref={reasonRef}
						error={reasonError ? t("reasonRequired") : undefined}
						onChange={() => setReasonError(false)}
					/>
				</Modal.Body>
				<Modal.Footer>
					<Button
						type="button"
						variant="tertiary"
						onClick={closeDeletionDialog}
					>
						{t("cancel")}
					</Button>
					<Button
						type="button"
						data-color="danger"
						variant="primary"
						onClick={deleteParticipant}
					>
						{t("confirmDelete")}
					</Button>
				</Modal.Footer>
			</Modal>
		</VStack>
	);
}

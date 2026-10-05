"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaEventMapping } from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	Heading,
	Table,
	TextField,
	VStack,
} from "@navikt/ds-react";
import { useState } from "react";

export function ManageDeltaEventMappingsView({
	mappings,
	onRefresh,
}: {
	mappings: DeltaEventMapping[];
	onRefresh: () => Promise<void>;
}) {
	const [programEventName, setProgramEventName] = useState("");
	const [deltaEventUuid, setDeltaEventUuid] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	const addMapping = async () => {
		if (busy) return;
		if (!programEventName.trim() || !deltaEventUuid.trim()) {
			setError("Enter both a program event name and a Delta event UUID.");
			return;
		}
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.addDeltaEventMapping(programEventName, deltaEventUuid);
			if (status === 409) {
				setError("This Delta event UUID is already mapped.");
				return;
			}
			if (status !== 201) {
				setError("We couldn't save the Delta event mapping. Check the event name and UUID, then try again.");
				return;
			}
			setNotice(`The Delta event was mapped to ${programEventName.trim()}.`);
			setProgramEventName("");
			setDeltaEventUuid("");
			await onRefresh();
		} catch {
			setError("We couldn't save the Delta event mapping. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const removeMapping = async (mapping: DeltaEventMapping) => {
		if (busy) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.removeDeltaEventMapping(mapping.id);
			if (status === 409) {
				setError("This mapping cannot be removed because it has awarded registration credits.");
				return;
			}
			if (status !== 204) {
				setError("We couldn't remove the Delta event mapping. Try again.");
				return;
			}
			setNotice(`The mapping for ${mapping.programEventName} was removed.`);
			await onRefresh();
		} catch {
			setError("We couldn't remove the Delta event mapping. Try again.");
		} finally {
			setBusy(false);
		}
	};

	return (
		<VStack gap="space-24">
			<VStack gap="space-4">
				<Heading level="1" size="xlarge">Manage Delta event mappings</Heading>
				<BodyShort>
					Only map Delta event UUIDs confirmed as eligible. Event names and dates are not used to infer a match.
				</BodyShort>
				<BodyShort>
					Registration sync will be enabled after Delta production access is confirmed.
				</BodyShort>
			</VStack>
			{notice && <BodyShort role="status">{notice}</BodyShort>}
			{error && <BodyShort role="alert">{error}</BodyShort>}

			<VStack gap="space-16">
				<Heading level="2" size="large">Add mapping</Heading>
				<TextField
					label="Program event name"
					value={programEventName}
					onChange={(event) => setProgramEventName(event.target.value)}
					maxLength={200}
				/>
				<TextField
					label="Owner-confirmed Delta event UUID"
					description="Enter the UUID supplied by the Delta event owner."
					value={deltaEventUuid}
					onChange={(event) => setDeltaEventUuid(event.target.value)}
					maxLength={36}
				/>
				<Button onClick={() => void addMapping()} loading={busy}>
					Add mapping
				</Button>
			</VStack>

			<section aria-labelledby="mappings-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="mappings-heading">Eligible event mappings</Heading>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Program event</Table.HeaderCell>
								<Table.HeaderCell scope="col">Delta event UUID</Table.HeaderCell>
								<Table.HeaderCell scope="col">Added</Table.HeaderCell>
								<Table.HeaderCell scope="col">Action</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{mappings.map((mapping) => (
								<Table.Row key={mapping.id}>
									<Table.HeaderCell scope="row">{mapping.programEventName}</Table.HeaderCell>
									<Table.DataCell>{mapping.deltaEventUuid}</Table.DataCell>
									<Table.DataCell>{new Date(mapping.createdAt).toLocaleString()}</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											variant="secondary"
											data-color="danger"
											loading={busy}
											onClick={() => void removeMapping(mapping)}
										>
											Remove mapping
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{mappings.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={4}>No eligible Delta event mappings have been added.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>
		</VStack>
	);
}

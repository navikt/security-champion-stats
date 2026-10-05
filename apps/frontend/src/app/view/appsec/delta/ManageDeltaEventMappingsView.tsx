"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaCategory, DeltaEventMapping } from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	Heading,
	Select,
	Table,
	TextField,
	VStack,
} from "@navikt/ds-react";
import { useState } from "react";

export function ManageDeltaEventMappingsView({
	mappings,
	categories,
	onRefresh,
}: {
	mappings: DeltaEventMapping[];
	categories: DeltaCategory[];
	onRefresh: () => Promise<void>;
}) {
	const [programEventName, setProgramEventName] = useState("");
	const [deltaEventUuid, setDeltaEventUuid] = useState("");
	const [deltaCategoryId, setDeltaCategoryId] = useState("");
	const [categoryChanges, setCategoryChanges] = useState<Record<string, string>>({});
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	const addMapping = async () => {
		if (busy) return;
		if (!programEventName.trim() || !deltaEventUuid.trim() || !deltaCategoryId) {
			setError("Enter a program event name, Delta event UUID, and category.");
			return;
		}
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.addDeltaEventMapping(
				programEventName,
				deltaEventUuid,
				Number(deltaCategoryId),
			);
			if (status === 409) {
				setError("This Delta event UUID is already mapped.");
				return;
			}
			if (status !== 201) {
				setError("We couldn't save the Delta event mapping. Check the event name, UUID, and category, then try again.");
				return;
			}
			setNotice(`The Delta event was mapped to ${programEventName.trim()}.`);
			setProgramEventName("");
			setDeltaEventUuid("");
			setDeltaCategoryId("");
			await onRefresh();
		} catch {
			setError("We couldn't save the Delta event mapping. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const updateCategory = async (mapping: DeltaEventMapping) => {
		if (busy) return;
		const selectedCategoryId = categoryChanges[mapping.id] ?? mapping.deltaCategoryId?.toString() ?? "";
		if (!selectedCategoryId) {
			setError("Choose a category for this mapping.");
			return;
		}
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.updateDeltaEventMappingCategory(mapping.id, Number(selectedCategoryId));
			if (status !== 204) {
				setError("We couldn't update the Delta category. Try again.");
				return;
			}
			setNotice(`The category for ${mapping.programEventName} was updated.`);
			setCategoryChanges((previous) => ({ ...previous, [mapping.id]: selectedCategoryId }));
			await onRefresh();
		} catch {
			setError("We couldn't update the Delta category. Try again.");
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
					Only map public Delta events confirmed as eligible. Event names and dates are not used to infer a match.
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
				<Select
					label="Delta category"
					description="Choose the category assigned to this event in Delta."
					value={deltaCategoryId}
					onChange={(event) => setDeltaCategoryId(event.target.value)}
				>
					<option value="">Choose a category</option>
					{categories.map((category) => (
						<option key={category.id} value={category.id}>{category.name}</option>
					))}
				</Select>
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
								<Table.HeaderCell scope="col">Delta category</Table.HeaderCell>
								<Table.HeaderCell scope="col">Added</Table.HeaderCell>
								<Table.HeaderCell scope="col">Action</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{mappings.map((mapping) => {
								const selectedCategoryId = categoryChanges[mapping.id] ??
									mapping.deltaCategoryId?.toString() ?? "";
								const hasCurrentCategory = categories.some(
									(category) => category.id.toString() === selectedCategoryId,
								);
								return (
									<Table.Row key={mapping.id}>
										<Table.HeaderCell scope="row">{mapping.programEventName}</Table.HeaderCell>
										<Table.DataCell>{mapping.deltaEventUuid}</Table.DataCell>
										<Table.DataCell>
											<Select
												label={`Delta category for ${mapping.programEventName}`}
												hideLabel
												value={selectedCategoryId}
												onChange={(event) => setCategoryChanges((previous) => ({
													...previous,
													[mapping.id]: event.target.value,
												}))}
											>
												<option value="">Choose a category</option>
												{mapping.deltaCategoryId !== null && !hasCurrentCategory && (
													<option value={mapping.deltaCategoryId}>
														Unavailable category ({mapping.deltaCategoryId})
													</option>
												)}
												{categories.map((category) => (
													<option key={category.id} value={category.id}>{category.name}</option>
												))}
											</Select>
										</Table.DataCell>
										<Table.DataCell>{new Date(mapping.createdAt).toLocaleString()}</Table.DataCell>
										<Table.DataCell>
											<VStack gap="space-4">
												<Button
													size="small"
													variant="secondary"
													loading={busy}
													onClick={() => void updateCategory(mapping)}
												>
													Save category
												</Button>
												<Button
													size="small"
													variant="secondary"
													data-color="danger"
													loading={busy}
													onClick={() => void removeMapping(mapping)}
												>
													Remove mapping
												</Button>
											</VStack>
										</Table.DataCell>
									</Table.Row>
								);
							})}
							{mappings.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={5}>No eligible Delta event mappings have been added.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>
		</VStack>
	);
}

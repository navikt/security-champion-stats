"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaCategory, DeltaEligibleCategory, DeltaEventMapping } from "@/app/utils/Variables";
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
	eligibleCategories,
	onRefresh,
}: {
	mappings: DeltaEventMapping[];
	categories: DeltaCategory[];
	eligibleCategories: DeltaEligibleCategory[];
	onRefresh: () => Promise<void>;
}) {
	const [programEventName, setProgramEventName] = useState("");
	const [deltaEventUuid, setDeltaEventUuid] = useState("");
	const [deltaCategoryId, setDeltaCategoryId] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	const addMapping = async () => {
		if (busy) return;
		if (!programEventName.trim() || !deltaEventUuid.trim()) {
			setError("Enter a program event name and Delta event UUID.");
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

	const addCategory = async () => {
		if (busy) return;
		if (!deltaCategoryId) {
			setError("Choose a Delta category.");
			return;
		}
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.addDeltaEligibleCategory(Number(deltaCategoryId));
			if (status === 409) {
				setError("This Delta category is already eligible.");
				return;
			}
			if (status !== 201) {
				setError("We couldn't add the Delta category. Try again.");
				return;
			}
			const name = categories.find((category) => category.id.toString() === deltaCategoryId)?.name;
			setNotice(`${name ?? "The Delta category"} is now eligible for scoring.`);
			setDeltaCategoryId("");
			await onRefresh();
		} catch {
			setError("We couldn't add the Delta category. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const removeCategory = async (category: DeltaEligibleCategory) => {
		if (busy) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.removeDeltaEligibleCategory(category.categoryId);
			if (status !== 204) {
				setError("We couldn't remove the Delta category. Try again.");
				return;
			}
			setNotice(`${category.categoryName} is no longer eligible. Points already awarded are kept.`);
			await onRefresh();
		} catch {
			setError("We couldn't remove the Delta category. Try again.");
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
				<Heading level="1" size="xlarge">Manage Delta scoring</Heading>
				<BodyShort>
					Registrations for public events that have started in an eligible category, or in a single mapped event,
					give points.
				</BodyShort>
				<BodyShort>
					Registration sync will be enabled after Delta production access is confirmed.
				</BodyShort>
			</VStack>
			{notice && <BodyShort role="status">{notice}</BodyShort>}
			{error && <BodyShort role="alert">{error}</BodyShort>}

			<section aria-labelledby="categories-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="categories-heading">Eligible categories</Heading>
					<Select
						label="Delta category"
						value={deltaCategoryId}
						onChange={(event) => setDeltaCategoryId(event.target.value)}
					>
						<option value="">Choose a category</option>
						{categories
							.filter((category) => !eligibleCategories.some((eligible) => eligible.categoryId === category.id))
							.map((category) => (
								<option key={category.id} value={category.id}>{category.name}</option>
							))}
					</Select>
					<Button onClick={() => void addCategory()} loading={busy}>
						Add category
					</Button>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Delta category</Table.HeaderCell>
								<Table.HeaderCell scope="col">Added</Table.HeaderCell>
								<Table.HeaderCell scope="col">Action</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{eligibleCategories.map((category) => (
								<Table.Row key={category.categoryId}>
									<Table.HeaderCell scope="row">{category.categoryName}</Table.HeaderCell>
									<Table.DataCell>{new Date(category.createdAt).toLocaleString()}</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											variant="secondary"
											data-color="danger"
											loading={busy}
											onClick={() => void removeCategory(category)}
										>
											Remove category
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{eligibleCategories.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={3}>No Delta categories are eligible.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>

			<VStack gap="space-16">
				<Heading level="2" size="large">Add single event</Heading>
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
					<Heading level="2" size="large" id="mappings-heading">Single events</Heading>
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
									<Table.DataCell colSpan={4}>No single Delta events have been added.</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>
		</VStack>
	);
}

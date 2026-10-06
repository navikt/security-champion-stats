"use client";

import { useState } from "react";
import {
	Button,
	DatePicker,
	ErrorMessage,
	Heading,
	Modal,
	Select,
	Switch,
	Textarea,
	TextField,
	useDatepicker,
	VStack,
} from "@navikt/ds-react";
import { SecurityEvent, SecurityEventType } from "@/app/utils/Variables";

interface AddEventModalProps {
	open: boolean;
	onClose: () => void;
	onCreate: (event: Omit<SecurityEvent, "id">) => Promise<void> | void;
	loading?: boolean;
	error?: string | null;
}

export function AddEventModal({
	open,
	onClose,
	onCreate,
	loading = false,
	error,
}: AddEventModalProps) {
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [location, setLocation] = useState("");
	const [type, setType] = useState<SecurityEventType>("meetup");
	const [startTime, setStartTime] = useState("09:00");
	const [endTime, setEndTime] = useState("10:00");
	const [externalEvent, setExternalEvent] = useState(false);

	const today = new Date();
	today.setHours(0, 0, 0, 0);

	const startDatepicker = useDatepicker({ fromDate: today });
	const endDatepicker = useDatepicker({
		fromDate: startDatepicker.selectedDay ?? today,
	});

	const startDate =
		startDatepicker.selectedDay && startTime
			? combineDateAndTime(startDatepicker.selectedDay, startTime)
			: undefined;
	const endDate =
		endDatepicker.selectedDay && endTime
			? combineDateAndTime(endDatepicker.selectedDay, endTime)
			: undefined;

	const isEndBeforeStart = !!startDate && !!endDate && endDate <= startDate;

	const handleSubmit = async () => {
		if (loading || !name.trim() || !startDate || !endDate || isEndBeforeStart) {
			return;
		}

		await onCreate({
			name: name.trim(),
			description,
			location: location.trim(),
			type,
			startDate: startDate.toISOString(),
			endDate: endDate.toISOString(),
			externalEvent,
			deltaEvent: false,
		});
	};

	return (
		<Modal
			open={open}
			onClose={onClose}
			aria-labelledby={"add-event-title"}
			width={"small"}
		>
			<Modal.Header>
				<Heading size={"medium"} id={"add-event-title"} level={"2"}>
					Add event
				</Heading>
			</Modal.Header>
			<Modal.Body>
				<VStack gap={"space-16"}>
					{error && <ErrorMessage role="alert">{error}</ErrorMessage>}
					<TextField
						label="Name"
						maxLength={100}
						error={name && !name.trim() ? "Enter an event name" : undefined}
						value={name}
						onChange={(e) => setName(e.target.value)}
					/>

					<Textarea
						label="Description"
						value={description}
						onChange={(e) => setDescription(e.target.value)}
					/>

					<TextField
						label="Location"
						maxLength={100}
						value={location}
						onChange={(e) => setLocation(e.target.value)}
					/>

					<Select
						label="Type"
						value={type}
						onChange={(e) => setType(e.target.value as SecurityEventType)}
					>
						<option value={"meetup"}>Meeting</option>
						<option value={"workshop"}>Workshop</option>
					</Select>

					<DatePicker {...startDatepicker.datepickerProps}>
						<DatePicker.Input
							{...startDatepicker.inputProps}
							label="Start date"
						/>
					</DatePicker>
					<TextField
						label="Start time"
						type={"time"}
						value={startTime}
						onChange={(e) => setStartTime(e.target.value)}
					/>

					<DatePicker {...endDatepicker.datepickerProps}>
						<DatePicker.Input {...endDatepicker.inputProps} label="End date" />
					</DatePicker>
					<TextField
						label="End time"
						type={"time"}
						value={endTime}
						onChange={(e) => setEndTime(e.target.value)}
					/>
					{isEndBeforeStart && (
						<ErrorMessage>End must be after start</ErrorMessage>
					)}

					<Switch
						checked={externalEvent}
						onChange={(e) => setExternalEvent(e.target.checked)}
					>
						External event
					</Switch>
				</VStack>
			</Modal.Body>
			<Modal.Footer>
				<Button
					onClick={handleSubmit}
					loading={loading}
					disabled={
						loading ||
						!name.trim() ||
						!startDate ||
						!endDate ||
						isEndBeforeStart
					}
				>
					Create event
				</Button>
				<Button variant={"secondary"} onClick={onClose} disabled={loading}>
					Cancel
				</Button>
			</Modal.Footer>
		</Modal>
	);
}

function combineDateAndTime(date: Date, time: string): Date {
	const [hours, minutes] = time.split(":").map(Number);
	const combined = new Date(date);
	combined.setHours(hours, minutes, 0, 0);
	return combined;
}

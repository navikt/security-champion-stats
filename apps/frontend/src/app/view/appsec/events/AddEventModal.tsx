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
import { useTranslations } from "next-intl";
import { SecurityEvent, SecurityEventType } from "@/app/utils/Variables";

interface AddEventModalProps {
	open: boolean;
	onClose: () => void;
	onCreate: (event: Omit<SecurityEvent, "id">) => Promise<void> | void;
	loading?: boolean;
}

export function AddEventModal({
	open,
	onClose,
	onCreate,
	loading = false,
}: AddEventModalProps) {
	const t = useTranslations("appsec.events.form");

	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [location, setLocation] = useState("");
	const [type, setType] = useState<SecurityEventType>("meetup");
	const [startTime, setStartTime] = useState("09:00");
	const [endTime, setEndTime] = useState("10:00");
	const [externalEvent, setExternalEvent] = useState(false);
	const [deltaEvent, setDeltaEvent] = useState(true);

	const today = new Date();
	today.setHours(0, 0, 0, 0);

	const startDatepicker = useDatepicker({ fromDate: today });
	const endDatepicker = useDatepicker({
		fromDate: startDatepicker.selectedDay ?? today,
	});

	const startDate = startDatepicker.selectedDay
		? combineDateAndTime(startDatepicker.selectedDay, startTime)
		: undefined;
	const endDate = endDatepicker.selectedDay
		? combineDateAndTime(endDatepicker.selectedDay, endTime)
		: undefined;

	const isEndBeforeStart = !!startDate && !!endDate && endDate <= startDate;

	const handleSubmit = async () => {
		if (!name || !startDate || !endDate || isEndBeforeStart) {
			return;
		}

		await onCreate({
			name,
			description,
			location,
			type,
			startDate: startDate.toISOString(),
			endDate: endDate.toISOString(),
			externalEvent,
			deltaEvent,
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
					{t("title")}
				</Heading>
			</Modal.Header>
			<Modal.Body>
				<VStack gap={"space-16"}>
					<TextField
						label={t("name")}
						value={name}
						onChange={(e) => setName(e.target.value)}
					/>

					<Textarea
						label={t("description")}
						value={description}
						onChange={(e) => setDescription(e.target.value)}
					/>

					<TextField
						label={t("location")}
						value={location}
						onChange={(e) => setLocation(e.target.value)}
					/>

					<Select
						label={t("type")}
						value={type}
						onChange={(e) => setType(e.target.value as SecurityEventType)}
					>
						<option value={"meetup"}>{t("types.meeting")}</option>
						<option value={"workshop"}>{t("types.workshop")}</option>
						<option value={"course"}>{t("types.course")}</option>
					</Select>

					<DatePicker {...startDatepicker.datepickerProps}>
						<DatePicker.Input
							{...startDatepicker.inputProps}
							label={t("startDate")}
						/>
					</DatePicker>
					<TextField
						label={t("startTime")}
						type={"time"}
						value={startTime}
						onChange={(e) => setStartTime(e.target.value)}
					/>

					<DatePicker {...endDatepicker.datepickerProps}>
						<DatePicker.Input
							{...endDatepicker.inputProps}
							label={t("endDate")}
						/>
					</DatePicker>
					<TextField
						label={t("endTime")}
						type={"time"}
						value={endTime}
						onChange={(e) => setEndTime(e.target.value)}
					/>
					{isEndBeforeStart && (
						<ErrorMessage>{t("endBeforeStart")}</ErrorMessage>
					)}

					<Switch
						checked={externalEvent}
						onChange={(e) => setExternalEvent(e.target.checked)}
					>
						{t("externalEvent")}
					</Switch>

					<Switch
						checked={deltaEvent}
						onChange={(e) => setDeltaEvent(e.target.checked)}
					>
						{t("deltaEvent")}
					</Switch>
				</VStack>
			</Modal.Body>
			<Modal.Footer>
				<Button
					onClick={handleSubmit}
					loading={loading}
					disabled={!name || !startDate || !endDate || isEndBeforeStart}
				>
					{t("submit")}
				</Button>
				<Button variant={"secondary"} onClick={onClose} disabled={loading}>
					{t("cancel")}
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

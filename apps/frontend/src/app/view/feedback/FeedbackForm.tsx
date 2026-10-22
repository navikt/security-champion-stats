"use client";

import { Button, Textarea, TextField, VStack } from "@navikt/ds-react";
import { type FormEvent, useRef, useState } from "react";
import { useMe } from "@/app/shared/hooks/UseMe";
import { FeedbackApi } from "./FeedbackApi";

export function FeedbackForm() {
	const { me, loading } = useMe();
	const [title, setTitle] = useState("");
	const [description, setDescription] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [receipt, setReceipt] = useState<string | null>(null);
	const [attempted, setAttempted] = useState(false);
	const submissionId = useRef<string | null>(null);

	async function submit(event: FormEvent) {
		event.preventDefault();
		if (busy) return;
		setError("");
		setAttempted(true);
		if (
			title.trim().length < 1 ||
			title.trim().length > 200 ||
			description.trim().length < 1 ||
			description.trim().length > 5000
		) {
			return;
		}
		setBusy(true);
		try {
			submissionId.current ??= crypto.randomUUID();
			const result = await FeedbackApi.submit({
				id: submissionId.current,
				title,
				description,
			});
			setReceipt(result.id);
			setTitle("");
			setDescription("");
			setAttempted(false);
			submissionId.current = null;
		} catch (error) {
			console.error("Feedback submission failed", error);
			setError(
				error instanceof Error
					? error.message
					: "The feedback could not be submitted.",
			);
		} finally {
			setBusy(false);
		}
	}

	if (loading) return <p role="status">Loading membership...</p>;
	if (!me.isActive)
		return <p>Active program membership is required to submit feedback.</p>;
	if (receipt) {
		return (
			<VStack gap="space-16" align="start">
				<h1>Thank you for your feedback</h1>
				<p role="status">
					Your feedback has been saved for the program administrators.
				</p>
				<p>Reference: {receipt}</p>
				<Button onClick={() => setReceipt(null)}>Submit more feedback</Button>
			</VStack>
		);
	}
	return (
		<VStack gap="space-16">
			<h1>Share feedback</h1>
			<p>
				Help improve the app or the Security Champion program. Your feedback and
				identity are visible only to program administrators.
			</p>
			<p>
				Administrators decide whether feedback qualifies for the configured
				feedback credit. Do not include secrets or sensitive personal
				information.
			</p>
			<VStack
				as="form"
				gap="space-16"
				onSubmit={submit}
				aria-busy={busy}
				noValidate
			>
				<TextField
					label="Title"
					value={title}
					maxLength={200}
					aria-required
					readOnly={busy}
					error={
						attempted && (title.trim().length < 1 || title.trim().length > 200)
							? "Provide a title of up to 200 characters."
							: undefined
					}
					onChange={(event) => {
						setTitle(event.target.value);
						submissionId.current = null;
					}}
				/>
				<Textarea
					label="Feedback"
					value={description}
					maxLength={5000}
					aria-required
					readOnly={busy}
					error={
						attempted &&
						(description.trim().length < 1 || description.trim().length > 5000)
							? "Provide feedback of up to 5000 characters."
							: undefined
					}
					onChange={(event) => {
						setDescription(event.target.value);
						submissionId.current = null;
					}}
					description="Describe the problem, suggestion, or experience you want to share."
				/>
				{error && <p role="alert">{error}</p>}
				<Button type="submit" loading={busy} disabled={busy}>
					Submit feedback
				</Button>
			</VStack>
		</VStack>
	);
}

"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	SlackChannelAttentionCategory,
	SlackChannelParticipant,
	SlackChannelParticipationOverview,
} from "@/app/utils/Variables";
import { BodyShort, Button, Heading, Table, VStack } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";

const categoryLabels: Record<SlackChannelAttentionCategory, string> = {
	NOT_IN_CHANNEL: "Active, not in channel",
	DEACTIVATED_AFTER_LEAVING: "Deactivated after leaving",
	IDENTITY_UNRESOLVED: "Slack account not found",
};

const outcomeLabels: Record<NonNullable<SlackChannelParticipationOverview["outcome"]>, string> = {
	RUNNING: "Running",
	SUCCEEDED: "Succeeded",
	PARTIAL_FAILURE: "Completed with undelivered notices",
	FAILED: "Failed",
};

function notificationLabel(status: SlackChannelParticipant["notificationStatus"]): string {
	switch (status) {
		case "SENT":
			return "Sent";
		case "PENDING":
		case "SENDING":
			return "Waiting to send";
		case "UNCERTAIN":
			return "Unconfirmed – check Slack";
		default:
			return "–";
	}
}

const formatTime = (value: string | null) => (value ? new Date(value).toLocaleString() : "Never");

export function SlackChannelParticipationView() {
	const [overview, setOverview] = useState<SlackChannelParticipationOverview | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	const refresh = useCallback(async () => {
		setBusy(true);
		setError(null);
		try {
			setOverview(await Apies.getSlackChannelParticipation());
		} catch {
			setError("We couldn't load channel participation. Try again.");
		} finally {
			setBusy(false);
		}
	}, []);

	useEffect(() => {
		void refresh();
	}, [refresh]);

	const triggerCheck = async () => {
		if (busy || !overview?.enabled) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		try {
			const status = await Apies.triggerSlackChannelParticipationCheck();
			if (status === 202) {
				setNotice("The channel check was queued. Refresh to see the result.");
			} else {
				setError(
					status === 409
						? "Channel checks are disabled or a check is already running."
						: "We couldn't start the channel check. Try again.",
				);
			}
		} catch {
			setError("We couldn't start the channel check. Try again.");
		} finally {
			setBusy(false);
		}
	};

	return (
		<section aria-labelledby="slack-channel-heading">
			<VStack gap="space-16">
				<Heading level="2" size="large" id="slack-channel-heading">
					Channel participation
				</Heading>
				<BodyShort>
					Security Champions are expected to follow the program channel. Participants who leave it are
					deactivated, notified privately and reactivated automatically when they rejoin.
				</BodyShort>
				{overview && (
					<VStack gap="space-8">
						{!overview.enabled && <BodyShort>Channel monitoring is disabled.</BodyShort>}
						{overview.enabled && !overview.channelConfigured && (
							<BodyShort role="alert">The program channel is not configured.</BodyShort>
						)}
						<BodyShort>Last successful check: {formatTime(overview.lastSuccessAt)}</BodyShort>
						{overview.outcome && overview.outcome !== "SUCCEEDED" && (
							<BodyShort role={overview.outcome === "FAILED" ? "alert" : undefined}>
								Latest check ({formatTime(overview.lastAttemptAt)}): {outcomeLabels[overview.outcome]}
								{overview.failureSummary ? ` – ${overview.failureSummary}` : ""}
							</BodyShort>
						)}
					</VStack>
				)}
				<div>
					<Button
						size="small"
						variant="secondary"
						onClick={triggerCheck}
						disabled={busy || !overview?.enabled}
						loading={busy}
					>
						Check channel now
					</Button>{" "}
					<Button size="small" variant="tertiary" onClick={() => void refresh()} disabled={busy}>
						Refresh
					</Button>
				</div>
				{notice && !busy && <BodyShort role="status">{notice}</BodyShort>}
				{error && <BodyShort role="alert">{error}</BodyShort>}
				{overview &&
					(overview.participants.length === 0 ? (
						<BodyShort>All checked participants are in the channel.</BodyShort>
					) : (
						<div
							role="region"
							aria-label="Participants needing attention"
							tabIndex={0}
							style={{ overflowX: "auto" }}
						>
							<Table size="small">
								<Table.Header>
									<Table.Row>
										<Table.HeaderCell scope="col">Participant</Table.HeaderCell>
										<Table.HeaderCell scope="col">Status</Table.HeaderCell>
										<Table.HeaderCell scope="col">Absent since</Table.HeaderCell>
										<Table.HeaderCell scope="col">Notification</Table.HeaderCell>
									</Table.Row>
								</Table.Header>
								<Table.Body>
									{overview.participants.map((participant) => (
										<Table.Row key={participant.participantId}>
											<Table.HeaderCell scope="row">
												{participant.name}
												<br />
												{participant.email}
											</Table.HeaderCell>
											<Table.DataCell>{categoryLabels[participant.category]}</Table.DataCell>
											<Table.DataCell>
												{participant.absentSince
													? new Date(participant.absentSince).toLocaleString()
													: "–"}
											</Table.DataCell>
											<Table.DataCell>
												{notificationLabel(participant.notificationStatus)}
											</Table.DataCell>
										</Table.Row>
									))}
								</Table.Body>
							</Table>
						</div>
					))}
			</VStack>
		</section>
	);
}

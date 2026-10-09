"use client";

import { useCallback, useEffect, useState } from "react";
import {
	BodyShort,
	Button,
	ErrorMessage,
	ExpansionCard,
	Heading,
	Tag,
	Textarea,
	VStack,
} from "@navikt/ds-react";
import { useMe } from "@/app/shared/hooks/UseMe";
import { Apies } from "@/app/shared/hooks/Apies";
import type { SecurityEvent } from "@/app/utils/Variables";
import {
	EventClaimsApi,
	type ContributionStatus,
	type EventClaim,
	type EventClaimOverview,
} from "./EventClaimsApi";
import { EventClaimForm } from "./EventClaimForm";

const contributionStatuses = [
	{ status: "PENDING", label: "pending" },
	{ status: "APPROVED", label: "approved" },
	{ status: "REJECTED", label: "rejected" },
	{ status: "REVOKED", label: "revoked" },
] as const;

function ClaimCard({
	claim,
	overview,
	admin,
	onUpdated,
	onEdit,
}: {
	claim: EventClaim;
	overview: EventClaimOverview;
	admin: boolean;
	onUpdated: (claim: EventClaim) => void;
	onEdit: (claim: EventClaim) => void;
}) {
	const [reason, setReason] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [message, setMessage] = useState("");
	const counts: Record<ContributionStatus, number> = {
		PENDING: 0,
		APPROVED: 0,
		REJECTED: 0,
		REVOKED: 0,
	};
	for (const contributor of claim.contributors) {
		counts[contributor.status]++;
	}
	const approved = counts.APPROVED;
	const pending = counts.PENDING;
	const fullyApproved =
		claim.contributors.length > 0 && approved === claim.contributors.length;
	const statusLabel = fullyApproved
		? "Approved"
		: approved > 0
			? "Partially approved"
			: pending > 0
				? "Pending review"
				: counts.REJECTED > 0 && counts.REJECTED === claim.contributors.length
					? "Rejected"
					: counts.REVOKED > 0 && counts.REVOKED === claim.contributors.length
						? "Revoked"
						: "Not approved";
	const statusColor = fullyApproved
		? "success"
		: approved > 0 || pending > 0
			? "warning"
			: "danger";

	async function review(participantId: string, decision: ContributionStatus) {
		if (busy) return;
		setError(null);
		setMessage("");
		if (!reason.trim() || reason.length > 1000) {
			setError("Provide a review reason of up to 1000 characters.");
			return;
		}
		if (
			decision === "REVOKED" &&
			!window.confirm(
				"Revoke this contributor's credit? The score correction will stay in the original season.",
			)
		)
			return;
		setBusy(true);
		try {
			onUpdated(
				await EventClaimsApi.review(claim, participantId, decision, reason),
			);
			setMessage(`Contribution ${decision.toLowerCase()}.`);
			setReason("");
		} catch (error) {
			console.error("Event claim review failed", error);
			setError(
				error instanceof Error
					? error.message
					: "The review could not be saved.",
			);
		} finally {
			setBusy(false);
		}
	}

	return (
		<ExpansionCard
			aria-label={claim.name}
			defaultOpen={!fullyApproved}
			data-color={statusColor}
		>
			<ExpansionCard.Header>
				<ExpansionCard.Title as="h2">{claim.name}</ExpansionCard.Title>
				<Tag size="small" variant="moderate" data-color={statusColor}>
					{statusLabel}
				</Tag>
				<ExpansionCard.Description>
					{new Date(claim.startDate).toLocaleString()} to{" "}
					{new Date(claim.endDate).toLocaleString()}
					{" · "}
					{contributionStatuses
						.filter(({ status }) => counts[status] > 0)
						.map(({ status, label }) => `${counts[status]} ${label}`)
						.join(", ")}
				</ExpansionCard.Description>
			</ExpansionCard.Header>
			<ExpansionCard.Content>
				<VStack as="article" gap="space-12">
					<BodyShort>
						{claim.externalEvent ? "External event" : "Nav-internal event"} ·{" "}
						{claim.location || "No location specified"} · Season from{" "}
						{claim.seasonStartsOn}
					</BodyShort>
					<BodyShort>{claim.description}</BodyShort>
					<ul>
						{claim.links.map((link) => (
							<li key={link}>
								<a href={link}>{link}</a>
							</li>
						))}
					</ul>
					<BodyShort>
						<strong>Advance invitation evidence:</strong>{" "}
						{claim.invitationEvidence}
					</BodyShort>
					{claim.published && (
						<BodyShort>
							Published in <a href="/events">Past events</a>.
						</BodyShort>
					)}
					{claim.contributors.map((contributor) => (
						<VStack key={contributor.participantId} gap="space-8">
							<Heading size="small" level="3">
								{contributor.fullName || "Participant"} -{" "}
								{contributor.status.toLowerCase()}
							</Heading>
							<BodyShort>{contributor.contribution}</BodyShort>
							{admin && contributor.status === "PENDING" && (
								<>
									{contributor.participantId !==
									overview.currentParticipantId ? (
										<Button
											disabled={busy}
											onClick={() =>
												review(contributor.participantId, "APPROVED")
											}
										>
											Approve {contributor.fullName || "contributor"}
										</Button>
									) : (
										<BodyShort>
											Another administrator must approve your credit.
										</BodyShort>
									)}
									<Button
										variant="secondary"
										disabled={busy}
										onClick={() =>
											review(contributor.participantId, "REJECTED")
										}
									>
										Reject {contributor.fullName || "contributor"}
									</Button>
								</>
							)}
							{admin && contributor.status === "APPROVED" && (
								<Button
									variant="secondary"
									data-color="danger"
									disabled={busy}
									onClick={() => review(contributor.participantId, "REVOKED")}
								>
									Revoke credit for {contributor.fullName || "contributor"}
								</Button>
							)}
						</VStack>
					))}
					{admin &&
						claim.contributors.some((contributor) =>
							["PENDING", "APPROVED"].includes(contributor.status),
						) && (
							<Textarea
								label={`Review reason for ${claim.name}`}
								value={reason}
								maxLength={1000}
								onChange={(event) => setReason(event.target.value)}
							/>
						)}
					{error && <ErrorMessage role="alert">{error}</ErrorMessage>}
					{message && <BodyShort role="status">{message}</BodyShort>}
					{claim.reviews.length > 0 && (
						<>
							<Heading size="small" level="3">
								Review history
							</Heading>
							<ul>
								{claim.reviews.map((entry, index) => (
									<li key={index}>
										{entry.fullName || "Contributor"}:{" "}
										{entry.decision.toLowerCase()} - {entry.reason} (
										{new Date(entry.createdAt).toLocaleString()})
									</li>
								))}
							</ul>
						</>
					)}
					{!admin &&
						claim.editable &&
						claim.submitterId === overview.currentParticipantId && (
							<Button variant="secondary" onClick={() => onEdit(claim)}>
								Edit and resubmit
							</Button>
						)}
				</VStack>
			</ExpansionCard.Content>
		</ExpansionCard>
	);
}

export function EventClaimsView({ admin = false }: { admin?: boolean }) {
	const { me, loading } = useMe();
	const [overview, setOverview] = useState<EventClaimOverview | null>(null);
	const [events, setEvents] = useState<SecurityEvent[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [refreshing, setRefreshing] = useState(false);
	const [editing, setEditing] = useState<EventClaim | "new" | null>(null);
	const [message, setMessage] = useState("");
	const allowed = admin ? me.isAdmin : me.isActive;

	const refresh = useCallback(async () => {
		setRefreshing(true);
		setError(null);
		try {
			const [loaded, catalog] = await Promise.all([
				EventClaimsApi.overview(admin),
				admin ? Promise.resolve([]) : Apies.fetchEvents(),
			]);
			setOverview(loaded);
			setEvents(catalog);
		} catch (error) {
			console.error("Event claims could not be loaded", error);
			setError(
				error instanceof Error
					? error.message
					: "Event claims could not be loaded.",
			);
		} finally {
			setRefreshing(false);
		}
	}, [admin]);

	useEffect(() => {
		if (!loading && allowed) void refresh();
	}, [loading, allowed, refresh]);

	if (loading)
		return <BodyShort role="status">Loading membership...</BodyShort>;
	if (!allowed)
		return (
			<BodyShort>
				{admin
					? "Administrator access is required."
					: "Active program membership is required to claim event credit."}
			</BodyShort>
		);

	function updated(claim: EventClaim) {
		setOverview((current) =>
			current
				? {
						...current,
						claims: current.claims.some((item) => item.id === claim.id)
							? current.claims.map((item) =>
									item.id === claim.id ? claim : item,
								)
							: [claim, ...current.claims],
					}
				: current,
		);
	}

	return (
		<VStack gap="space-32">
			<Heading size="xlarge" level="1">
				{admin ? "Review event claims" : "My event claims"}
			</Heading>
			{admin && (
				<BodyShort>
					Check delivered security content, advance network invitation and each
					person&apos;s contribution before approving. Each approved contributor
					receives the full configured credit in the event&apos;s season.
				</BodyShort>
			)}
			{error && <ErrorMessage role="alert">{error}</ErrorMessage>}
			{message && <BodyShort role="status">{message}</BodyShort>}
			<Button variant="secondary" loading={refreshing} onClick={refresh}>
				Refresh claims
			</Button>
			{!overview && !error && (
				<BodyShort role="status">Loading event claims...</BodyShort>
			)}
			{overview && (
				<>
					{!admin && !editing && (
						<Button
							onClick={() => {
								setMessage("");
								setEditing("new");
							}}
						>
							Claim event credit
						</Button>
					)}
					{editing && (
						<EventClaimForm
							key={editing === "new" ? "new" : editing.id}
							overview={overview}
							events={events}
							claim={editing === "new" ? undefined : editing}
							onCancel={() => setEditing(null)}
							onSaved={(claim) => {
								updated(claim);
								setEditing(null);
								setMessage("Claim submitted for administrator review.");
							}}
						/>
					)}
					{overview.claims.length === 0 && (
						<BodyShort>No event claims yet.</BodyShort>
					)}
					{overview.claims.map((claim) => (
						<ClaimCard
							key={claim.id}
							claim={claim}
							overview={overview}
							admin={admin}
							onUpdated={updated}
							onEdit={setEditing}
						/>
					))}
				</>
			)}
		</VStack>
	);
}

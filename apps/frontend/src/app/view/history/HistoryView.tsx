"use client";

import { useEffect, useState } from "react";
import { BodyShort, Button, Heading, HStack, TextField, VStack } from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import { HistoryEntry, HistoryPage } from "@/app/utils/Variables";

function readable(value: string): string {
	const text = value
		.replaceAll("_", " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase();
	return text.charAt(0).toUpperCase() + text.slice(1);
}

function time(value: string): string {
	return new Date(value).toLocaleString("nb-NO", { timeZone: "Europe/Oslo" });
}

const creditLabels: Record<string, string> = {
	SLACK_WEEK: "Weekly Slack participation",
	DELTA_REGISTRATION: "Delta event registration (not confirmed attendance)",
	GITHUB_COMMIT: "GitHub commit",
	GITHUB_PULL_REQUEST: "GitHub pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security-event contribution",
};

export function HistoryView({ admin = false }: { admin?: boolean }) {
	const [page, setPage] = useState<HistoryPage | null>(null);
	const [query, setQuery] = useState("");
	const [search, setSearch] = useState("");
	const [cursor, setCursor] = useState<string | null>(null);
	const [previousCursors, setPreviousCursors] = useState<(string | null)[]>([]);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [retry, setRetry] = useState(0);

	useEffect(() => {
		let current = true;
		setLoading(true);
		setFailed(false);
		Apies.getHistory(admin, search, cursor)
			.then((result) => {
				if (current) setPage(result);
			})
			.catch((error) => {
				console.error("Failed to load history:", error);
				if (current) setFailed(true);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, [admin, search, cursor, retry]);

	return (
		<VStack gap="space-24" style={{ overflowWrap: "anywhere" }}>
			<Heading level="1" size="xlarge">
				{admin ? "Audit trail" : "My history"}
			</Heading>
			<BodyShort>
				History starts when audit logging was introduced; earlier changes are not included. Capture is best-effort, so
				some changes may be missing.
				{admin && " Operational records are retained for 12 months."}
			</BodyShort>
			{admin && (
				<form
					onSubmit={(event) => {
						event.preventDefault();
						setSearch(query.trim());
						setCursor(null);
						setPreviousCursors([]);
						setRetry((value) => value + 1);
					}}
				>
					<HStack gap="space-16" align="end">
						<TextField label="Search audit trail" maxLength={100} value={query}
							onChange={(event) => setQuery(event.target.value)} />
						<Button type="submit">Search</Button>
					</HStack>
				</form>
			)}
			{loading ? (
				<BodyShort role="status">Loading history...</BodyShort>
			) : failed ? (
				<VStack gap="space-16" align="start">
					<BodyShort role="alert">We couldn't fetch history. Try again.</BodyShort>
					<Button onClick={() => setRetry((value) => value + 1)}>Retry</Button>
				</VStack>
			) : (
				page && (
					<>
						{page.entries.length === 0 ? (
							<BodyShort>No history recorded.</BodyShort>
						) : (
							<VStack as="ol" gap="space-24" aria-label={admin ? "Audit entries" : "Your history"}>
								{page.entries.map((entry: HistoryEntry) => (
									<li key={entry.id}>
										<VStack gap="space-8">
											<Heading level="2" size="small">
												{readable(entry.action)}
											</Heading>
											<BodyShort>
												<time dateTime={entry.recordedAt}>{time(entry.recordedAt)}</time>
												{" — "}
												{readable(entry.outcome)}
											</BodyShort>
											{entry.occurredAt && entry.occurredAt !== entry.recordedAt && (
												<BodyShort>
													Activity time: <time dateTime={entry.occurredAt}>{time(entry.occurredAt)}</time>
												</BodyShort>
											)}
											{admin && (
												<BodyShort>
													Actor: {entry.actor || "System / erased identity"}
													{entry.participantId && ` · Participant: ${entry.participantId}`}
													{entry.runId && ` · Run: ${entry.runId}`}
												</BodyShort>
											)}
											<dl>
												{Object.entries(entry.details)
													.filter(([, value]) => value !== null)
													.map(([key, value]) => (
														<div key={key}>
															<dt>{readable(key)}</dt>
															<dd>
																{key === "creditType" && typeof value === "string"
																	? creditLabels[value] || readable(value)
																	: String(value)}
															</dd>
														</div>
													))}
											</dl>
										</VStack>
									</li>
								))}
							</VStack>
						)}
						<HStack gap="space-16" aria-label="History pages">
							{previousCursors.length > 0 && (
								<Button
									variant="secondary"
									onClick={() => {
										setCursor(previousCursors[previousCursors.length - 1]);
										setPreviousCursors((values) => values.slice(0, -1));
									}}
								>
									Newer entries
								</Button>
							)}
							{page.nextCursor && (
								<Button
									variant="secondary"
									onClick={() => {
										setPreviousCursors((values) => [...values, cursor]);
										setCursor(page.nextCursor);
									}}
								>
									Older entries
								</Button>
							)}
						</HStack>
					</>
				)
			)}
		</VStack>
	);
}

type Source = {
	displayName?: string | null;
	sourceUrl?: string | null;
	sourceOccurredAt?: string | null;
	creditType?: string | null;
};

const sourceDate = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	dateStyle: "medium",
	timeStyle: "short",
});

export function sourceDescription(source: Source): string | null {
	if (source.displayName) return source.displayName;
	if (
		source.creditType === "GITHUB_COMMIT" ||
		source.creditType === "GITHUB_PULL_REQUEST"
	) {
		return "Change details unavailable";
	}
	if (
		source.creditType === "DELTA_REGISTRATION" ||
		source.creditType === "SECURITY_EVENT_CONTRIBUTION"
	) {
		return "Event details unavailable";
	}
	return null;
}

export function safeSourceUrl(value?: string | null): string | null {
	if (!value) return null;
	try {
		const url = new URL(value);
		return ["https:", "http:"].includes(url.protocol) &&
			!url.username &&
			!url.password
			? url.href
			: null;
	} catch {
		return null;
	}
}

export function CreditSource(source: Source) {
	const description = sourceDescription(source);
	const url = safeSourceUrl(source.sourceUrl);
	if (!description && !source.sourceOccurredAt && !url) return null;
	return (
		<span>
			{url ? <a href={url}>{description ?? "View source"}</a> : description}
			{source.sourceOccurredAt && (
				<>
					{" · "}
					{source.creditType === "DELTA_REGISTRATION" ||
					source.creditType === "SECURITY_EVENT_CONTRIBUTION"
						? "Event"
						: "Activity"}
					{": "}
					<time dateTime={source.sourceOccurredAt}>
						{sourceDate.format(new Date(source.sourceOccurredAt))}
					</time>
				</>
			)}
		</span>
	);
}

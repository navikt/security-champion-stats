"use client";

import { BodyShort } from "@navikt/ds-react";
import { useEffect, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import type { AdminDashboardOverview } from "@/app/utils/Variables";
import {
	AdminDashboardView,
	type ScoringIntegration,
} from "@/app/view/appsec/dashboard/AdminDashboardView";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";

export default function Page() {
	const { me, loading } = useMe();
	const [overview, setOverview] = useState<AdminDashboardOverview | null>(null);
	const [failed, setFailed] = useState(false);
	const [triggeringSync, setTriggeringSync] = useState<ScoringIntegration | null>(
		null,
	);
	const [triggerError, setTriggerError] = useState<{
		integration: ScoringIntegration;
		message: string;
	} | null>(null);
	const [pendingSync, setPendingSync] = useState<{
		integration: ScoringIntegration;
		lastAttemptAt: string | null;
	} | null>(null);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		let mounted = true;
		Apies.getAdminDashboard()
			.then((result) => {
				if (!mounted) return;
				setOverview(result);
				setFailed(result === null);
			})
			.catch(() => {
				if (!mounted) return;
				setFailed(true);
			});
		return () => {
			mounted = false;
		};
	}, [loading, me.isAdmin]);

	useEffect(() => {
		if (
			!overview ||
			(!pendingSync &&
				overview.slack.outcome !== "RUNNING" &&
				overview.delta.outcome !== "RUNNING" &&
				overview.github.outcome !== "RUNNING")
		) {
			return;
		}

		const interval = setInterval(() => {
			Apies.getAdminDashboard()
				.then((result) => {
					if (!result) return;
					setOverview(result);
					if (pendingSync) {
						const currentStatus = result[pendingSync.integration];
						if (
							currentStatus.outcome !== "RUNNING" &&
							currentStatus.lastAttemptAt !== pendingSync.lastAttemptAt
						) {
							setPendingSync(null);
						}
					}
				})
				.catch((error) => {
					console.error("Failed to refresh dashboard sync status:", error);
				});
		}, 3000);

		return () => clearInterval(interval);
	}, [overview, pendingSync]);

	const triggerSync = async (integration: ScoringIntegration) => {
		setTriggeringSync(integration);
		setTriggerError(null);

		try {
			const status =
				integration === "slack"
					? await Apies.triggerSlackSync()
					: integration === "github"
						? await Apies.triggerGithubSync()
						: await Apies.triggerDeltaSync();
			if (status === 202) {
				setPendingSync({
					integration,
					lastAttemptAt: overview?.[integration].lastAttemptAt ?? null,
				});
				try {
					const refreshed = await Apies.getAdminDashboard();
					if (refreshed) setOverview(refreshed);
				} catch (error) {
					console.error("Failed to refresh dashboard sync status:", error);
				}
			} else {
				setPendingSync(null);
				setTriggerError({
					integration,
					message:
						status === 409
							? "A sync is already running or this integration is disabled."
							: "We couldn't start the sync. Try again later.",
				});
				if (status === 409) {
					try {
						const refreshed = await Apies.getAdminDashboard();
						if (refreshed) setOverview(refreshed);
					} catch (error) {
						console.error("Failed to refresh dashboard sync status:", error);
					}
				}
			}
		} catch (error) {
			console.error(`Failed to trigger ${integration} sync:`, error);
			setPendingSync(null);
			setTriggerError({
				integration,
				message: "We couldn't start the sync. Try again later.",
			});
		} finally {
			setTriggeringSync(null);
		}
	};

	if (loading || (me.isAdmin && overview === null && !failed)) {
		return <Loading />;
	}
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || overview === null) {
		return (
			<BodyShort role="alert">
				We couldn't fetch the program dashboard. Try again later.
			</BodyShort>
		);
	}
	return (
		<AdminDashboardView
			overview={overview}
			onTriggerSync={triggerSync}
			triggeringSync={triggeringSync}
			triggerError={triggerError}
		/>
	);
}

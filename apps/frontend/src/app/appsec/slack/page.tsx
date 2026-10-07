"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import { AdminProgramParticipant, SlackMappingOverview } from "@/app/utils/Variables";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";
import { ManageSlackMappingsView } from "@/app/view/appsec/slack/ManageSlackMappingsView";
import { ManageSlackMembershipView } from "@/app/view/appsec/slack/ManageSlackMembershipView";
import { BodyShort, VStack } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";

export default function Page() {
	const { me, loading } = useMe();
	const [participants, setParticipants] = useState<AdminProgramParticipant[] | null>(null);
	const [overview, setOverview] = useState<SlackMappingOverview | null>(null);
	const [failed, setFailed] = useState(false);

	const refresh = useCallback(async () => {
		try {
			const [participantResult, mappingResult] = await Promise.all([
				Apies.getAdminParticipants(),
				Apies.getSlackMappingOverview(),
			]);
			setParticipants(participantResult);
			setOverview(mappingResult);
			setFailed(participantResult === null || mappingResult === null);
		} catch {
			setFailed(true);
		}
	}, []);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		void refresh();
	}, [loading, me.isAdmin, refresh]);

	if (loading || (me.isAdmin && (participants === null || overview === null) && !failed)) {
		return <Loading />;
	}
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || participants === null || overview === null) {
		return (
			<BodyShort role="alert">
				We couldn't fetch Slack mappings and review items. Try again later.
			</BodyShort>
		);
	}
	return (
		<VStack gap="space-32">
			<ManageSlackMappingsView
				participants={participants}
				overview={overview}
				onRefresh={refresh}
			/>
			<ManageSlackMembershipView participants={participants} onRefresh={refresh} />
		</VStack>
	);
}

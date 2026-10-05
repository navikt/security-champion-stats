import { BodyShort } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";
import { Me, ProgramParticipant } from "@/app/utils/Variables";
import Loading from "@/app/view/Loading";
import { Apies } from "@/app/shared/hooks/Apies";
import { JoinedMembershipView } from "@/app/view/member/components/JoinedMembershipView";
import { JoinProgramView } from "@/app/view/member/components/JoinProgramView";
import "../../../style/home/MembershipView.css";

export function MembershipView({ me }: { me: Me }) {
	const [userData, setMe] = useState(me);
	const [loading, setLoading] = useState(me.isParticipant);
	const [participant, setParticipant] = useState<ProgramParticipant | null>(null);
	const [fetchFailed, setFetchFailed] = useState(false);

	const refreshMembership = useCallback(async () => {
		try {
			const updatedMe = await Apies.validatePerson();
			setMe(updatedMe);
			if (!updatedMe.isParticipant) {
				setParticipant(null);
				setFetchFailed(false);
				return;
			}
			const result = await Apies.fetchMembership();
			setParticipant(result);
			setFetchFailed(result === null);
		} catch {
			setFetchFailed(true);
		}
	}, []);

	useEffect(() => {
		if (!me.isParticipant) return;
		refreshMembership().finally(() => setLoading(false));
	}, [me.isParticipant, refreshMembership]);

	const enroll = async () => {
		setLoading(true);
		try {
			const enrolled = await Apies.joinProgram();
			if (!enrolled) return false;
			await refreshMembership();
			return true;
		} finally {
			setLoading(false);
		}
	};

	if (loading) return <Loading />;
	if (!userData.isParticipant) return <JoinProgramView onEnroll={enroll} />;
	if (fetchFailed || !participant) {
		return (
			<BodyShort>
				We couldn't fetch your participant details. Try again later.
			</BodyShort>
		);
	}

	return <JoinedMembershipView participant={participant} />;
}

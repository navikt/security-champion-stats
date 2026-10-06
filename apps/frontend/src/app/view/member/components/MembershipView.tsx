import { BodyShort } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";
import { Me, ProgramParticipant } from "@/app/utils/Variables";
import Loading from "@/app/view/Loading";
import { Apies } from "@/app/shared/hooks/Apies";
import { JoinedMembershipView } from "@/app/view/member/components/JoinedMembershipView";
import { JoinProgramView } from "@/app/view/member/components/JoinProgramView";
import { LeaveProgramModal } from "@/app/view/member/modal/LeaveProgramModal";
import "../../../style/home/MembershipView.css";

export function MembershipView({ me, onMembershipChanged }: { me: Me; onMembershipChanged?: (updated: Me) => void }) {
	const [userData, setMe] = useState(me);
	const [loading, setLoading] = useState(me.isParticipant);
	const [participant, setParticipant] = useState<ProgramParticipant | null>(null);
	const [fetchFailed, setFetchFailed] = useState(false);
	const [leaveOpen, setLeaveOpen] = useState(false);
	const [pending, setPending] = useState(false);
	const [actionError, setActionError] = useState<string | null>(null);

	const refreshMembership = useCallback(async () => {
		try {
			const result = await Apies.fetchMembership();
			if (!result) throw new Error("Failed to fetch membership");
			const updatedMe = { ...me, isParticipant: true, isActive: result.active };
			setMe(updatedMe);
			onMembershipChanged?.(updatedMe);
			setParticipant(result);
			setFetchFailed(false);
			return true;
		} catch (error) {
			console.error("Failed to refresh membership:", error);
			setFetchFailed(true);
			return false;
		}
	}, [me, onMembershipChanged]);

	useEffect(() => {
		if (!me.isParticipant) return;
		refreshMembership().finally(() => setLoading(false));
	}, [me.isParticipant, refreshMembership]);

	const enroll = async () => {
		const enrolled = await Apies.joinProgram();
		if (!enrolled) return false;
		return refreshMembership();
	};

	const changeParticipation = async (leave: boolean) => {
		if (pending) return;
		setPending(true);
		setActionError(null);
		try {
			if (leave) await Apies.leaveProgram();
			else if (!(await Apies.joinProgram())) throw new Error("We couldn't rejoin the program. Try again.");
			const updatedMe = { ...userData, isActive: !leave };
			setMe(updatedMe);
			onMembershipChanged?.(updatedMe);
			setLeaveOpen(false);
			await refreshMembership();
		} catch (error) {
			console.error("Failed to change participation:", error);
			setActionError(error instanceof Error ? error.message : "We couldn't change your participation. Try again.");
		} finally {
			setPending(false);
		}
	};

	if (loading) return <Loading />;
	if (!userData.isParticipant) return <JoinProgramView onEnroll={enroll} />;
	if (fetchFailed || !participant) {
		return <BodyShort>We couldn't fetch your participant details. Try again later.</BodyShort>;
	}

	return (
		<>
			<JoinedMembershipView
				participant={participant}
				onLeave={() => {
					setActionError(null);
					setLeaveOpen(true);
				}}
				onRejoin={() => changeParticipation(false)}
				pending={pending}
				error={leaveOpen ? null : actionError}
			/>
			<LeaveProgramModal
				open={leaveOpen}
				onClose={() => setLeaveOpen(false)}
				onConfirm={() => changeParticipation(true)}
				loading={pending}
				error={actionError}
			/>
		</>
	);
}

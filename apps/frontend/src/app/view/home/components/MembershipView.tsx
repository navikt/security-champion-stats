import {useEffect, useState} from "react";
import "../../../style/home/MembershipView.css"
import {Me, Member} from "@/app/utils/Variables";
import Loading from "@/app/view/Loading";
import {Apies} from "@/app/shared/hooks/Apies";
import {JoinedMembershipView} from "@/app/view/home/components/JoinedMembershipView";
import {JoinProgramView} from "@/app/view/home/components/JoinProgramView";
import {BoosterCard} from "@/app/view/home/components/BoosterCard";
import {ReferralCard} from "@/app/view/home/components/ReferralCard";

export function MembershipView({me, onMembershipChange}: {me: Me, onMembershipChange?: () => void}) {
    const [userData, setMe] = useState(me)
    const [loading, setLoading] = useState(me.isSecChamp)
    const [memberships, setMemberships] = useState<Member | null>()

    const fetchMembership = async () => {
        const member = await Apies.fetchMembership()
        const updatedMe = await Apies.validatePerson()
        setMemberships(member)
        setMe(updatedMe)
    }

    const refreshMembership = async () => {
        await fetchMembership()
        onMembershipChange?.()
    }

    useEffect(() => {
        if (!me.isSecChamp) return;

        fetchMembership().then(() => setLoading(false))
    }, [me.isSecChamp])

    if (loading) return <Loading />

    if (userData.isSecChamp && memberships) {
        return (
            <div className={"sc-membership-stack"}>
                <JoinedMembershipView member={memberships} onMembershipChange={refreshMembership} />
                <div className={"sc-membership-row"}>
                    <BoosterCard onRedeemed={refreshMembership} />
                    <ReferralCard onClaimed={refreshMembership} />
                </div>
            </div>
        )
    }

    return (
        <JoinProgramView />
    )
}
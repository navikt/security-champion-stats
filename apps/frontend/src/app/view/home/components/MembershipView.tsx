import {useEffect, useState} from "react";
import "../../../style/MembershipView.css"
import {Me, Member} from "@/app/utils/Variables";
import Loading from "@/app/view/Loading";
import {Apies} from "@/app/shared/hooks/Apies";
import {JoinedMembershipView} from "@/app/view/home/components/JoinedMembershipView";
import {JoinProgramView} from "@/app/view/home/components/JoinProgramView";

export function MembershipView({me}: {me: Me}) {
    const [userData, setMe] = useState(me)
    const [loading, setLoading] = useState(me.isSecChamp)
    const [memberships, setMemberships] = useState<Member | null>()

    useEffect(() => {
        if (!me.isSecChamp) return;

        const fetchMembership = async () => {
            const member = await Apies.fetchMembership()
            setMemberships(member)
        }

        fetchMembership().then(() => setLoading(false))
    }, [me.isSecChamp])

    if (loading) return <Loading />

    if (userData.isSecChamp && memberships) {
        return <JoinedMembershipView member={memberships} />
    }

    return (
        <JoinProgramView />
    )
}
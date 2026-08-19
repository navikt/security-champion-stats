import {useState} from "react";
import {Me, Member} from "@/app/utils/Variables";
import Loading from "@/app/view/mainView/components/Loading";
import {Apies} from "@/app/shared/hooks/Apies";
import {JoinedMembershipView} from "@/app/view/mainView/components/JoinedMembershipView";
import {JoinProgramView} from "@/app/view/mainView/components/JoinProgramView";

export function MembershipView({me}: {me: Me}) {
    const [userData, setMe] = useState(me)
    const [loading, setLoading] = useState(me.isSecChamp)
    const [memberships, setMemberships] = useState<Member | null>()

    const fetchMembership = async () => {
        const member = await Apies.fetchMembership()
        setMemberships(member)
        setLoading(false)
    }

    while (loading) {
        return <Loading />
    }

    if (userData.isSecChamp && memberships) {
        return <JoinedMembershipView member={memberships} />
    }

    return (
        <JoinProgramView />
    )
}
"use client";

import Loading from "../loading";
import {UserView} from "../shared/view/DashboardView";
import {useMe} from "../shared/hooks/useMe";

export default function Page() {
    const { me, loading } = useMe();
    if (loading) return <Loading />
    return <UserView info={ me }/>
}
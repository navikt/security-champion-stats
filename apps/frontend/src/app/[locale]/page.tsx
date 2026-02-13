"use client";

import {useAuth} from "../shared/hooks/useAuth";
import Loading from "../loading";
import {UserView} from "../shared/view/DashboardView";

export default function Page() {
    const { me, loading } = useAuth()
    if (loading) return <Loading />
    return <UserView info={ me ?? { username: "", isAdmin: false, inProgram: false } }/>
}
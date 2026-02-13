"use client"

import {useAuth} from "../../shared/hooks/useAuth";
import Loading from "../../loading";
import {AdminView, UserView} from "../../shared/view/DashboardView";

export default function AdminPage() {
    const { me, loading } = useAuth()
    if (loading) return <Loading />
    if (!me?.isAdmin) return <UserView info={ me ?? { username: "", isAdmin: false, inProgram: false }}/>;
    return <AdminView info={me ?? { username: "", isAdmin: false, inProgram: false }}/>
}
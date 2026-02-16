"use client"

import Loading from "../../loading";
import {AdminView, UserView} from "../../shared/view/DashboardView";
import {useMe} from "../../shared/hooks/useMe";

export default function AdminPage() {
    const { me, loading } = useMe();
    if (loading) return <Loading />
    if (!me?.isAdmin) return <UserView info={ me }/>;
    return <AdminView info={ me }/>
}
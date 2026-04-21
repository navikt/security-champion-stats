"use client";

import Loading from "../shared/components/Loading";
import {MainView} from "../shared/view/HomeView";
import {useMe} from "../shared/hooks/UseMe";

export default function Page() {
    const { me, loading } = useMe();
    if (loading) return <Loading />
    return <MainView info={ me }/>
}
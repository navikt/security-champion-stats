"use client";

import Loading from "../view/mainView/components/Loading";
import {MainView} from "../view/mainView/HomeView";
import {useMe} from "../shared/hooks/UseMe";

export default function Page() {
    const { me, loading } = useMe();
    if (loading) return <Loading />
    return <MainView info={ me }/>
}
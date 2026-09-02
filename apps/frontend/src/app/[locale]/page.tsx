"use client";

import Loading from "@/app/view/Loading";
import {MainView} from "@/app/view/home/HomeView";
import {useMe} from "../shared/hooks/UseMe";

export default function Page() {
    const { me, loading } = useMe();
    console.log("I feteched me value: " + me)
    if (loading) return <Loading />
    return <MainView info={ me }/>
}
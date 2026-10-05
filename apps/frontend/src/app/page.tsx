"use client";

import Loading from "@/app/view/Loading";
import { MainView } from "@/app/view/HomeView";
import { useMe } from "./shared/hooks/UseMe";

export default function Page() {
	const { me, loading } = useMe();
	if (loading) return <Loading />;
	return <MainView info={me} />;
}

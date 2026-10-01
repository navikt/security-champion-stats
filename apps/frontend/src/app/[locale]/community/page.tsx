"use client";

import { useEffect, useState } from "react";
import { Member } from "@/app/utils/Variables";
import { Apies } from "@/app/shared/hooks/Apies";
import { CommunityView } from "@/app/view/community/CommunityView";
import Loading from "@/app/view/Loading";

export default function CommunityPage() {
	const [members, setMembers] = useState<Member[] | null>(null);

	useEffect(() => {
		Apies.getMembers().then((response) => setMembers(response));
	}, []);

	if (members == null) return <Loading />;

	return <CommunityView members={members} />;
}

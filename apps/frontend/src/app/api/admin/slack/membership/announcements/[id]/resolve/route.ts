import type { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function POST(
	request: NextRequest,
	{ params }: { params: Promise<{ id: string }> },
) {
	const { id } = await params;
	return proxyBackendRequest(
		request,
		`/api/admin/slack/membership/announcements/${encodeURIComponent(id)}/resolve`,
	);
}

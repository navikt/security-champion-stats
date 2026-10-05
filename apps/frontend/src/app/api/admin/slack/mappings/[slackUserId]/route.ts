import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function DELETE(
	request: NextRequest,
	{ params }: { params: Promise<{ slackUserId: string }> },
) {
	const { slackUserId } = await params;
	return proxyBackendRequest(
		request,
		`/api/admin/slack/mappings/${encodeURIComponent(slackUserId)}`,
	);
}

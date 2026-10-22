import type { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(
	request: NextRequest,
	{ params }: { params: Promise<{ id: string }> },
) {
	const { id } = await params;
	return proxyBackendRequest(
		request,
		`/api/admin/feedback/${encodeURIComponent(id)}`,
	);
}

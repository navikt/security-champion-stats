import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function PUT(
	request: NextRequest,
	{ params }: { params: Promise<{ id: string }> },
) {
	const { id } = await params;
	return proxyBackendRequest(
		request,
		`/api/admin/delta/event-mappings/${encodeURIComponent(id)}/category`,
	);
}

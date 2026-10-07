import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	return proxyBackendRequest(
		request,
		`/api/admin/dashboard/members${new URL(request.url).search}`,
	);
}

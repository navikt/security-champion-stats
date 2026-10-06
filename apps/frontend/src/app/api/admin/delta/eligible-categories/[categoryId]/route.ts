import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function DELETE(
	request: NextRequest,
	{ params }: { params: Promise<{ categoryId: string }> },
) {
	const { categoryId } = await params;
	return proxyBackendRequest(
		request,
		`/api/admin/delta/eligible-categories/${encodeURIComponent(categoryId)}`,
	);
}

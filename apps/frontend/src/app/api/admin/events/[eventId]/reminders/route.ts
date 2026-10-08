import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

type Context = { params: Promise<{ eventId: string }> };

export async function GET(request: NextRequest, context: Context) {
	const { eventId } = await context.params;
	return proxyBackendRequest(
		request,
		`/api/admin/events/${encodeURIComponent(eventId)}/reminders`,
	);
}

export async function POST(request: NextRequest, context: Context) {
	const { eventId } = await context.params;
	return proxyBackendRequest(
		request,
		`/api/admin/events/${encodeURIComponent(eventId)}/reminders`,
	);
}

import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "@/app/shared/utils/validation";
import {mockMembers} from "@/app/mocks/mockPayloads";
import {AUTHENTICATED_FAILED, FAILED_TO_LEAVE} from "@/app/shared/utils/variable";

export async function POST(request: NextRequest) {
    const body = await request.json()
    const { email } = body

    if (activeMock()) {
        const updatedMembers = mockMembers.members.filter(
            member => member.email !== email
        )
        return new Response(JSON.stringify(updatedMembers))
    }
    try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: "Authentication failed, failed to fetch obo-token or token" },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/leave`
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${backendToken}`,
                "Content-Type": "application/json"
            },
            body: email
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_TO_LEAVE },
                { status: response.status }
            )
        }
        return NextResponse.json(
            { status: response.status },
        )
    } catch (error) {
        console.error("Error in /api/leave:", error)
        return NextResponse.json(
            { error: "Failed to leave program, due to an internal error" },
            { status: 500}
        )
    }
}
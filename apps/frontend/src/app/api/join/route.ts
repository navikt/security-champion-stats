import {activeMock, getBackendToken, getServerEnv} from "../../shared/utils/Validation";
import {mockMembers} from "../../mocks/MockPayloads";
import {NextRequest, NextResponse} from "next/server";
import {AUTHENTICATED_FAILED, FAILED_TO_JOIN} from "../../shared/utils/Variables";

export async function POST(request: NextRequest) {
    const body = await request.json()
    const {email} = body

    if (activeMock()) {
        return NextResponse.json(mockMembers)
    }
    try {
        const {backendUrl} = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                {error: "Authentication failed, failed to fetch obo-token or token" },
                {status: 401 },
            )
        }

        const url = `${backendUrl}/api/join`
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
                {error: FAILED_TO_JOIN },
                {status: response.status}
            )
        }
        return NextResponse.json( { status: response.status } )
    } catch (error) {
        console.error("Error in /api/join:", error)
        return NextResponse.json(
            {error: FAILED_TO_JOIN},
            { status: 500 }
        )
    }
}
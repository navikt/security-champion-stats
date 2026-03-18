import {activeMock, getBackendToken, getServerEnv} from "../../shared/utils/validation";
import {mockMembers} from "../../mocks/mockPayloads";
import {NextRequest, NextResponse} from "next/server";
import {AUTHENTICATED_FAILED, FAILED_TO_JOIN} from "../../shared/utils/variable";

export async function POST(request: NextRequest) {
    const body = await request.json()
    const {email} = body

    if (activeMock()) {
        const updatedMembers = mockMembers.members.map(member => {
            if (member.email === email) {
                member.inProgram = true
            }
        })
        return new Response(JSON.stringify(updatedMembers))
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
import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../shared/utils/validation";
import {mockMembers} from "../../../mocks/mockPayloads";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "../../../shared/utils/variable";

export async function POST(request: NextRequest) {
    const body = await request.json()
    const { email } = body

    if (activeMock()) {
        const updatedMembers = mockMembers.members.push(
            {
                id: "String-3",
                fullname: "mock-3",
                points: 0,
                email: email,
                inProgram: true
            }
        )
        return NextResponse.json(updatedMembers)
    } try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url =`${backendUrl}/api/admin/member`
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${backendUrl}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({email})
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }
        return NextResponse.json(response)
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
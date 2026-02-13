import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../shared/utils/validation";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "../../../shared/utils/variable";
import {mockMembers} from "../../../mocks/mockPayloads";

export async function POST(
    request: NextRequest
) {
    const body = await request.json()
    const { email, amount } = body
    if (activeMock()) {
        const updatedMember = mockMembers.members.map(m => {
            if (m.email === email) { m.points += amount }
        })
        return NextResponse.json(updatedMember)
    } try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/points`
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${backendUrl}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({email, amount})
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }
        return NextResponse.json({ status: "success" })
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
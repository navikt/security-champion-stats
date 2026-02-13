import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../../shared/utils/validation";
import {
    AUTHENTICATED_FAILED,
    DeleteParam,
    FAILED_FETCH,
    INTERNAL_ERROR,
    MISSING_VALUE
} from "../../../../shared/utils/variable";
import {mockMembers} from "../../../../mocks/mockPayloads";


export async function DELETE(
    request: NextRequest,
    ctx: RouteContext<"/api/admin/member/[email]">
) {
    const { email } = await ctx.params

    if (activeMock()) {
        const members = mockMembers

        if (!email) {
            return NextResponse.json(
                { error: MISSING_VALUE },
                { status: 400 }
            )
        }
        members.members.filter(
            members => members.email !== email
        )
        return NextResponse.json(members)
    }
    try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/member?${email}`

        const response = await fetch(url, {
            method: 'DELETE',
            headers: {
                Authorization: `Bearer ${backendUrl}`,
                "Content-Type": "application/json"
            }
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
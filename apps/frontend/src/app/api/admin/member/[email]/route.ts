import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../../shared/utils/Validation";
import {
    AUTHENTICATED_FAILED,
    FAILED_FETCH,
    INTERNAL_ERROR,
    MISSING_VALUE
} from "../../../../shared/utils/Variables";
import {mockMembers} from "../../../../mocks/MockPayloads";


export async function DELETE(
    request: NextRequest,
    ctx: RouteContext<"/api/admin/member/[email]">
) {
    const { email } = await ctx.params

    if (activeMock()) {
        return NextResponse.json(mockMembers)
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
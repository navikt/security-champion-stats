import {NextRequest, NextResponse} from "next/server";
import {getBackendToken, getServerEnv} from "../../../../shared/utils/Validation";
import {
    AUTHENTICATED_FAILED,
    FAILED_FETCH,
    INTERNAL_ERROR,
} from "../../../../shared/utils/Variables";


export async function DELETE(
    request: NextRequest,
    ctx: RouteContext<"/api/admin/member/[id]">
) {
    const { id } = await ctx.params
    try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            console.log("Authentication failed when trying to delete member with email: ", id)
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/member/${id}`
        const response = await fetch(url, {
            method: 'DELETE',
            headers: {
                Authorization: `Bearer ${backendToken}`,
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
import {ChallengeMember, Me, Member, AppSecDashboard, SCData, InviteResponse, BoosterToken, ReferralCertificate} from "../../utils/Variables";

export const Apies = {
    getMembers: async (): Promise<Member[]> => {
        const res = await fetch("/api/members")
        if (!res.ok) {
            console.warn("Failed to fetch members, status: ", res.status)
            return []
        }
        return await res.json()
    },
    addMember: async (email: string) => {
        const res = await fetch(
            "/api/admin/member", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "email": email }),
        })
        if (!res.ok) console.error("Failed to add member, with status code ", res.status)
        return res.status
    },
    deleteMember: async (id: string) => {
        const res = await fetch(`/api/admin/member/${encodeURIComponent(id)}`, {
            method: "DELETE",
        })
        if (!res.ok) console.error("Failed to delete member, with status code ", res.status)
        return res.status
    },
    addPoints: async (email: string, amount: number) => {
        const res = await fetch("/api/admin/points", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "email": email, "amount": amount }),
        })
        if (!res.ok) console.error("Failed to add points, with status code: ", res.status)
        return res.status
    },
    joinGamification: async() => {
        const res = await fetch("/api/joinGame", {
            method: "POST",
            headers: { "Content-Type": "application/json" }, // fixed typo
        })

        if (!res.ok) console.error("Failed to join a member to the program, with status code: ", res.status)
        return res.status
    },
    validatePerson: async(): Promise<Me> => {
        const res = await fetch("/api/validate")
        if (!res.ok) {
            console.error("Failed to validate user, status: ", res.status)
            return { username: "", isAdmin: false, isSecChamp: false, inGame: false }
        }

        return await res.json()
    },
    leaveGame: async(): Promise<Number> => {
        const res = await fetch("/api/leaveGame", { method: "POST" })
        if (!res.ok) console.error("Failed to leave the program, with status code: ", res.status)

        return res.status
    },
    getSCData: async(): Promise<SCData[]> => {
        const res = await fetch("/api/admin/dashboard/members")
        if (!res.ok) {
            console.error("Failed to fetch SCData, with status: ", res.status)
            return []
        }
        return res.json()
    },
    getAppSecDashboard: async(): Promise<AppSecDashboard | null> => {
        const res = await fetch("/api/appsec/dashboard")
        if (!res.ok) {
            console.error("Failed to fetch appsec dashboard, with status: ", res.status)
            return null
        }
        return res.json()
    },
    fetchMembership: async(): Promise<Member | null> => {
        const res = await fetch("/api/membership", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        })
        
        if (!res.ok) {
            console.error("Failed to fetch membership, with status: ", res.status)
            return null
        }
        return res.json()
    },
    upsertChallengeMember: async(member: ChallengeMember): Promise<number> => {
        const res = await fetch("/api/admin/challenges/member", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(member),
        })
        if (!res.ok) console.error("Failed to upsert challenge member, with status code: ", res.status)
        return res.status
    },
    inviteColleague: async(fullName: string, email: string, requesterEmail: string): Promise<InviteResponse> => {
        const res = await fetch("/api/invite", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ fullName, email, requesterEmail }),
        })
        return res.json()
    },
    claimActivityPoints: async(amount: number): Promise<InviteResponse> => {
        const res = await fetch("/api/activity/claim", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ amount }),
        })
        return res.json()
    },
    updateDisplayName: async(displayName: string): Promise<InviteResponse> => {
        const res = await fetch("/api/profile/displayname", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ displayName }),
        })
        return res.json()
    },
    getBoosterToken: async(): Promise<BoosterToken | null> => {
        const res = await fetch("/api/booster/mine")
        if (!res.ok) {
            console.error("Failed to fetch booster token, with status: ", res.status)
            return null
        }
        return res.json()
    },
    redeemBoosterToken: async(token: string): Promise<InviteResponse> => {
        const res = await fetch("/api/booster/redeem", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token }),
        })
        return res.json()
    },
    getReferralCertificate: async(): Promise<ReferralCertificate | null> => {
        const res = await fetch("/api/referral/mine")
        if (!res.ok) {
            console.error("Failed to fetch referral certificate, with status: ", res.status)
            return null
        }
        return res.json()
    },
    claimReferralBonus: async(data: string, signature: string): Promise<InviteResponse> => {
        const res = await fetch("/api/referral/claim", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ data, signature }),
        })
        return res.json()
    },
}
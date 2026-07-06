import {Me, Member, SCData} from "../utils/Variables";

export const Apies = {
    getMembers: async (): Promise<Member[]> => {
        const res = await fetch("/api/members")
        if (!res.ok) {
            console.warn("Failed to fetch members, status: ", res.status)
            return []
        }
        return await res.json()
    },
    addMember: async (email: string, fullname: string) => {
        const res = await fetch(
            "/api/admin/member", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "email": email, "fullname": fullname }),
        })
        if (!res.ok) console.error("Failed to add member, with status code ", res.status)
        return res.status
    },
    deleteMember: async (email: string) => {
        const res = await fetch(`/api/admin/member/${encodeURIComponent(email)}`, {
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
    joinProgram: async (email: string) => {
        const res = await fetch("/api/join", {
            method: "POST",
            headers: { "Content-Type": "application/json" }, // fixed typo
            body: JSON.stringify({ "email" : email }),
        })

        if (!res.ok) console.error("Failed to join a member to the program, with status code: ", res.status)
        return res.status
    },
    validatePerson: async(): Promise<Me> => {
        const res = await fetch("/api/validate")
        if (!res.ok) {
            console.error("Failed to validate user, status: ", res.status)
            return { username: "", isAdmin: false, inProgram: false }
        }
        return await res.json()
    },
    leaveProgram: async( email: string): Promise<Number> => {
        const res = await fetch("/api/leave", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "email": email })
        })
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
    }
}
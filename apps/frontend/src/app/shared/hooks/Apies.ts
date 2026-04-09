import {Me, Member} from "../utils/variable";

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
        if (!res.ok) console.warn("Failed to add member, with email: ", email)
        return res.status
    },
    deleteMember: async (email: string) => {
        const res = await fetch(`/api/admin/member/${email}`)
        if (!res.ok) console.warn("Failed to delete member, with email: ", email)
        return res.status
    },
    addPoints: async (email: string, amount: number) => {
        const res = await fetch("/api/admin/points", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "email": email, "amount": amount }),
        })
        if (!res.ok) console.warn("Failed to add points, for user: ", email)
        return res.status
    },
    joinProgram: async (email: string) => {
        const res = await fetch("/api/join", {
            method: "POST",
            headers: { "Content-Type": "application/json" }, // fixed typo
            body: JSON.stringify({ "email" : email }),
        })

        if (!res.ok) console.warn("Failed to join program, for user: ", email)
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
        if (!res.ok) {
            console.warn("Failed to leave program, for user: ", email)
        }
        return res.status
    }
}
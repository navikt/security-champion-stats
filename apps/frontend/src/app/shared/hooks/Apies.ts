import {Me} from "../utils/variable";

export const Apies = {
    getMembers: async () => {
        const res = await fetch("/api/members", { credentials: "include" })
        if (!res.ok) throw new Error("Failed to fetch members due to error")
        const data = await res.json()
        return data.members
    },
    addMember: async (email: string) => {
        const res = await fetch(
            "/api/admin/member", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email }),
            credentials: "include"
        })
        if (!res.ok) throw new Error("Failed to add member due to error")
        return res.status
    },
    deleteMember: async (email: string) => {
        const res = await fetch(`/api/admin/member/${email}`, {
            method: "DELETE",
            credentials: "include"
        })
        if (!res.ok) throw new Error("Failed to delete member due to error")
        return res.status
    },
    addPoints: async (email: string, amount: number) => {
        const res = await fetch("/api/admin/points", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, amount }),
            credentials: "include"
        })
        if (!res.ok) throw new Error(`Failed to add points for member ${email}`)
        return res.status
    },
    joinProgram: async (email: string) => {
        const res = await fetch("/api/join", {
            method: "POST",
            headers: { "Content-Type": "application/json" }, // fixed typo
            body: JSON.stringify({ email }),
            credentials: "include"
        })

        if (!res.ok) throw new Error(`Failed to join program for member ${email}`)
        return res.status
    },
    validatePerson: async(): Promise<Me> => {
        console.log("Validating user...")
        const res = await fetch("/api/validate", { credentials: "include" })
        console.log("Validation response status: ", res.status)
        console.log("Checking if response is ok: ", res.ok)
        if (!res.ok) {
            console.log("Failed to validate user, status: ", res.status)
            return { username: "", isAdmin: false, inProgram: false }
        }
        return await res.json()
    }
}
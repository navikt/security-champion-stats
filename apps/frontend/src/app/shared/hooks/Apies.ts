export const Apies = {
    getMembers: async () => {
        const res = await fetch("/api/members")
        if (!res.ok) throw new Error("Failed to fetch members due to error")
        const data = await res.json()
        return data.members
    },
    addMember: async (email: string) => {
        const res = await fetch("/api/admin/member", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email })
        })
        if (!res.ok) throw new Error("Failed to add member due to error")
        return res.status
    },
    deleteMember: async (email: string) => {
        const res = await fetch(`/api/admin/member/${email}`, {
            method: "DELETE",
        })
        if (!res.ok) throw new Error("Failed to delete member due to error")
        return res.status
    },
    addPoints: async (email: string, amount: number) => {
        const res = await fetch("/api/admin/points", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, amount })
        })
        if (!res.ok) throw new Error(`Failed to add points for member ${email}`)
        return res.status
    },
    joinProgram: async (email: string) => {
        const res = await fetch("/api/join", {
            method: "POST",
            headers: { "Constent-Type": "application/json" },
            body: JSON.stringify({ email })
        })

        if (!res.ok) throw new Error(`Failed to join program for member ${email}`)
        return res.status
    }
}
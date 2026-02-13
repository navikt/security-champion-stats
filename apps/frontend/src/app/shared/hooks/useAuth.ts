"use client";
import { useEffect, useState } from "react";
import {Me} from "../utils/variable";

export function useAuth() {
    const [me, setMe] = useState<Me | null>(null)
    const [loading, setLoading] = useState(true)
    const redirect_link = "/oauth2/login"

    useEffect(() => {
        const httpLink = window.location.origin + "/api/me"
        let cancelled = false;
        (async () => {
            console.log("The link looks like this: " + window.location.href)
            try {
                console.log("The link looks like this: " + window.location.href)
                const res = await fetch("/api/me", { method: "GET" })
                if (res.status === 401) {
                    window.location.href = redirect_link
                    return;
                }

                if (res.status === 403) {
                    window.location.href = "/"
                    return;
                }

                if (!res.ok) throw new Error("Failed /api/me");

                const data = (await res.json()) as Me
                console.log("Data from api/me, is " + data.isAdmin)
                console.log("keys:", data && typeof data === "object" ? Object.keys(data) : null);
                if (!cancelled) setMe(data)
            } catch (error) {
                console.log("Error fetching /api/me, redirecting to login, ", httpLink)
                console.log("Error fetching /api/me, redirecting to login fixed and edited, ", error)
            } finally {
                if (!cancelled) setLoading(false)
            }
        })();

        return () => {
            cancelled = true;
        }
    }, []);
    console.log("Me in useAuth is, ", me, loading)
    return { me, loading }
}
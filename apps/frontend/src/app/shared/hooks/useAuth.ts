"use client";
import { useEffect, useState } from "react";
import {Me} from "../utils/variable";

export function useAuth() {
    const [me, setMe] = useState<Me | null>(null)
    const [loading, setLoading] = useState(true)
    const redirect_link = "/oauth2/login"

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await fetch("/api/me", { method: "GET" })

                if (!res.ok) throw new Error("Failed /api/me");

                const data = (await res.json()) as Me
                if (!cancelled) setMe(data)
            } catch (error) {
                console.log("Error, failed validation due to en error: ", error)
            } finally {
                if (!cancelled) setLoading(false)
            }
        })();

        return () => {
            cancelled = true;
        }
    }, []);
    return { me, loading }
}
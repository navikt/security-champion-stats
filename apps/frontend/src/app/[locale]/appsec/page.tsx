"use client";

import { Line } from "react-chartjs-2"
import {
    Chart as ChartJsm,
    CategoryScale,
    LinearScale,
    BarElement,
    Title,
    Tooltip,
    Legend,
    PointElement,
    type ChartData,
    Filler,
    LineElement
} from "chart.js";
import { useEffect, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import Loading from "@/app/view/Loading";
import { lineOptions } from "@/app/style/char.js.stylling";
import { Box, Heading, BodyShort } from "@navikt/ds-react";
import { AppSecDashboard } from "@/app/utils/Variables";

ChartJsm.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend, PointElement, LineElement, Filler)

export default function Page() {
    const [dashboard, setDashboard] = useState<AppSecDashboard | null>(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let cancelled = false

        async function load() {
            try {
                setLoading(true)
                const res = await Apies.getAppSecDashboard()
                if (!cancelled) setDashboard(res)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        load()
        return () => {
            cancelled = true;
        }
    }, [])

    if (loading) return <Loading />
    if (!dashboard) return <div className={"dashboardView"}><p>Failed to load data</p></div>

    const lineData: ChartData<"line", number[], string> = {
        labels: dashboard.data.map((r) => r.timestamp),
        datasets: [
            {
                label: "Security Champions over time",
                data: dashboard.data.map((r) => r.amount),
                backgroundColor: "color-mix(in oklab, var(--accent) 22%, transparent)",
                borderColor: "var(--accent)",
                fill: true,
                tension: 0.35,
                pointRadius: 0,
                pointHitRadius: 12,
                pointHoverRadius: 4,
                borderWidth: 2
            }
        ]
    }

    return (
        <Box>
            <Heading size={"small"} spacing level={"2"}>
                AppSec internal dashboard
            </Heading>
            <BodyShort spacing>{dashboard.notice}</BodyShort>
            <div className={"adminChart"}>
                <Line data={lineData} options={lineOptions} />
            </div>
        </Box>
    )
}

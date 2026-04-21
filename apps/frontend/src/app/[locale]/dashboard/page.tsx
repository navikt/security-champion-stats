"use client";

import { Bar, Line } from "react-chartjs-2"
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
    LineElement
} from "chart.js";
import {useEffect, useState} from "react";
import {Row, SCData} from "@/app/shared/utils/Variables";
import {Apies} from "@/app/shared/hooks/Apies";
import Loading from "@/app/shared/components/Loading";
import {useTranslations} from "next-intl";
import {lineOptions} from "@/app/style/char.js.stylling";

ChartJsm.register(CategoryScale, LinearScale, BarElement, BarElement, Title, Tooltip, Legend, PointElement, LineElement)

export default function Page() {
    const [scData, setSCData] = useState<SCData[] | null>([])
    const [loading, setLoading] = useState(true)
    const t = useTranslations("admin")

    useEffect(() => {
        let cancelled = false

        async function load() {
            try {
                setLoading(true)
                const res = await Apies.getSCData()
                if(!cancelled) setSCData(res)
            } catch (error) {
                console.log("Error fetching SCData: ", error)
            } finally {
                if (!cancelled) {
                    setLoading(false)
                }
            }
        }

        load()
        return () => {
            cancelled = true;
        }
    }, [])

    if (loading) return <Loading />
    if (!scData) return <div className={"dashboardView"}><p>{t("errors.AllDataFailedToLoad")}</p></div>

    const lineData: ChartData<"line", number[], string> = {
        labels: scData.map((r) => r.timestamp),
        datasets: [
            {
                label: t("SCData.title"),
                data: scData.map((r) => r.amount),
                backgroundColor: "rgba(25, 249, 216, 0.35)",
                borderColor: "rgba(25, 249, 216, 0.9)",
                borderWidth: 1
            }
        ]
    }

    return (
        <div className={"dashboardView"}>
            <div style={{ width: "800px", height: "400px" }}>
                <Line data={lineData} options={lineOptions} />
            </div>
        </div>
    )
}
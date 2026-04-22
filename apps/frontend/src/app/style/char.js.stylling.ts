import type {ChartOptions} from "chart.js";

export const lineOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: "index", intersect: false },
    plugins: {
        legend: {
            display: true,
            labels: { color: "var(--text-muted)" }
        },
        tooltip: {
            enabled: true,
            backgroundColor: "var(--tooltip-big)",
            titleColor: "var(--tooltip-text)",
            bodyColor: "var(--tooltip-text)",
            borderColor: "var(--border)",
            borderWidth: 1
        }
    },
    scales: {
        x: {
            grid: { color: "var(--grid)" },
            ticks: { color: "var(--text-muted)", maxRotation: 0}
        },
        y: {
            grid: { color: "var(--grid)" },
            ticks: { color: "var(--text-muted)" }
        }
    }
}
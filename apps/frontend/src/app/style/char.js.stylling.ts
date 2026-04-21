import type {ChartOptions} from "chart.js";

export const lineOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { labels: { color: "rgba(255, 255, 255, 0.75)" } },
        title: { display: false },
        tooltip: {}
    },
    scales: {
        x: {
            grid: {
                color: "rgba(255, 255, 255, 0.08)",
            },
            ticks: {
                color: "rgba(255, 255, 255, 0.65)"
            }
        },
        y: {
            grid: {
                color: "rgba(255, 255, 255, 0.08)"
            },
            ticks: {
                color: "rgba(255, 255, 255, 0.65)"
            }
        }
    }
}
import React from 'react'
import { Bar } from 'react-chartjs-2'
import {
  Chart as ChartJS, BarElement, CategoryScale, LinearScale, Tooltip, Legend
} from 'chart.js'

ChartJS.register(BarElement, CategoryScale, LinearScale, Tooltip, Legend)

export function PointsChart({ data }: { data: { label: string; value: number }[] }) {
  const chartData = {
    labels: data.map(d => d.label),
    datasets: [{ label: 'Points', data: data.map(d => d.value) }]
  }
  const options = { responsive: true, plugins: { legend: { display: false } },
                    scales: { y: { beginAtZero: true } } }
  return (
    <div className="card">
      <h3>Points</h3>
      <Bar data={chartData} options={options} />
    </div>
  )
}
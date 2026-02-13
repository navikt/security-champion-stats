import {Faro, getWebInstrumentations, initializeFaro} from '@grafana/faro-web-sdk'
import {TracingInstrumentation} from "@grafana/faro-web-tracing";

let faro: Faro | null = null

export function initInstrumentation(): void {
    if (typeof window === 'undefined' || faro !== null) return
    getFaro()
}

export function getFaro(): Faro | null {
    if (faro !== null || typeof window === 'undefined') {
        return faro
    }

    const telemetryUrl = process.env.NEXT_PUBLIC_TELEMETRY_URL

    if (!telemetryUrl) {
        console.warn('NEXT_PUBLIC_TELEMETRY_URL is not set, Faro instrumentation will not be initialized');
        return null;
    }

    faro = initializeFaro({
        url: telemetryUrl,
        app: {
            name: 'SecurityChampion-stats',
            version: process.env.NEXT_PUBLIC_VERSION || 'unknown'
        },
        instrumentations: [
            ...getWebInstrumentations({
                captureConsole: true,
            }),
            new TracingInstrumentation()
        ]
    })

    return faro
}
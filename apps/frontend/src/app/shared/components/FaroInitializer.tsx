"use client"

import {useEffect} from "react";
import {initInstrumentation} from "../../../instrumentation/faro";

export function FaroInitializer() {
    useEffect(() => {
        initInstrumentation()
    }, [])

    return null
}
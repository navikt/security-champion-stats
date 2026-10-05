"use client";

import { ThemeProvider as NextThemeProvider, useTheme } from "next-themes";
import { useEffect, useState } from "react";
import { Select } from "@navikt/ds-react";

export function ThemeProvider({ children }: { children: React.ReactNode }) {
	return (
		<NextThemeProvider attribute="class" enableSystem storageKey="scs-theme">
			{children}
		</NextThemeProvider>
	);
}

export function ThemeToggle() {
	const { theme, setTheme } = useTheme();
	const [mounted, setMounted] = useState(false);

	useEffect(() => {
		setMounted(true);
	}, []);

	if (!mounted) {
		return (
			<Select
				label="Theme"
				size={"small"}
				value={"system"}
				onChange={() => {}}
				disabled
			>
				<option value={"system"}>System</option>
			</Select>
		);
	}

	const themes = [
		{ value: "light", label: "Light" },
		{ value: "dark", label: "Dark" },
		{ value: "system", label: "System" },
	];

	return (
		<Select
			label="Theme"
			size={"small"}
			value={theme}
			onChange={(e) => setTheme(e.target.value)}
		>
			{themes.map((themeOption) => (
				<option key={themeOption.value} value={themeOption.value}>
					{themeOption.label}
				</option>
			))}
		</Select>
	);
}

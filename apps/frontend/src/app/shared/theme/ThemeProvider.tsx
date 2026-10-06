"use client";

import { ThemeProvider as NextThemeProvider, useTheme } from "next-themes";
import { useEffect, useState } from "react";
import { ThemeIcon } from "@navikt/aksel-icons";
import { ActionMenu, InternalHeader, Theme } from "@navikt/ds-react";

export function ThemeProvider({ children }: { children: React.ReactNode }) {
	return (
		<NextThemeProvider attribute="class" enableSystem storageKey="scs-theme">
			{children}
		</NextThemeProvider>
	);
}

export function ThemeToggle() {
	const { theme, resolvedTheme, setTheme } = useTheme();
	const [mounted, setMounted] = useState(false);

	useEffect(() => {
		setMounted(true);
	}, []);

	const themes = [
		{ value: "light", label: "Light" },
		{ value: "dark", label: "Dark" },
		{ value: "system", label: "System" },
	];

	return (
		<ActionMenu>
			<ActionMenu.Trigger>
				<InternalHeader.Button
					aria-label="Choose theme"
					title="Choose theme"
					disabled={!mounted}
				>
					<ThemeIcon aria-hidden fontSize="1.5rem" />
				</InternalHeader.Button>
			</ActionMenu.Trigger>
			<Theme theme={mounted && resolvedTheme === "dark" ? "dark" : "light"}>
				<ActionMenu.Content align="end">
					<ActionMenu.RadioGroup
						label="Theme"
						value={mounted ? theme ?? "system" : "system"}
						onValueChange={setTheme}
					>
						{themes.map((themeOption) => (
							<ActionMenu.RadioItem key={themeOption.value} value={themeOption.value}>
								{themeOption.label}
							</ActionMenu.RadioItem>
						))}
					</ActionMenu.RadioGroup>
				</ActionMenu.Content>
			</Theme>
		</ActionMenu>
	);
}

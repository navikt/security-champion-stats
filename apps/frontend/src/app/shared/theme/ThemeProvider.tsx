"use client";

import { ThemeIcon } from "@navikt/aksel-icons";
import { ActionMenu, Button, Theme } from "@navikt/ds-react";
import { ThemeProvider as NextThemeProvider, useTheme } from "next-themes";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { hackerCopy } from "@/app/view/hackerCopy";

type HackerPreferences = {
	rain: boolean;
	crt: boolean;
	phosphor: "matrix" | "amber" | "swordfish";
	setRain: (enabled: boolean) => void;
	setCrt: (enabled: boolean) => void;
	setPhosphor: (variant: HackerPreferences["phosphor"]) => void;
};

type StoredHackerPreferences = Pick<
	HackerPreferences,
	"rain" | "crt" | "phosphor"
>;

const HackerPreferencesContext = createContext<HackerPreferences | null>(null);
const HACKER_PREFERENCES_KEY = "scs-hacker-preferences";

export function HackerPreferencesProvider({
	children,
}: {
	children: React.ReactNode;
}) {
	const [rain, setRain] = useState(false);
	const [crt, setCrt] = useState(false);
	const [phosphor, setPhosphor] =
		useState<HackerPreferences["phosphor"]>("matrix");
	const [loaded, setLoaded] = useState(false);

	useEffect(() => {
		const reducedMotion =
			window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
		try {
			const storage = window.localStorage;
			if (!storage) {
				setRain(!reducedMotion);
				setCrt(true);
				return;
			}
			const stored = storage.getItem(HACKER_PREFERENCES_KEY);
			if (stored) {
				const preferences: Partial<StoredHackerPreferences> =
					JSON.parse(stored);
				setRain(
					typeof preferences.rain === "boolean"
						? preferences.rain
						: !reducedMotion,
				);
				setCrt(typeof preferences.crt === "boolean" ? preferences.crt : true);
				if (
					preferences.phosphor === "matrix" ||
					preferences.phosphor === "amber" ||
					preferences.phosphor === "swordfish"
				) {
					setPhosphor(preferences.phosphor);
				}
			} else {
				setRain(!reducedMotion);
				setCrt(true);
			}
		} catch (error) {
			console.error("Failed to load hacker theme preferences:", error);
			setRain(!reducedMotion);
			setCrt(true);
		} finally {
			setLoaded(true);
		}
	}, []);

	useEffect(() => {
		document.documentElement.dataset.phosphor = phosphor;
		if (!loaded) return;
		try {
			const storage = window.localStorage;
			if (!storage) return;
			storage.setItem(
				HACKER_PREFERENCES_KEY,
				JSON.stringify({ rain, crt, phosphor }),
			);
		} catch (error) {
			console.error("Failed to save hacker theme preferences:", error);
		}
	}, [rain, crt, phosphor, loaded]);

	return (
		<HackerPreferencesContext.Provider
			value={{ rain, crt, phosphor, setRain, setCrt, setPhosphor }}
		>
			{children}
		</HackerPreferencesContext.Provider>
	);
}

function useHackerPreferences() {
	const preferences = useContext(HackerPreferencesContext);
	if (!preferences)
		throw new Error("Hacker preferences must be used within ThemeProvider");
	return preferences;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
	return (
		<NextThemeProvider
			attribute="class"
			themes={["light", "dark", "hacker"]}
			enableSystem
			storageKey="scs-theme"
		>
			<HackerPreferencesProvider>{children}</HackerPreferencesProvider>
		</NextThemeProvider>
	);
}

export function ThemeToggle() {
	const { theme, resolvedTheme, setTheme } = useTheme();
	const { rain, crt, phosphor, setRain, setCrt, setPhosphor } =
		useHackerPreferences();
	const [mounted, setMounted] = useState(false);

	useEffect(() => {
		setMounted(true);
	}, []);

	const themes = [
		{ value: "light", label: "Light" },
		{ value: "dark", label: "Dark" },
		{ value: "system", label: "System" },
		{ value: "hacker", label: "Hacker" },
	];

	return (
		<ActionMenu>
			<ActionMenu.Trigger>
				<Button
					variant="tertiary"
					data-color="neutral"
					size="small"
					icon={<ThemeIcon aria-hidden />}
					aria-label="Choose theme"
					title="Choose theme"
					disabled={!mounted}
				>
					Theme
				</Button>
			</ActionMenu.Trigger>
			<Theme
				theme={
					mounted && (resolvedTheme === "dark" || theme === "hacker")
						? "dark"
						: "light"
				}
			>
				<ActionMenu.Content align="end">
					<ActionMenu.RadioGroup
						label="Theme"
						value={mounted ? (theme ?? "system") : "system"}
						onValueChange={setTheme}
					>
						{themes.map((themeOption) => (
							<ActionMenu.RadioItem
								key={themeOption.value}
								value={themeOption.value}
							>
								{themeOption.label}
							</ActionMenu.RadioItem>
						))}
					</ActionMenu.RadioGroup>
					{mounted && theme === "hacker" && (
						<div className="hackerThemeSettings">
							<label className="hackerThemeSettings__field">
								{hackerCopy.settings.phosphor}
								<select
									value={phosphor}
									onChange={(event) => {
										const value = event.target.value;
										if (
											value === "matrix" ||
											value === "amber" ||
											value === "swordfish"
										)
											setPhosphor(value);
									}}
								>
									<option value="matrix">{hackerCopy.settings.matrix}</option>
									<option value="amber">{hackerCopy.settings.amber}</option>
									<option value="swordfish">
										{hackerCopy.settings.swordfish}
									</option>
								</select>
							</label>
							<label className="hackerThemeSettings__toggle">
								<input
									type="checkbox"
									checked={rain}
									onChange={(event) => setRain(event.target.checked)}
								/>
								{hackerCopy.settings.rain}
							</label>
							<label className="hackerThemeSettings__toggle">
								<input
									type="checkbox"
									checked={crt}
									onChange={(event) => setCrt(event.target.checked)}
								/>
								{hackerCopy.settings.crt}
							</label>
						</div>
					)}
				</ActionMenu.Content>
			</Theme>
		</ActionMenu>
	);
}

export function HackerBackdrop() {
	const { theme } = useTheme();
	const { rain, crt } = useHackerPreferences();
	const canvasRef = useRef<HTMLCanvasElement>(null);
	const isHacker = theme === "hacker";

	useEffect(() => {
		const canvas = canvasRef.current;
		if (
			!canvas ||
			!isHacker ||
			!rain ||
			(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false)
		)
			return;
		const context = canvas.getContext("2d");
		if (!context) return;

		const characters = "アカサタナハマヤラワ0123456789#$%<>/\\SECHUB";
		let columns = 0;
		let drops: number[] = [];
		let timer: number | undefined;
		const resize = () => {
			canvas.width = window.innerWidth;
			canvas.height = window.innerHeight;
			columns = Math.ceil(canvas.width / 16);
			drops = Array.from(
				{ length: columns },
				() => Math.random() * (canvas.height / 16),
			);
		};
		const draw = () => {
			context.fillStyle = "rgba(3, 6, 3, 0.08)";
			context.fillRect(0, 0, canvas.width, canvas.height);
			context.font = "16px monospace";
			const primary = getComputedStyle(document.documentElement)
				.getPropertyValue("--primary")
				.trim();
			drops.forEach((drop, index) => {
				const character =
					characters[Math.floor(Math.random() * characters.length)];
				context.fillStyle = Math.random() < 0.025 ? "#eaffef" : primary;
				context.fillText(character, index * 16, drop * 16);
				if (drop * 16 > canvas.height && Math.random() > 0.975)
					drops[index] = 0;
				else drops[index] = drop + 1;
			});
		};
		const start = () => {
			if (timer === undefined && !document.hidden) {
				timer = window.setInterval(draw, 55);
			}
		};
		const stop = () => {
			if (timer !== undefined) {
				window.clearInterval(timer);
				timer = undefined;
			}
		};
		const onVisibilityChange = () => {
			if (document.hidden) stop();
			else start();
		};
		resize();
		window.addEventListener("resize", resize);
		document.addEventListener("visibilitychange", onVisibilityChange);
		start();
		return () => {
			stop();
			window.removeEventListener("resize", resize);
			document.removeEventListener("visibilitychange", onVisibilityChange);
			context.clearRect(0, 0, canvas.width, canvas.height);
		};
	}, [isHacker, rain]);

	if (!isHacker) return null;
	return (
		<>
			{rain && (
				<div className="hackerRain" aria-hidden="true">
					<canvas ref={canvasRef} className="hackerRain__canvas" />
				</div>
			)}
			{crt && (
				<div className="hackerCrt" aria-hidden="true">
					<div className="hackerCrt__scanlines" />
					<div className="hackerCrt__vignette" />
				</div>
			)}
		</>
	);
}

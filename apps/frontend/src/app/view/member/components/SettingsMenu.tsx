"use client";

import { BodyShort, Button, Popover, VStack } from "@navikt/ds-react";
import { CogIcon } from "@navikt/aksel-icons";
import { ThemeToggle } from "@/app/shared/theme/ThemeProvider";
import { useRef, useState, useId } from "react";

export default function SettingsMenu() {
	const anchorRef = useRef<HTMLButtonElement>(null);
	const [open, setOpen] = useState(false);
	const popoverId = useId();
	return (
		<div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
			<Button
				ref={anchorRef}
				onClick={() => setOpen(!open)}
				variant={"tertiary"}
				icon={<CogIcon aria-hidden />}
				size={"small"}
				aria-label="Settings"
			>
				<BodyShort size={"small"}>Settings</BodyShort>
			</Button>
			<Popover
				anchorEl={anchorRef.current}
				open={open}
				onClose={() => setOpen(false)}
				id={popoverId}
				placement={"bottom-end"}
			>
				<Popover.Content>
					<VStack gap={"space-4"}>
						<ThemeToggle />
					</VStack>
				</Popover.Content>
			</Popover>
		</div>
	);
}

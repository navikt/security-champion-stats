"use client";

import { usePathname } from "next/navigation";
import "../../style/SideNavigation.css";
import { administrationLinks, navigation } from "@/app/shared/navigation/Navigation";
import Link from "next/link";
import { ThemeToggle } from "@/app/shared/theme/ThemeProvider";
import { getInitials } from "@/app/utils/GetInitials";
import type { Me } from "@/app/utils/Variables";
import { useState } from "react";
import { Button } from "@navikt/ds-react";
import { ChevronDownIcon } from "@navikt/aksel-icons";

function isActivePath(pathName: string, href: string): boolean {
	return href === "/" ? pathName === "/" : pathName === href || pathName.startsWith(`${href}/`);
}

function AdministrationNavigation({ pathName }: { pathName: string }) {
	const [expanded, setExpanded] = useState(
		administrationLinks.some((item) => isActivePath(pathName, item.path)),
	);

	return (
		<div className="sideNavigation__administration">
			<Button
				variant="tertiary"
				data-color="neutral"
				size="small"
				className="sideNavigation__item sideNavigation__administrationTrigger"
				icon={<ChevronDownIcon aria-hidden className="sideNavigation__chevron" />}
				iconPosition="right"
				aria-expanded={expanded}
				aria-controls="administration-navigation"
				onClick={() => setExpanded(!expanded)}
			>
				Administration
			</Button>
			<div id="administration-navigation" hidden={!expanded}>
				<div className="sideNavigation__adminLinks">
					<span className="sideNavigation__adminLabel">Admin only</span>
					{administrationLinks.map((item) => {
						const isActive = isActivePath(pathName, item.path);
						return (
							<Link
								key={item.path}
								href={item.path}
								className={[
									"sideNavigation__item",
									isActive ? "sideNavigation__item--active" : "",
								].filter(Boolean).join(" ")}
								aria-current={isActive ? "page" : undefined}
							>
								{item.label}
							</Link>
						);
					})}
				</div>
			</div>
		</div>
	);
}

export function SideNavigation({ me }: { me: Me }) {
	const pathName = usePathname();
	const paths = navigation();
	const userLabel = `Signed in as ${me.displayName || me.username || "Name unavailable"}`;

	return (
		<aside className={"sideNavigation"}>
			<Link href="/" className="sideNavigation__brand">
				Sec Hub
			</Link>
			<nav className={"sideNavigation__nav"} aria-label={"Main navigation"}>
				{paths.map((item) => {
					const href = item.path || "/";
					const isActive = isActivePath(pathName, href);
					const Icon = item.icon;

					return (
						<Link
							key={item.id}
							href={href}
							className={[
								"sideNavigation__item",
								isActive ? "sideNavigation__item--active" : "",
							]
								.filter(Boolean)
								.join(" ")}
							aria-current={isActive ? "page" : undefined}
						>
							<Icon aria-hidden className={"sideNavigation__icon"} />
							<span>{item.label}</span>
						</Link>
					);
				})}
				{me.isParticipant && (
					<Link
						href="/history"
						className="sideNavigation__item"
						aria-current={isActivePath(pathName, "/history") ? "page" : undefined}
					>
						My history
					</Link>
				)}
				{me.isAdmin && <AdministrationNavigation key={pathName} pathName={pathName} />}
			</nav>
			<div className="sideNavigation__footer">
				<ThemeToggle />
				<span
					className="sideNavigation__avatar"
					role="img"
					aria-label={userLabel}
					title={userLabel}
					tabIndex={0}
				>
					<span aria-hidden>{getInitials(me.displayName ?? "") || "?"}</span>
				</span>
			</div>
		</aside>
	);
}

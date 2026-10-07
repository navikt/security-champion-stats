"use client";

import { usePathname } from "next/navigation";
import "../../style/SideNavigation.css";
import { ChevronDownIcon, TimelineIcon } from "@navikt/aksel-icons";
import { Button } from "@navikt/ds-react";
import Link from "next/link";
import { useTheme } from "next-themes";
import { useState } from "react";
import {
	administrationLinks,
	navigation,
} from "@/app/shared/navigation/Navigation";
import { ThemeToggle } from "@/app/shared/theme/ThemeProvider";
import { getInitials } from "@/app/utils/GetInitials";
import type { Me } from "@/app/utils/Variables";
import { hackerCopy } from "@/app/view/hackerCopy";
import { hackerAlias } from "@/app/view/hackerUtils";

function isActivePath(pathName: string, href: string): boolean {
	return href === "/"
		? pathName === "/"
		: pathName === href || pathName.startsWith(`${href}/`);
}

function AdministrationNavigation({ pathName }: { pathName: string }) {
	const { theme } = useTheme();
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
				icon={
					<ChevronDownIcon aria-hidden className="sideNavigation__chevron" />
				}
				iconPosition="right"
				aria-expanded={expanded}
				aria-controls="administration-navigation"
				onClick={() => setExpanded(!expanded)}
			>
				{theme === "hacker" ? hackerCopy.navigation.admin : "Administration"}
			</Button>
			<div id="administration-navigation" hidden={!expanded}>
				<div className="sideNavigation__adminLinks">
					{administrationLinks.map((item) => {
						const isActive = isActivePath(pathName, item.path);
						return (
							<Link
								key={item.path}
								href={item.path}
								className={[
									"sideNavigation__item",
									isActive ? "sideNavigation__item--active" : "",
								]
									.filter(Boolean)
									.join(" ")}
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
	const { theme } = useTheme();
	const hacker = theme === "hacker";
	const paths = navigation();
	const userLabel = `Signed in as ${me.displayName || me.username || "Name unavailable"}`;

	return (
		<aside
			className={`sideNavigation${hacker ? " sideNavigation--hacker" : ""}`}
		>
			<Link href="/" className="sideNavigation__brand">
				{hacker ? hackerCopy.brand : "Sec Hub"}
			</Link>
			{hacker && (
				<span className="sideNavigation__version">
					{hackerCopy.navigation.version}
				</span>
			)}
			<nav className={"sideNavigation__nav"} aria-label={"Main navigation"}>
				{hacker && (
					<span className="sideNavigation__prompt">
						{hackerCopy.navigation.prompt}
					</span>
				)}
				{paths.map((item) => {
					const href = item.path || "/";
					const isActive = isActivePath(pathName, href);
					const Icon = item.icon;
					const hackerLabel =
						item.id === "overview"
							? "overview/"
							: `${item.label.toLowerCase()}/`;

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
							{!hacker && (
								<Icon aria-hidden className={"sideNavigation__icon"} />
							)}
							<span>
								{hacker && isActive ? "> " : hacker ? "  " : ""}
								{hacker ? hackerLabel : item.label}
							</span>
						</Link>
					);
				})}
				{me.isParticipant && (
					<Link
						href="/history"
						className={[
							"sideNavigation__item",
							isActivePath(pathName, "/history") ? "sideNavigation__item--active" : "",
						]
							.filter(Boolean)
							.join(" ")}
						aria-current={
							isActivePath(pathName, "/history") ? "page" : undefined
						}
					>
						{!hacker && (
							<TimelineIcon aria-hidden className="sideNavigation__icon" />
						)}
						<span>{hacker ? hackerCopy.navigation.history : "My history"}</span>
					</Link>
				)}
				{me.isAdmin && (
					<AdministrationNavigation key={pathName} pathName={pathName} />
				)}
			</nav>
			{hacker && (
				<div className="sideNavigation__hackerStatus" aria-hidden="true">
					<div className="sideNavigation__hackerStatusTitle">
						{hackerCopy.navigation.statusTitle}
					</div>
					{hackerCopy.status.map((line) => {
						const [label, value] = line.split(/\s{2,}/);
						return (
							<div key={line}>
								{label}
								<span>{value}</span>
							</div>
						);
					})}
				</div>
			)}
			<div className="sideNavigation__footer">
				<ThemeToggle />
				<span
					className="sideNavigation__avatar"
					role="img"
					aria-label={userLabel}
					title={userLabel}
				>
					<span aria-hidden>{getInitials(me.displayName ?? "") || "?"}</span>
				</span>
				{hacker && (
					<span className="sideNavigation__identity">
						<span>{hackerAlias(me.displayName, me.username)}</span>
						<span>{hackerCopy.statusUnavailable}</span>
					</span>
				)}
			</div>
		</aside>
	);
}

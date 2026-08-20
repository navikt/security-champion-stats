"use client";

import { usePathname } from "next/navigation";
import "../../style/SideNavigation.css";
import { mainNavigation } from "@/app/shared/navigation/Navigation";
import Link from "next/link";

interface SideNavigationProps {
	locale: string;
}

export function SideNavigation({ locale }: SideNavigationProps) {
	const pathName = usePathname();

	return (
		<aside className={"sideNavigation"}>
			<nav className={"sideNavigation__nav"} aria-label={"Main navigation"}>
				{mainNavigation.map((item) => {
					const href =
						item.path === "" ? `/${locale}` : `/${locale}${item.path}`;
					const isActive =
						item.path === ""
							? pathName === `/${locale}`
							: pathName.startsWith(href);
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
							<Icon aria-hidden className={"sideNa"} />
							<span>{item.label}</span>
						</Link>
					);
				})}
			</nav>
		</aside>
	);
}

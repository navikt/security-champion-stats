"use client";

import { usePathname } from "next/navigation";
import "../../style/SideNavigation.css";
import { navigation } from "@/app/shared/navigation/Navigation";
import Link from "next/link";

export function SideNavigation() {
	const pathName = usePathname();
	const paths = navigation();
	return (
		<aside className={"sideNavigation"}>
			<nav className={"sideNavigation__nav"} aria-label={"Main navigation"}>
				{paths.map((item) => {
					const href = item.path || "/";
					const isActive =
						item.path === ""
							? pathName === "/"
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

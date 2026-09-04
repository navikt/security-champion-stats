import { ComponentType, SVGProps } from "react";
import { CalendarIcon, HouseIcon, PersonIcon } from "@navikt/aksel-icons";

export interface ModuleNavLink {
	labelKey: string;
	path: string;
	//optional:
	order?: number;
}

export const moduleNavLinks: ModuleNavLink[] = [
	{
		labelKey: "header.admin",
		path: "/admin",
		order: 1,
	},
];

export type NavigationItem = {
	id: string;
	label: string;
	path: string;
	icon: ComponentType<SVGProps<SVGSVGElement>>;
};

export const mainNavigation: NavigationItem[] = [
	{
		id: "overview",
		label: "Overview",
		path: "",
		icon: HouseIcon,
	},
	{
		id: "profile",
		label: "My Profile",
		path: "/profile",
		icon: PersonIcon,
	},
	{
		id: "events",
		label: "Events",
		path: "/events",
		icon: CalendarIcon,
	},
];

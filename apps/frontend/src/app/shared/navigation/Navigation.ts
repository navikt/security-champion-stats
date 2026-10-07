import { ComponentType, SVGProps } from "react";
import { CalendarIcon, HouseIcon, PersonGroupIcon } from "@navikt/aksel-icons";

export interface ModuleNavLink {
	label: string;
	path: string;
	order?: number;
}

export const moduleNavLinks: ModuleNavLink[] = [
	{
		label: "Admin",
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

export const administrationLinks: ModuleNavLink[] = [
	{ label: "Program dashboard", path: "/dashboard" },
	{ label: "Manage events", path: "/appsec/events" },
	{ label: "Review event claims", path: "/appsec/event-claims" },
	{ label: "Manage participants", path: "/appsec/membership" },
	{ label: "Scoring dashboard", path: "/appsec/scoring" },
	{ label: "Manage Slack mappings", path: "/appsec/slack" },
	{ label: "Manage Delta mappings", path: "/appsec/delta" },
	{ label: "Audit trail", path: "/appsec/audit" },
];

export function navigation(): NavigationItem[] {
	return [
		{
			id: "overview",
			label: "Overview",
			path: "",
			icon: HouseIcon,
		},
		{
			id: "events",
			label: "Events",
			path: "/events",
			icon: CalendarIcon,
		},
		{
			id: "community",
			label: "Community",
			path: "/community",
			icon: PersonGroupIcon,
		},
	];
}

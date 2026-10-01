import { ComponentType, SVGProps } from "react";
import { CalendarIcon, HouseIcon, PersonGroupIcon } from "@navikt/aksel-icons";
import { useTranslations } from "next-intl";

export interface ModuleNavLink {
	labelKey: string;
	path: string;
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
	labelKey: string;
	path: string;
	icon: ComponentType<SVGProps<SVGSVGElement>>;
};

export function navigation(): NavigationItem[] {
	const t = useTranslations("sidebar");
	return [
		{
			id: "overview",
			labelKey: t("overview"),
			path: "",
			icon: HouseIcon,
		},
		{
			id: "events",
			labelKey: t("events"),
			path: "/events",
			icon: CalendarIcon,
		},
		{
			id: "community",
			labelKey: t("community"),
			path: "/community",
			icon: PersonGroupIcon,
		},
	];
}

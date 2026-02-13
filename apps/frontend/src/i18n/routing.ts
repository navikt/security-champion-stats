import {defineRouting} from "next-intl/routing";
import {createNavigation} from "next-intl/navigation";

export const routing = defineRouting({
    locales: ["nb", "en"],
    defaultLocale: "nb",
    localePrefix: "always"
})

export const { Link, redirect, usePathname, useRouter } =
    createNavigation(routing)
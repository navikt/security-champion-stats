export interface ModuleNavLink {
    labelKey: string,
    path: string,
    //optional:
    order?: number
}

export const moduleNavLinks: ModuleNavLink[] = [
    {
        labelKey: "header.admin",
        path: "/admin",
        order: 1
    }
]
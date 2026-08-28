import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
    reactCompiler: true,
    output: "standalone",
    outputFileTracingIncludes: {
        "/**": ["./node_modules/@swc/helpers/**"],
    },
};

export default withNextIntl(nextConfig);
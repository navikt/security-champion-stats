import "./style/globals.css";
import {getLocale, getMessages} from "next-intl/server";
import {NextIntlClientProvider} from "next-intl";
import {FaroInitializer} from "./shared/components/FaroInitializer";
import {ThemeProvider} from "./shared/theme/ThemeProvider";

export default async function RootLayout({
    children
}: Readonly<{
    children: React.ReactNode
}>) {
    const locale = await getLocale()
    const messages = await getMessages()

    return (
        <html lang={locale} suppressHydrationWarning>
            <body>
            <FaroInitializer />
            <ThemeProvider>
                <NextIntlClientProvider messages={messages}>
                    {children}
                </NextIntlClientProvider>
            </ThemeProvider>
            </body>
        </html>
    )
}
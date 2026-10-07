import "./style/global.css";
import { FaroInitializer } from "@/app/view/member/components/FaroInitializer";
import { ThemeProvider } from "./shared/theme/ThemeProvider";
import AppLayout from "./AppLayout";

export const metadata = {
	title: "Security Champion Hub",
};

export default function RootLayout({
	children,
}: Readonly<{
	children: React.ReactNode;
}>) {
	return (
		<html lang="en" suppressHydrationWarning>
			<body>
				<FaroInitializer />
				<ThemeProvider>
					<AppLayout>{children}</AppLayout>
				</ThemeProvider>
			</body>
		</html>
	);
}

import { BodyShort, Box, Heading, Loader } from "@navikt/ds-react";

export default function Loading() {
	return (
		<Box
			as={"main"}
			className={"loadingScreen"}
			aria-busy={true}
			aria-live={"polite"}
		>
			<Box className={"loadingScreen__content"}>
				<Heading size={"large"} level={"1"}>
					Sec Hub
				</Heading>
				<BodyShort spacing> </BodyShort>
				<Box className={"loadingScreen__spinner"}>
					<Loader size={"large"} title="Loading..." />
				</Box>
			</Box>
		</Box>
	);
}

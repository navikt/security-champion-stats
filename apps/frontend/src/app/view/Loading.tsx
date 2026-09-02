import {useTranslations} from "next-intl";
import {BodyShort, Box, Heading, Loader} from "@navikt/ds-react";

export default function Loading() {
    const t = useTranslations()
    return (
        <Box
            as={"main"}
            className={"loadingScreen"}
            aria-busy={true}
            aria-live={"polite"}
        >
            <Box className={"loadingScreen__content"}>
                <Heading size={"large"} level={"1"}>
                    {t("title")}
                </Heading>
                <BodyShort spacing> </BodyShort>
                <Box className={"loadingScreen__spinner"}>
                    <Loader size={"large"} title={t("loading.title")} />
                </Box>
            </Box>
        </Box>
    );
}

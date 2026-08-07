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
                    {t("common.appTitle")}
                </Heading>
                <BodyShort spacing>{t("common.description")}</BodyShort>
                <Box className={"loadingScreen__spinner"}>
                    <Loader size={"large"} title={t("common.loading")} />
                </Box>
            </Box>
        </Box>
    );
}

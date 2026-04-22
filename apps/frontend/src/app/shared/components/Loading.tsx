import {useTranslations} from "next-intl";
import "../../style/loading.css"
import {BodyShort, Box, Heading, Loader} from "@navikt/ds-react";

export default function Loading() {
    const t = useTranslations()
    return (
        <Box
            as={"main"}
            padding={"space-6"}
            style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}
            aria-busy={true}
            aria-live={"polite"}
        >
            <Box style={{ width: "100%", maxWidth: 720 }}>
                <Heading size={"large"} level={"1"}>
                    {t("common.appTitle")}
                </Heading>
                <BodyShort spacing>{t("common.description")}</BodyShort>
                <Box
                    paddingBlock={"space-12"} style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 240}}>
                    <Loader size={"large"} title={t("common.loading")} />
                </Box>
            </Box>
        </Box>
    );
}

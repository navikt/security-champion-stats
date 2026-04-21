import {useTranslations} from "next-intl";
import {BodyShort, Box, Loader} from "@navikt/ds-react";

export default function Loading() {
    const t = useTranslations()
    return (
        <div style={{marginTop: "2rem"}} className="flex items-center justify-center min-h-screen">
            <main>
                <div>
                    <h1 dangerouslySetInnerHTML={{__html: t.raw("common.appTitle")}}/>
                    <BodyShort spacing>
                        {t("common.description")}
                    </BodyShort>
                </div>
                <Box
                    style={{ display: "flex", justifyContent: "center", minHeight: "50vh", alignItems: "center" }}
                    paddingBlock="space-24"
                >
                    <Loader size="large" title={t("common.loading")}/>
                </Box>
            </main>
        </div>
    );
}

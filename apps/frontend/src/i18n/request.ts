
import nbMessages from "../messages/nb.json"
import enMessages from "../messages/en.json"
import {getRequestConfig} from "next-intl/server";
import {routing} from "./routing";

const messages = {
    nb: nbMessages,
    en: enMessages
}

export default getRequestConfig(async ({ requestLocale }) => {
    let locale = await requestLocale
    if (!locale || !routing.locales.includes(locale as "nb" | "en")) {
        locale = routing.defaultLocale
    }

    return {
        locale,
        messages: messages[locale as "nb" | "en"]
    }
});
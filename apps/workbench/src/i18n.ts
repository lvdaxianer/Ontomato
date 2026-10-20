import { appTextEn } from "@ontomato/workbench-server/i18n/app-text-en";
import { en, enLocaleMeta } from "@ontomato/workbench-server/i18n/locales/en";
import { zhCNLocaleMeta, zhCNMessages } from "@ontomato/workbench-server/i18n/locales/zh-CN";
import { configureI18n } from "@ontomato/workbench-server";

export function configureOssI18n(): void {
  configureI18n({
    defaultLocale: "zh-CN",
    languageSwitchEnabled: true,
    appTextLocale: "en",
    packs: {
      "zh-CN": {
        ...zhCNLocaleMeta,
        messages: { ...zhCNMessages, ...appTextEn },
      },
      en: {
        messages: { ...en, ...appTextEn },
        ...enLocaleMeta,
      },
    },
  });
}

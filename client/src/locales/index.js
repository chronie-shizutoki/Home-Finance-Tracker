import { createI18n } from 'vue-i18n';
import enUS from './en-US.json';
import enAN from './en-AN.json';
import zhCN from './zh-CN.json';
import zhTW from './zh-TW.json';

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  fallbackLocale: 'en-US',
  messages: {
    'en-US': enUS,
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    'en-AN': enAN
  }
});

export default i18n;

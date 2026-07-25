import { createI18n } from 'vue-i18n';
import enUS from './en-US.json';
import idID from './id-ID.json';
import jaJP from './ja-JP.json';
import koKR from './ko-KR.json';
import msMY from './ms-MY.json';
import thTH from './th-TH.json';
import viVN from './vi-VN.json';
import zhCN from './zh-CN.json';
import zhHK from './zh-HK.json';
import zhMO from './zh-MO.json';
import zhSG from './zh-SG.json';
import zhTW from './zh-TW.json';

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  fallbackLocale: 'en-US',
  messages: {
    'en-US': enUS,
    'id-ID': idID,
    'ja-JP': jaJP,
    'ko-KR': koKR,
    'ms-MY': msMY,
    'th-TH': thTH,
    'vi-VN': viVN,
    'zh-CN': zhCN,
    'zh-HK': zhHK,
    'zh-MO': zhMO,
    'zh-SG': zhSG,
    'zh-TW': zhTW
  }
});

export default i18n;

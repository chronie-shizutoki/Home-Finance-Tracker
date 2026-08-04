/*
 * @file i18n.js
 * @package Home-Finance-Tracker
 * @module Localization Configuration
 * @description Core configuration file for i18n instance and language packages
 * @author Developer
 * @version 1.0
*/

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

const browserLanguage = navigator.language || navigator.userLanguage;
console.log('Browser default language detected:', browserLanguage);

const supportedLanguages = [
  'en-US',
  'id-ID',
  'ja-JP',
  'ko-KR',
  'ms-MY',
  'th-TH',
  'vi-VN',
  'zh-CN',
  'zh-HK',
  'zh-MO',
  'zh-SG',
  'zh-TW'
];
console.log('Supported languages list:', supportedLanguages);

let defaultLocale = 'en-US';
console.log('Initial default language setting:', defaultLocale);

if (supportedLanguages.includes(browserLanguage)) {
  defaultLocale = browserLanguage;
  console.log('Browser language in supported list, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('zh-TW') || browserLanguage.includes('TW')) {
  defaultLocale = 'zh-TW';
  console.log('Browser language is Traditional Chinese (Taiwan), default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('zh-HK') || browserLanguage.includes('HK')) {
  defaultLocale = 'zh-HK';
  console.log('Browser language is Traditional Chinese (Hong Kong), default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('zh-MO') || browserLanguage.includes('MO')) {
  defaultLocale = 'zh-MO';
  console.log('Browser language for Traditional Chinese (Macau) detected, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('zh-SG') || browserLanguage.includes('SG')) {
  defaultLocale = 'zh-SG';
  console.log('Browser language for Simplified Chinese (Singapore) detected, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('zh')) {
  defaultLocale = 'zh-CN';
  console.log('Browser language for Chinese detected, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('ja')) {
  defaultLocale = 'ja-JP';
  console.log('Browser language is Japanese, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('ko')) {
  defaultLocale = 'ko-KR';
  console.log('Browser language is Korean, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('id')) {
  defaultLocale = 'id-ID';
  console.log('Browser language is Indonesian, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('ms')) {
  defaultLocale = 'ms-MY';
  console.log('Browser language is Malay, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('th')) {
  defaultLocale = 'th-TH';
  console.log('Browser language is Thai, default language updated to:', defaultLocale);
} else if (browserLanguage.startsWith('vi')) {
  defaultLocale = 'vi-VN';
  console.log('Browser language is Vietnamese, default language updated to:', defaultLocale);
} else {
  console.log('No matching language found, default language kept:', defaultLocale);
}

/**
 * i18n instance configuration
 * @type {import('vue-i18n').I18n}
 */
const i18n = createI18n({
  legacy: false,
  locale: defaultLocale,
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

export const changeLanguage = (newLocale) => {
  if (supportedLanguages.includes(newLocale)) {
    i18n.global.locale.value = newLocale;
    console.log(`Language switched to: ${newLocale}`);
  } else {
    console.error(`Unsupported language detected: ${newLocale}`);
  }
};

export default i18n;

/*
 * @file i18n.js
 * @package 家庭记账本
 * @module 国际化配置
 * @description 多语言支持核心配置文件，初始化i18n实例并加载语言包
 * @author 开发者
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
console.log('获取到的浏览器默认语言为:', browserLanguage);

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
console.log('支持的语言列表为:', supportedLanguages);

let defaultLocale = 'en-US';
console.log('初始默认语言设置为:', defaultLocale);

if (supportedLanguages.includes(browserLanguage)) {
  defaultLocale = browserLanguage;
  console.log('浏览器语言在支持列表中，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('zh-TW') || browserLanguage.includes('TW')) {
  defaultLocale = 'zh-TW';
  console.log('浏览器语言为繁体中文(台湾)，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('zh-HK') || browserLanguage.includes('HK')) {
  defaultLocale = 'zh-HK';
  console.log('浏览器语言为繁体中文(香港)，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('zh-MO') || browserLanguage.includes('MO')) {
  defaultLocale = 'zh-MO';
  console.log('浏览器语言为繁体中文(澳门)，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('zh-SG') || browserLanguage.includes('SG')) {
  defaultLocale = 'zh-SG';
  console.log('浏览器语言为简体中文(新加坡)，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('zh')) {
  defaultLocale = 'zh-CN';
  console.log('浏览器语言为中文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('ja')) {
  defaultLocale = 'ja-JP';
  console.log('浏览器语言为日文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('ko')) {
  defaultLocale = 'ko-KR';
  console.log('浏览器语言为韩文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('id')) {
  defaultLocale = 'id-ID';
  console.log('浏览器语言为印尼文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('ms')) {
  defaultLocale = 'ms-MY';
  console.log('浏览器语言为马来文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('th')) {
  defaultLocale = 'th-TH';
  console.log('浏览器语言为泰文，默认语言更新为:', defaultLocale);
} else if (browserLanguage.startsWith('vi')) {
  defaultLocale = 'vi-VN';
  console.log('浏览器语言为越南文，默认语言更新为:', defaultLocale);
} else {
  console.log('未找到匹配的语言，默认语言保持不变:', defaultLocale);
}

/**
 * 初始化i18n实例
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
    console.log(`语言已切换为: ${newLocale}`);
  } else {
    console.error(`不支持的语言: ${newLocale}`);
  }
};

export default i18n;

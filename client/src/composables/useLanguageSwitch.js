/*
 * @file useLanguageSwitch.js
 * @package Home-Finance-Tracker
 * @module Composables
 * @description Language switch function for managing application language state and switching
 * @author Developer
 * @version 1.0
*/

import { ref } from 'vue';
import i18n from '@/locales/i18n';

/**
 * Language switch function for application language management
 * @returns {Object} Object containing language state and switch methods
 */
export function useLanguageSwitch () {
  /**
   * Current application language (reactive data)
   * @type {import('vue').Ref<string>}
   */
  // Project uses reactive API mode (legacy: false), directly get initial language from i18n.global.locale
  const currentLang = ref(i18n.global.locale.value);

  /**
   * Switch application language
   * @param {string} lang - Target language code
   */
  const switchLanguage = (lang) => {
    // Project uses reactive API mode (legacy: false), directly access i18n.global.locale
    try {
      i18n.global.locale.value = lang;
      currentLang.value = lang;
      localStorage.setItem('appLang', lang); // Persist language setting in local storage
    } catch (error) {
      console.error('Switch language failed:', error);
    }
  };

  /**
   * Initialize application language from local storage
   */
  const initLanguage = () => {
    const savedLang = localStorage.getItem('appLang');
    if (savedLang) switchLanguage(savedLang);
  };

  // Component mounted, initialize language from local storage
  initLanguage();

  return {
    currentLang,
    switchLanguage
  };
}

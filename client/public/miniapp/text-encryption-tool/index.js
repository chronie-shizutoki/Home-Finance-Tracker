/**
 * 文本加密工具 - 核心功能实现
 * 使用AES-256加密算法，支持将加密结果转换为拟声词组合
 */

// DOM元素引用
const inputText = document.getElementById('input-text');
const outputText = document.getElementById('output-text');
const encryptBtn = document.getElementById('encrypt-btn');
const decryptBtn = document.getElementById('decrypt-btn');
const clearBtn = document.getElementById('clear-btn');
const copyBtn = document.getElementById('copy-btn');
const encryptionTypeRadios = document.querySelectorAll('input[name="encryption-type"]');
const encryptionPassword = document.getElementById('encryption-password');
const strengthIndicator = document.getElementById('strength-indicator');
const strengthText = document.getElementById('strength-text');

// 用于无密码模式的默认密钥
const DEFAULT_KEY = 'default_text_encryption_key_2025';

// 加密配置常量
const ENCRYPTION_CONFIG = {
    meow: {
        name: '咩模式',
        chars: ['咩'],
        separator: ''
    },
    gulu: {
        name: '咕/噜模式',
        chars: ['咕', '噜'],
        separator: ''
    }
};

// 当前选择的加密模式
let currentEncryptionMode = 'meow';

// 初始化事件监听器
function initEventListeners() {
    // 加密按钮点击事件
    encryptBtn.addEventListener('click', handleEncrypt);

    // 解密按钮点击事件
    decryptBtn.addEventListener('click', handleDecrypt);

    // 清空按钮点击事件
    clearBtn.addEventListener('click', handleClear);

    // 复制按钮点击事件
    copyBtn.addEventListener('click', handleCopy);

    // 加密模式切换事件
    encryptionTypeRadios.forEach(radio => {
        radio.addEventListener('change', (e) => {
            currentEncryptionMode = e.target.value;
        });
    });

    // 密码输入事件 - 实时检测密码强度
    encryptionPassword.addEventListener('input', checkPasswordStrength);
}

/**
 * 检查密码强度
 */
function checkPasswordStrength() {
    const password = encryptionPassword.value;

    if (!password) {
        strengthIndicator.className = '';
        strengthText.textContent = '密码强度: 未输入';
        return;
    }

    let strength = 0;

    // 密码长度检查
    if (password.length >= 8) strength++;
    if (password.length >= 12) strength++;

    // 包含数字
    if (/\d/.test(password)) strength++;

    // 包含小写字母
    if (/[a-z]/.test(password)) strength++;

    // 包含大写字母
    if (/[A-Z]/.test(password)) strength++;

    // 包含特殊字符
    if (/[^A-Za-z0-9]/.test(password)) strength++;

    // 更新密码强度指示器
    if (strength <= 2) {
        strengthIndicator.className = 'strength-weak';
        strengthText.textContent = '密码强度: 弱';
    } else if (strength <= 4) {
        strengthIndicator.className = 'strength-medium';
        strengthText.textContent = '密码强度: 中';
    } else {
        strengthIndicator.className = 'strength-strong';
        strengthText.textContent = '密码强度: 强';
    }
}

/**
 * 加密文本处理函数
 */
function handleEncrypt() {
    const text = inputText.value.trim();
    let password = encryptionPassword.value.trim();

    if (!text) {
        alert('请输入要加密的文本');
        return;
    }

    // 如果密码为空，使用默认密钥（用于无密码模式）
    if (password.length === 0) {
        password = DEFAULT_KEY;
    }

    try {
        const encrypted = encryptText(text, password, currentEncryptionMode);
        outputText.value = encrypted;
    } catch (error) {
        console.error('加密过程出错:', error);
        alert('加密过程中出现错误: ' + error.message);
    }
}

/**
 * 解密文本处理函数
 */
function handleDecrypt() {
    const encryptedText = inputText.value.trim();
    let password = encryptionPassword.value.trim();

    if (!encryptedText) {
        alert('请输入要解密的文本');
        return;
    }

    // 如果密码为空，使用默认密钥（用于无密码模式）
    if (password.length === 0) {
        password = DEFAULT_KEY;
    }

    try {
        const decrypted = decryptText(encryptedText, password);
        outputText.value = decrypted;
    } catch (error) {
        console.error('解密过程出错:', error);
        alert('解密失败: ' + error.message);
    }
}

/**
 * 清空文本处理函数
 */
function handleClear() {
    inputText.value = '';
    outputText.value = '';
    encryptionPassword.value = '';
    strengthIndicator.className = '';
    strengthText.textContent = '密码强度: 未输入';
    inputText.focus();
}

/**
 * 复制结果处理函数
 */
function handleCopy() {
    if (outputText.value) {
        try {
            // 先尝试现代的复制API
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(outputText.value).then(() => {
                    showCopyFeedback();
                }).catch(err => {
                    console.error('Clipboard API 复制失败:', err);
                    fallbackCopy();
                });
            } else {
                // 如果不支持现代API，直接使用降级方案
                fallbackCopy();
            }
        } catch (error) {
            console.error('复制过程发生错误:', error);
            // 最后的降级方案
            try {
                outputText.select();
                outputText.setSelectionRange(0, 99999);
                const successful = document.execCommand('copy');
                if (successful) {
                    showCopyFeedback();
                } else {
                    alert('复制失败，文本已选中，请手动复制 (Ctrl+C)');
                }
            } catch (finalError) {
                console.error('所有复制方法都失败了:', finalError);
                outputText.select();
                alert('复制失败，文本已选中，请手动复制 (Ctrl+C)');
            }
        }
    }
}

/**
 * 降级复制方案
 */
function fallbackCopy() {
    try {
        // 创建一个临时的textarea元素用于复制
        const tempTextarea = document.createElement('textarea');
        tempTextarea.value = outputText.value;
        tempTextarea.style.position = 'fixed';
        tempTextarea.style.opacity = '0';
        tempTextarea.style.left = '-9999px';
        document.body.appendChild(tempTextarea);
        tempTextarea.select();
        tempTextarea.setSelectionRange(0, 99999); // 兼容移动设备

        // 尝试执行复制命令
        const successful = document.execCommand('copy');

        // 移除临时元素
        document.body.removeChild(tempTextarea);

        if (successful) {
            showCopyFeedback();
        } else {
            throw new Error('复制命令执行失败');
        }
    } catch (error) {
        console.error('降级复制方案失败:', error);
        // 最后的手动选择方案
        outputText.select();
        outputText.setSelectionRange(0, 99999);
        alert('自动复制失败，文本已选中，请手动复制 (Ctrl+C)');
    }
}

/**
 * 显示复制成功的反馈
 */
function showCopyFeedback() {
    const originalText = copyBtn.textContent;
    copyBtn.textContent = '已复制!';
    copyBtn.classList.add('copied');

    setTimeout(() => {
        copyBtn.textContent = originalText;
        copyBtn.classList.remove('copied');
    }, 2000);
}

/**
 * 文本加密核心函数 - 使用AES-256
 * @param {string} text - 要加密的文本
 * @param {string} password - 加密密码
 * @param {string} mode - 加密模式
 * @returns {string} 加密后的文本
 */
function encryptText(text, password, mode) {
    const config = ENCRYPTION_CONFIG[mode];
    if (!config) {
        throw new Error('未知的加密模式');
    }

    try {
        // 生成密钥 - 使用PBKDF2从密码派生
        const salt = CryptoJS.lib.WordArray.random(128 / 8);
        const key = CryptoJS.PBKDF2(password, salt, {
            keySize: 256 / 32,
            iterations: 10000
        });

        // 使用AES-256-CBC模式加密
        const iv = CryptoJS.lib.WordArray.random(128 / 8);
        const encrypted = CryptoJS.AES.encrypt(text, key, {
            iv: iv,
            mode: CryptoJS.mode.CBC,
            padding: CryptoJS.pad.Pkcs7
        });

        // 将加密结果、salt和iv组合成一个字符串
        const combined = salt.toString() + iv.toString() + encrypted.toString();

        // 将组合后的字符串转换为拟声词
        return convertToSoundWords(combined, config);
    } catch (error) {
        throw new Error('AES加密失败: ' + error.message);
    }
}

/**
 * 文本解密核心函数 - 使用AES-256
 * @param {string} encryptedText - 要解密的文本
 * @param {string} password - 解密密码
 * @returns {string} 解密后的文本
 */
function decryptText(encryptedText, password) {
    try {
        // 检测加密模式
        const isMeowMode = encryptedText.includes('咩');
        const isGuluMode = encryptedText.includes('咕') || encryptedText.includes('噜');

        let config;

        if (isMeowMode && !isGuluMode) {
            config = ENCRYPTION_CONFIG.meow;
        } else if (isGuluMode && !isMeowMode) {
            config = ENCRYPTION_CONFIG.gulu;
        } else if (isGuluMode && isMeowMode) {
            // 如果同时包含两种字符，优先选择咕噜模式
            config = ENCRYPTION_CONFIG.gulu;
        } else {
            throw new Error('无法识别的加密文本格式');
        }

        // 从拟声词恢复原始加密数据
        const encryptedData = convertFromSoundWords(encryptedText, config);

        // 分离salt、iv和密文
        const salt = CryptoJS.enc.Hex.parse(encryptedData.substring(0, 32));
        const iv = CryptoJS.enc.Hex.parse(encryptedData.substring(32, 64));
        const ciphertext = encryptedData.substring(64);

        // 生成密钥
        const key = CryptoJS.PBKDF2(password, salt, {
            keySize: 256 / 32,
            iterations: 10000
        });

        // 解密
        const decrypted = CryptoJS.AES.decrypt(ciphertext, key, {
            iv: iv,
            mode: CryptoJS.mode.CBC,
            padding: CryptoJS.pad.Pkcs7
        });

        // 转换为UTF-8字符串
        const plaintext = decrypted.toString(CryptoJS.enc.Utf8);

        if (!plaintext) {
            throw new Error('解密失败，密码可能不正确或文本格式无效');
        }

        return plaintext;
    } catch (error) {
        // 更详细的错误信息
        if (error.message.includes('Malformed UTF-8 data')) {
            throw new Error('解密失败，密码可能不正确');
        }
        throw error;
    }
}

/**
 * 将字符串转换为拟声词组合
 * @param {string} data - 要转换的数据
 * @param {Object} config - 加密配置
 * @returns {string} 转换后的拟声词
 */
function convertToSoundWords(data, config) {
    let result = '';
    const chars = config.chars;
    const base = chars.length;

    // 特殊处理单字符模式（如咩模式）
    if (base === 1) {
        // 对于单字符模式，使用16进制表示每个字符，然后用单个字符重复表示
        for (let i = 0; i < data.length; i++) {
            const charCode = data.charCodeAt(i);
            // 将字符编码转换为16进制，然后用字符重复次数表示
            const hexValue = charCode.toString(16).padStart(4, '0');
            for (let j = 0; j < hexValue.length; j++) {
                const digit = parseInt(hexValue[j], 16);
                // 每个16进制数字用1-16个字符表示（0用16个字符表示）
                const repeatCount = digit === 0 ? 16 : digit;
                result += chars[0].repeat(repeatCount) + '|'; // 使用分隔符
            }
        }
        return result;
    }

    // 计算需要多少个字符才能表示所有可能的字符编码 (0-65535)
    const charsNeeded = Math.ceil(Math.log(65536) / Math.log(base));

    // 遍历数据的每个字符
    for (let i = 0; i < data.length; i++) {
        const charCode = data.charCodeAt(i);

        // 将字符编码转换为指定进制
        let value = charCode;
        let soundChars = '';

        // 生成足够的字符来表示字符编码
        for (let j = 0; j < charsNeeded; j++) {
            soundChars = chars[value % base] + soundChars;
            value = Math.floor(value / base);
        }

        result += soundChars;
    }

    return result;
}

/**
 * 从拟声词组合恢复原始数据
 * @param {string} soundWords - 拟声词组合
 * @param {Object} config - 加密配置
 * @returns {string} 恢复的数据
 */
function convertFromSoundWords(soundWords, config) {
    let result = '';
    const chars = config.chars;
    const base = chars.length;

    // 特殊处理单字符模式（如咩模式）
    if (base === 1) {
        const char = chars[0];
        const segments = soundWords.split('|').filter(seg => seg.length > 0);

        let hexString = '';
        for (let i = 0; i < segments.length; i++) {
            const segment = segments[i];
            // 检查是否只包含指定字符
            if (!segment.split('').every(c => c === char)) {
                throw new Error('加密文本包含无效字符');
            }

            // 计算重复次数，转换为16进制数字
            const repeatCount = segment.length;
            const digit = repeatCount === 16 ? 0 : repeatCount;
            hexString += digit.toString(16);
        }

        // 每4个16进制字符组成一个字符编码
        for (let i = 0; i < hexString.length; i += 4) {
            const hexCode = hexString.substring(i, i + 4);
            if (hexCode.length === 4) {
                const charCode = parseInt(hexCode, 16);
                result += String.fromCharCode(charCode);
            }
        }

        return result;
    }

    // 计算每个字符需要多少个拟声词字符 (与加密时保持一致)
    const charsPerChar = Math.ceil(Math.log(65536) / Math.log(base));

    // 检查长度是否正确
    if (soundWords.length % charsPerChar !== 0) {
        throw new Error('加密文本格式错误，长度不匹配');
    }

    // 遍历拟声词文本
    for (let i = 0; i < soundWords.length; i += charsPerChar) {
        const soundGroup = soundWords.substring(i, i + charsPerChar);

        // 检查组合是否只包含当前模式的字符
        let valid = true;
        for (let j = 0; j < soundGroup.length; j++) {
            if (!chars.includes(soundGroup[j])) {
                valid = false;
                break;
            }
        }

        if (!valid) {
            throw new Error('加密文本包含无效字符');
        }

        // 将拟声词转换回字符编码
        let charCode = 0;
        for (let j = 0; j < soundGroup.length; j++) {
            const charIndex = chars.indexOf(soundGroup[j]);
            charCode += charIndex * Math.pow(base, soundGroup.length - 1 - j);
        }

        // 确保字符编码在有效范围内
        if (charCode > 65535) {
            throw new Error('解密过程中遇到无效的字符编码');
        }

        result += String.fromCharCode(charCode);
    }

    return result;
}

// 初始化应用
function initApp() {
    initEventListeners();
    inputText.focus();
    console.log('文本加密工具已初始化 - 基于AES-256加密');
}

// 页面加载完成后初始化应用
document.addEventListener('DOMContentLoaded', initApp);
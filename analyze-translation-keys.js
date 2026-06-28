const fs = require('fs');
const path = require('path');

// translation file paths
const localesDir = path.join(__dirname, 'client', 'src', 'locales');
const translationFiles = [
  'en-US.json',
  'zh-CN.json', 
  'zh-TW.json'
];

// source code directory
const srcDir = path.join(__dirname, 'client', 'src');

// function to extract translation keys from a JSON file
function extractKeysFromTranslationFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const translations = JSON.parse(content);
    const keys = [];
    
    function traverse(obj, prefix = '') {
      for (const key in obj) {
        if (obj.hasOwnProperty(key)) {
          const fullKey = prefix ? `${prefix}.${key}` : key;
          if (typeof obj[key] === 'object' && obj[key] !== null) {
            traverse(obj[key], fullKey);
          } else {
            keys.push(fullKey);
          }
        }
      }
    }
    
    traverse(translations);
    return keys;
  } catch (error) {
    console.error(`Error reading translation file ${filePath}:`, error.message);
    return [];
  }
}

// search translation key usage in source code
function searchKeysInSourceCode(keys) {
  const usedKeys = new Set();
  
  function searchInFile(filePath) {
    try {
      const content = fs.readFileSync(filePath, 'utf8');
      // match $t('key') or t('key') patterns
      const regex = /\$t\(['"]([^'"]+)['"]\)|t\(['"]([^'"]+)['"]\)/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        const key = match[1] || match[2];
        usedKeys.add(key);
      }
    } catch (error) {
      // ignore read errors
    }
  }
  
  function traverseDirectory(dir) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
      const fullPath = path.join(dir, file);
      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        if (file !== 'node_modules' && file !== '.git') {
          traverseDirectory(fullPath);
        }
      } else if (['.vue', '.js', '.jsx', '.ts', '.tsx'].includes(path.extname(file))) {
        searchInFile(fullPath);
      }
    }
  }
  
  traverseDirectory(srcDir);
  return usedKeys;
}

// main function to analyze translation key usage in source code
function main() {
  console.log('Analyzing translation key usage...');
  
  // extract all translation keys
  const allKeys = new Set();
  const keysByFile = {};
  
  for (const file of translationFiles) {
    const filePath = path.join(localesDir, file);
    const keys = extractKeysFromTranslationFile(filePath);
    keysByFile[file] = keys;
    keys.forEach(key => allKeys.add(key));
  }
  
  console.log(`Total ${allKeys.size} translation keys extracted`);
  
  // search for key usage in source code
  const usedKeys = searchKeysInSourceCode(allKeys);
  console.log(`Found ${usedKeys.size} used translation keys in source code`);
  
  // find unused keys
  const unusedKeys = [];
  allKeys.forEach(key => {
    if (!usedKeys.has(key)) {
      unusedKeys.push(key);
    }
  });
  
  console.log(`Found ${unusedKeys.length} unused translation keys`);
  
  // generate analysis report
  const reportPath = path.join(__dirname, 'translation-keys-analysis.txt');
  let reportContent = `Translation key usage analysis report\n`;
  reportContent += `Generated at: ${new Date().toLocaleString()}\n`;
  reportContent += `=====================================\n\n`;
  
  reportContent += `1. Overall statistics\n`;
  reportContent += `-----------------\n`;
  reportContent += `Total translation keys extracted: ${allKeys.size}\n`;
  reportContent += `Used translation keys: ${usedKeys.size}\n`;
  reportContent += `Unused translation keys: ${unusedKeys.length}\n`;
  reportContent += `Usage rate: ${((usedKeys.size / allKeys.size) * 100).toFixed(2)}%\n\n`;
  
  reportContent += `2. Unused translation keys\n`;
  reportContent += `-----------------\n`;
  if (unusedKeys.length > 0) {
    unusedKeys.sort().forEach(key => {
      reportContent += `- ${key}\n`;
    });
  } else {
    reportContent += `All translation keys are in use\n`;
  }
  reportContent += `\n`;
  
  reportContent += `3. Translation key statistics by file type\n`;
  reportContent += `-----------------\n`;
  for (const file in keysByFile) {
    const keys = keysByFile[file];
    const fileUsedKeys = keys.filter(key => usedKeys.has(key));
    const fileUnusedKeys = keys.filter(key => !usedKeys.has(key));
    
    reportContent += `${file}:\n`;
    reportContent += `  Total keys extracted: ${keys.length}\n`;
    reportContent += `  Used keys: ${fileUsedKeys.length}\n`;
    reportContent += `  Unused keys: ${fileUnusedKeys.length}\n`;
    reportContent += `  Usage rate: ${((fileUsedKeys.length / keys.length) * 100).toFixed(2)}%\n\n`;
  }
  
  fs.writeFileSync(reportPath, reportContent, 'utf8');
  console.log(`Analysis report generated: ${reportPath}`);
}

// run analysis
main();
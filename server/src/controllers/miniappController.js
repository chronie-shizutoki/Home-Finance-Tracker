const fs = require('fs').promises;
const path = require('path');

// 小程序目录路径
const MINIAPP_DIR = path.join(__dirname, '../../..', 'client/public/miniapp');

/**
 * 扫描小程序目录，返回所有有效的小程序信息
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 */
exports.getMiniAppList = async (req, res) => {
  try {
    // 扫描小程序目录
    const miniApps = await scanMiniAppDirectory();
    
    res.status(200).json(miniApps);
  } catch (error) {
    console.error('获取小程序列表失败:', error);
    res.status(500).json({
      success: false,
      message: '获取小程序列表失败',
      error: error.message
    });
  }
};

/**
 * 扫描小程序目录，返回有效的小程序信息
 * @returns {Array} 小程序信息数组
 */
async function scanMiniAppDirectory() {
  try {
    // 检查小程序目录是否存在
    try {
      await fs.access(MINIAPP_DIR);
    } catch (error) {
      // 如果目录不存在，创建它
      await fs.mkdir(MINIAPP_DIR, { recursive: true });
      return [];
    }
    
    // 读取小程序目录下的所有子目录
    const entries = await fs.readdir(MINIAPP_DIR, { withFileTypes: true });
    const directories = entries.filter(entry => entry.isDirectory());
    
    // 收集所有有效的小程序信息
    const miniApps = [];
    
    for (const dir of directories) {
      const appPath = path.join(MINIAPP_DIR, dir.name);
      const indexHtmlPath = path.join(appPath, 'index.html');
      
      try {
        // 检查是否存在index.html文件
        await fs.access(indexHtmlPath);
        
        // 获取小程序信息
        const appInfo = {
          id: dir.name,
          name: dir.name.charAt(0).toUpperCase() + dir.name.slice(1), // 首字母大写作为默认名称
          description: `小程序: ${dir.name}`,
          path: `/miniapp/${dir.name}/index.html`,
          author: 'Unknown',
          version: '1.0.0',
          lastModified: new Date().toISOString()
        };
        
        // 尝试读取package.json文件获取更多信息
        try {
          const packageJsonPath = path.join(appPath, 'package.json');
          await fs.access(packageJsonPath);
          const packageJson = JSON.parse(await fs.readFile(packageJsonPath, 'utf8'));
          
          if (packageJson.name) appInfo.name = packageJson.name;
          if (packageJson.description) appInfo.description = packageJson.description;
          if (packageJson.author) appInfo.author = packageJson.author;
          if (packageJson.version) appInfo.version = packageJson.version;
        } catch (error) {
          // 如果没有package.json文件，使用默认信息
          console.log(`小程序 ${dir.name} 没有package.json文件，使用默认信息`);
        }
        
        miniApps.push(appInfo);
      } catch (error) {
        // 如果没有index.html文件，跳过这个目录
        console.log(`目录 ${dir.name} 不是有效的小程序，缺少index.html文件`);
      }
    }
    
    return miniApps;
  } catch (error) {
    console.error('扫描小程序目录失败:', error);
    return [];
  }
}
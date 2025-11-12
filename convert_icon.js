const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

// 定义不同密度的尺寸
const densities = [
  { name: 'mdpi', size: 48 },    // 48x48
  { name: 'hdpi', size: 72 },    // 72x72
  { name: 'xhdpi', size: 96 },   // 96x96
  { name: 'xxhdpi', size: 144 }, // 144x144
  { name: 'xxxhdpi', size: 192 } // 192x192
];

// 定义需要生成的图标类型
const iconTypes = ['ic_launcher.png', 'ic_launcher_round.png'];

// 源PNG文件路径（使用现有的高清图标）
const pngPath = path.join(__dirname, 'app-icon.png');

// 目标目录路径
const androidResPath = path.join(__dirname, 'android', 'app', 'src', 'main', 'res');

async function convertPngToDifferentDensities() {
  try {
    // 确保源文件存在
    if (!fs.existsSync(pngPath)) {
      throw new Error(`源文件不存在: ${pngPath}`);
    }
    
    console.log(`使用高清图标: ${pngPath} 进行转换`);
    
    for (const density of densities) {
      // 创建目标目录
      const mipmapDir = path.join(androidResPath, `mipmap-${density.name}`);
      if (!fs.existsSync(mipmapDir)) {
        fs.mkdirSync(mipmapDir, { recursive: true });
      }
      
      // 转换为相应尺寸的PNG并保存
      const outputPath = path.join(mipmapDir, 'ic_launcher.png');
      await sharp(pngPath)
        .resize(density.size, density.size, {
          fit: 'inside',
          withoutEnlargement: true,
          background: { r: 0, g: 0, b: 0, alpha: 0 }
        })
        .png({
          quality: 100,
          compressionLevel: 0
        })
        .toFile(outputPath);
      
      console.log(`已生成 ${density.name} 图标: ${outputPath}`);
    }
    
    console.log('方形图标转换完成！');
  } catch (error) {
    console.error('转换过程中出错:', error);
  }
}

// 转换并复制圆形图标
async function convertAndCopyIcons() {
  await convertPngToDifferentDensities();
  
  // 复制方形图标为圆形图标
  for (const density of densities) {
    const sourcePath = path.join(androidResPath, `mipmap-${density.name}`, 'ic_launcher.png');
    const targetPath = path.join(androidResPath, `mipmap-${density.name}`, 'ic_launcher_round.png');
    
    if (fs.existsSync(sourcePath)) {
      fs.copyFileSync(sourcePath, targetPath);
      console.log(`已复制 ${density.name} 圆形图标: ${targetPath}`);
    }
  }
  
  console.log('所有图标（包括圆形图标）处理完成！');
}

// 执行转换和复制
convertAndCopyIcons();
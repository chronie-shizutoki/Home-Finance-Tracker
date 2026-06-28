const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

// define different densities
const densities = [
  { name: 'mdpi', size: 48 },    // 48x48
  { name: 'hdpi', size: 72 },    // 72x72
  { name: 'xhdpi', size: 96 },   // 96x96
  { name: 'xxhdpi', size: 144 }, // 144x144
  { name: 'xxxhdpi', size: 192 } // 192x192
];

// define icon types
const iconTypes = ['ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'];

// source PNG file path (use existing high-resolution icon)
const pngPath = path.join(__dirname, 'app-icon.png');

// target directory path
const androidResPath = path.join(__dirname, 'android', 'app', 'src', 'main', 'res');

async function convertPngToDifferentDensities() {
  try {
    // ensure source file exists
    if (!fs.existsSync(pngPath)) {
      throw new Error(`Source file not found: ${pngPath}`);
    }
    
    console.log(`High-resolution icon used: ${pngPath} for conversion`);
    
    for (const density of densities) {
      // create target directory
      const mipmapDir = path.join(androidResPath, `mipmap-${density.name}`);
      if (!fs.existsSync(mipmapDir)) {
        fs.mkdirSync(mipmapDir, { recursive: true });
      }
      
      // convert to corresponding size PNG and save, ensuring 25% white border (content 75%)
      const outputPath = path.join(mipmapDir, 'ic_launcher.png');
      
      // calculate content size (target size 50%)
      const contentSize = Math.round(density.size * 0.5);
      
      await sharp(pngPath)
        // first resize icon to content area size (75%)
        .resize(contentSize, contentSize, {
          fit: 'inside',
          withoutEnlargement: true
        })
        // then center display on full-size transparent canvas
        .extend({
          top: Math.round((density.size - contentSize) / 2),
          bottom: Math.round((density.size - contentSize) / 2),
          left: Math.round((density.size - contentSize) / 2),
          right: Math.round((density.size - contentSize) / 2),
          background: { r: 0, g: 0, b: 0, alpha: 0 }
        })
        .png({
          quality: 100,
          compressionLevel: 0
        })
        .toFile(outputPath);
      
      console.log(`Generated ${density.name} icon: ${outputPath}`);
    }
    
    console.log('Square icon conversion completed!');
  } catch (error) {
    console.error('Conversion error:', error);
  }
}

// convert and copy all icon types
async function convertAndCopyIcons() {
  // first generate ic_launcher.png
  await convertPngToDifferentDensities();
  
  // copy generated other icon types
  for (const density of densities) {
    const sourcePath = path.join(androidResPath, `mipmap-${density.name}`, 'ic_launcher.png');
    
    if (fs.existsSync(sourcePath)) {
      // copy to round icon
      const roundPath = path.join(androidResPath, `mipmap-${density.name}`, 'ic_launcher_round.png');
      fs.copyFileSync(sourcePath, roundPath);
      console.log(`Copied ${density.name} round icon: ${roundPath}`);
      
      // copy to foreground icon
      const foregroundPath = path.join(androidResPath, `mipmap-${density.name}`, 'ic_launcher_foreground.png');
      fs.copyFileSync(sourcePath, foregroundPath);
      console.log(`Copied ${density.name} foreground icon: ${foregroundPath}`);
    }
  }
  
  console.log('All icon types processed!');
}

// execute conversion and copy
convertAndCopyIcons();
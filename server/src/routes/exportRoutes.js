/**
 * 导出功能路由配置
 * @module routes/exportRoutes
 * @desc 定义消费记录导出相关API端点（Excel）
 */
const express = require('express')
const router = express.Router()
const multer = require('multer')
const path = require('path')
const ExportService = require('../utils/export')
const ImportService = require('../utils/import')

const exportService = new ExportService()
const importService = new ImportService()

// 配置文件上传
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = path.join(__dirname, '../../uploads/')
    require('fs').mkdirSync(uploadDir, { recursive: true })
    cb(null, uploadDir)
  },
  filename: (req, file, cb) => {
    cb(null, `import_${Date.now()}_${file.originalname}`)
  }
})
const upload = multer({ storage })

/**
 * @api {get} /api/export/excel 导出Excel文件
 * @apiName ExportExcel
 * @apiGroup Export
 * @apiSuccess {file} Excel文件 消费记录的Excel文件
 */
router.get('/api/export/excel', async (req, res) => {
  try {
    const filePath = exportService.generateExcel()
    res.download(filePath)
  } catch (error) {
    res.status(500).send('生成Excel失败')
  }
})

/**
 * @api {post} /api/import/excel 导入Excel文件
 * @apiName ImportExcel
 * @apiGroup Import
 * @apiParam {file} file Excel文件
 * @apiSuccess {object} result 导入结果
 */
router.post('/api/import/excel', upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, message: '未上传文件' })
    }
    const result = await importService.importFromExcel(req.file.path)
    res.json(result)
  } catch (error) {
    res.status(500).json({ success: false, message: error.message })
  }
})

module.exports = router

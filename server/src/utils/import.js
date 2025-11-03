const XLSX = require('xlsx')
const { Expense } = require('../db')
const dayjs = require('dayjs')

class ImportService {
  async importFromExcel (filePath) {
    const workbook = XLSX.readFile(filePath)
    const sheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[sheetName]
    const data = XLSX.utils.sheet_to_json(worksheet)

    // 数据验证和转换
    const records = data.map(item => ({
      type: item['分类'] || item.Category,
      remark: item['备注'] || item.Description || '',
      amount: parseFloat((item['金额'] || item['Amount'] || '').toString().replace(/[^0-9.]/g, '')) || 0,
      time: dayjs(item['日期'] || item.Date).toDate()
    })).filter(r => r.type && r.amount > 0 && r.time)

    if (records.length === 0) {
      return { success: true, message: '没有有效数据被导入。' }
    }

    try {
      await Expense.bulkCreate(records)
      return { success: true, message: `成功导入 ${records.length} 条记录。` }
    } catch (error) {
      console.error('导入Excel数据失败:', error)
      throw new Error('数据库插入失败。')
    }
  }
}

module.exports = ImportService

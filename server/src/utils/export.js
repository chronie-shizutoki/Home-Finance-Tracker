const XLSX = require('xlsx')
const fs = require('fs')
const path = require('path')
const { Expense } = require('../db')

class ExportService {
  constructor () {}

  // 获取完整消费数据（异步方法）
  async getFullData () {
    return await Expense.findAll({ raw: true }) // 使用 Expense 模型获取数据
  }

  // 生成Excel文件
  async generateExcel () {
    const data = await this.getFullData()
    
    // 处理数据，确保date字段存在且格式正确
    const processedData = data.map(expense => ({
      type: expense.type,
      remark: expense.remark || '',
      amount: expense.amount,
      date: expense.date || new Date().toISOString().split('T')[0] // 确保有日期，如果没有则使用今天日期
    }))
    
    const worksheet = XLSX.utils.json_to_sheet(processedData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, '消费记录')

    const filePath = path.join(__dirname, '../../exports/', `expenses_${Date.now()}.xlsx`)
    XLSX.writeFile(workbook, filePath)
    return filePath
  }
}

module.exports = ExportService

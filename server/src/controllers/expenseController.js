const { Expense, sequelize } = require('../db')
const dayjs = require('dayjs')
const { Op } = require('sequelize')

const getExpenses = async (req, res) => {
  try {
    const { page = 1, limit = 20, keyword, type, month, minAmount, maxAmount, sort = 'dateDesc' } = req.query
    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)

    const offset = (pageNum - 1) * pageSize

    // 构建查询条件
    const where = {}
    if (type) where.type = type
    if (month) {
      // 解析月份并构建日期范围
      try {
        const [year, monthNum] = month.split('-').map(Number)
        const startDate = new Date(year, monthNum - 1, 1)
        const endDate = new Date(year, monthNum, 0, 23, 59, 59, 999)
        where.time = {
          [Op.between]: [startDate, endDate]
        }
      } catch (error) {
        console.warn('无效的月份格式:', month)
      }
    }
    // 金额范围筛选，添加有效性检查
    const validMinAmount = parseFloat(minAmount)
    const validMaxAmount = parseFloat(maxAmount)
    if (!isNaN(validMinAmount)) {
      where.amount = { ...where.amount, [Op.gte]: validMinAmount }
    }
    if (!isNaN(validMaxAmount)) {
      where.amount = { ...where.amount, [Op.lte]: validMaxAmount }
    }
    // 关键词搜索，移除对time字段的like搜索（time是日期类型，不应该用like）
    if (keyword) {
      where[Op.or] = [
        { type: { [Op.like]: `%${keyword}%` } },
        { remark: { [Op.like]: `%${keyword}%` } }
      ]
      // 如果需要日期搜索，可以添加适当的日期查询逻辑
    }

    // 构建排序规则
    let order = []
    switch (sort) {
      case 'dateAsc':
        order = [['time', 'ASC']]
        break
      case 'dateDesc':
        order = [['time', 'DESC']]
        break
      case 'amountAsc':
        order = [['amount', 'ASC']]
        break
      case 'amountDesc':
        order = [['amount', 'DESC']]
        break
      default:
        order = [['time', 'DESC']]
    }

    const { count, rows } = await Expense.findAndCountAll({
      where,
      limit: pageSize,
      offset: offset,
      order
    })

    // 获取唯一类型和可用月份信息（排除当前的筛选条件）
    const meta = {
      uniqueTypes: [],
      availableMonths: []
    }
    
    try {
      // 获取所有唯一类型
      const typesResult = await Expense.findAll({
        attributes: [['type', 'type']],
        group: ['type'],
        where: {}, // 不使用筛选条件，获取所有类型
        raw: true
      })
      
      meta.uniqueTypes = typesResult
        .map(item => item.type)
        .filter(type => type && type.trim() !== '')
        .sort()
      
      // 获取所有可用月份（格式：YYYY-MM）
      // 使用SQLite的strftime函数替代MySQL的DATE_FORMAT
      const monthsResult = await Expense.findAll({
        attributes: [
          [
            sequelize.fn('strftime', '%Y-%m', sequelize.col('time')),
            'month'
          ]
        ],
        group: ['month'],
        where: {}, // 不使用筛选条件，获取所有月份
        order: [[sequelize.literal('month'), 'DESC']],
        raw: true
      })
      
      meta.availableMonths = monthsResult.map(item => item.month)
    } catch (metaError) {
      console.warn('获取元数据时出错，但不影响主要功能:', metaError)
    }

    res.json({
      data: rows,
      total: count,
      page: pageNum,
      limit: pageSize,
      meta
    })
  } catch (err) {
    console.error('获取消费记录失败:', err)
    res.status(500).json({ error: '读取数据失败' })
  }
}

const addExpense = async (req, res) => {
  try {
    const { type, remark, amount, time } = req.body

    // 后端数据验证
    if (!type || !amount) {
      return res.status(400).json({ error: '消费类型和金额是必填项' })
    }

    const newExpense = await Expense.create({
      type,
      remark,
      amount: parseFloat(amount),
      time: time ? dayjs(time).toDate() : dayjs().toDate()
    })

    res.status(201).json(newExpense)
  } catch (error) {
    console.error('添加消费记录失败:', error)
    res.status(500).json({ error: '无法添加记录' })
  }
}

const deleteExpense = async (req, res) => {
  try {
    const { id } = req.params
    const deleted = await Expense.destroy({
      where: { id: id }
    })

    if (deleted) {
      res.status(204).send()
    } else {
      res.status(404).json({ error: '未找到要删除的记录' })
    }
  } catch (error) {
    console.error('删除消费记录失败:', error)
    res.status(500).json({ error: '无法删除记录' })
  }
}

// 统计功能API
const getStatistics = async (req, res) => {
  try {
    const { type, month, keyword, minAmount, maxAmount } = req.query

    // 构建查询条件，与getExpenses相同
    const where = {}
    if (type) where.type = type
    if (month) {
      try {
        const [year, monthNum] = month.split('-').map(Number)
        const startDate = new Date(year, monthNum - 1, 1)
        const endDate = new Date(year, monthNum, 0, 23, 59, 59, 999)
        where.time = {
          [Op.between]: [startDate, endDate]
        }
      } catch (error) {
        console.warn('无效的月份格式:', month)
      }
    }
    // 金额范围筛选
    const validMinAmount = parseFloat(minAmount)
    const validMaxAmount = parseFloat(maxAmount)
    if (!isNaN(validMinAmount)) {
      where.amount = { ...where.amount, [Op.gte]: validMinAmount }
    }
    if (!isNaN(validMaxAmount)) {
      where.amount = { ...where.amount, [Op.lte]: validMaxAmount }
    }
    // 关键词搜索
    if (keyword) {
      where[Op.or] = [
        { type: { [Op.like]: `%${keyword}%` } },
        { remark: { [Op.like]: `%${keyword}%` } }
      ]
    }

    // 使用aggregate函数进行统计计算
    const statsResult = await Expense.findAll({
      where,
      attributes: [
        [sequelize.fn('COUNT', sequelize.col('id')), 'count'],
        [sequelize.fn('SUM', sequelize.col('amount')), 'totalAmount'],
        [sequelize.fn('AVG', sequelize.col('amount')), 'averageAmount'],
        [sequelize.fn('MIN', sequelize.col('amount')), 'minAmount'],
        [sequelize.fn('MAX', sequelize.col('amount')), 'maxAmount']
      ],
      raw: true
    })

    const stats = statsResult[0]
    
    // 获取类型分布统计
    const typeDistributionResult = await Expense.findAll({
      where,
      attributes: [
        'type',
        [sequelize.fn('COUNT', sequelize.col('id')), 'count'],
        [sequelize.fn('SUM', sequelize.col('amount')), 'amount']
      ],
      group: ['type'],
      raw: true
    })

    // 计算类型分布的百分比
    const typeDistribution = {}
    const totalCount = parseInt(stats.count)
    
    typeDistributionResult.forEach(item => {
      const type = item.type || '未知类型'
      typeDistribution[type] = {
        count: parseInt(item.count),
        amount: parseFloat(item.amount),
        percentage: totalCount > 0 ? Math.round((parseInt(item.count) / totalCount) * 100) : 0
      }
    })

    // 计算中位数
    let medianAmount = 0
    if (totalCount > 0) {
      const sortedAmounts = await Expense.findAll({
        where,
        attributes: ['amount'],
        order: [['amount', 'ASC']],
        raw: true
      })
      
      const amounts = sortedAmounts.map(item => parseFloat(item.amount))
      if (amounts.length > 0) {
        if (amounts.length % 2 === 0) {
          medianAmount = (amounts[amounts.length / 2 - 1] + amounts[amounts.length / 2]) / 2
        } else {
          medianAmount = amounts[Math.floor(amounts.length / 2)]
        }
      }
    }

    res.json({
      count: parseInt(stats.count) || 0,
      totalAmount: parseFloat(stats.totalAmount) || 0,
      averageAmount: parseFloat(stats.averageAmount) || 0,
      medianAmount: medianAmount || 0,
      minAmount: parseFloat(stats.minAmount) || 0,
      maxAmount: parseFloat(stats.maxAmount) || 0,
      typeDistribution
    })
  } catch (error) {
    console.error('获取统计数据失败:', error)
    res.status(500).json({ error: '获取统计数据失败' })
  }
}

module.exports = {
  getExpenses,
  addExpense,
  deleteExpense,
  getStatistics
}

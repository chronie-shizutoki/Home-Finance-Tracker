const { Debt } = require('../db')
const dayjs = require('dayjs')

// 获取所有债务记录（支持分页）
const getDebts = async (req, res) => {
  try {
    const { page = 1, limit = 20, type, isRepaid } = req.query
    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)

    const offset = (pageNum - 1) * pageSize
    
    // 构建查询条件
    const where = {}
    if (type && ['lend', 'borrow'].includes(type)) {
      where.type = type
    }
    if (isRepaid !== undefined) {
      where.isRepaid = isRepaid === 'true' || isRepaid === true
    }

    const { count, rows } = await Debt.findAndCountAll({
      where,
      limit: pageSize,
      offset: offset,
      order: [['date', 'DESC']]
    })

    res.json({
      data: rows,
      total: count,
      page: pageNum,
      limit: pageSize
    })
  } catch (err) {
    console.error('获取债务记录失败:', err)
    res.status(500).json({ error: '读取数据失败' })
  }
}

// 添加新的债务记录
const addDebt = async (req, res) => {
  try {
    const { type, person, amount, date, isRepaid = false, remark } = req.body

    // 后端数据验证
    if (!type || !['lend', 'borrow'].includes(type)) {
      return res.status(400).json({ error: '请提供有效的债务类型（lend或borrow）' })
    }
    if (!person || !person.trim()) {
      return res.status(400).json({ error: '交易对方姓名是必填项' })
    }
    if (!amount || isNaN(amount) || parseFloat(amount) <= 0) {
      return res.status(400).json({ error: '请提供有效的金额' })
    }

    const newDebt = await Debt.create({
      type,
      person,
      amount: parseFloat(amount),
      date: date ? dayjs(date).toDate() : dayjs().toDate(),
      isRepaid,
      remark
    })

    res.status(201).json(newDebt)
  } catch (error) {
    console.error('添加债务记录失败:', error)
    res.status(500).json({ error: '无法添加记录' })
  }
}

// 更新债务记录
const updateDebt = async (req, res) => {
  try {
    const { id } = req.params
    const { type, person, amount, date, isRepaid, remark } = req.body

    const debt = await Debt.findByPk(id)
    if (!debt) {
      return res.status(404).json({ error: '未找到要更新的债务记录' })
    }

    // 构建更新对象
    const updateData = {}
    if (type !== undefined && ['lend', 'borrow'].includes(type)) {
      updateData.type = type
    }
    if (person !== undefined && person.trim()) {
      updateData.person = person
    }
    if (amount !== undefined && !isNaN(amount) && parseFloat(amount) > 0) {
      updateData.amount = parseFloat(amount)
    }
    if (date !== undefined) {
      updateData.date = dayjs(date).toDate()
    }
    if (isRepaid !== undefined) {
      updateData.isRepaid = isRepaid
    }
    if (remark !== undefined) {
      updateData.remark = remark
    }

    await debt.update(updateData)
    res.json(debt)
  } catch (error) {
    console.error('更新债务记录失败:', error)
    res.status(500).json({ error: '无法更新记录' })
  }
}

// 删除债务记录
const deleteDebt = async (req, res) => {
  try {
    const { id } = req.params
    const deleted = await Debt.destroy({
      where: { id: id }
    })

    if (deleted) {
      res.status(204).send()
    } else {
      res.status(404).json({ error: '未找到要删除的债务记录' })
    }
  } catch (error) {
    console.error('删除债务记录失败:', error)
    res.status(500).json({ error: '无法删除记录' })
  }
}

module.exports = {
  getDebts,
  addDebt,
  updateDebt,
  deleteDebt
}
const express = require('express')
const router = express.Router()
const { getDebts, addDebt, updateDebt, deleteDebt } = require('../controllers/debtController')

// 获取所有债务记录（支持分页和筛选）
router.get('/', getDebts)

// 添加新的债务记录
router.post('/', addDebt)

// 更新指定的债务记录
router.put('/:id', updateDebt)

// 删除指定的债务记录
router.delete('/:id', deleteDebt)

module.exports = router
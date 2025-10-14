const express = require('express')
const router = express.Router()
const { getExpenses, addExpense, deleteExpense, getStatistics } = require('../controllers/expenseController')

router.get('/', getExpenses)
router.post('/', addExpense)
router.delete('/:id', deleteExpense)
router.get('/statistics', getStatistics) // 添加统计API路由

module.exports = router

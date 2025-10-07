const express = require('express');
const router = express.Router();
const miniappController = require('../controllers/miniappController');

/**
 * 小程序API路由
 * 用于管理和访问家庭记账本应用中的小程序功能
 */

// 获取小程序列表
router.get('/list', miniappController.getMiniAppList);

module.exports = router;
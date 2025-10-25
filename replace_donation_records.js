const fs = require('fs');
const path = require('path');

function replaceDonationRecords() {
    const originalFilePath = path.join(__dirname, 'server', 'data', 'donation_records.json');
    const filteredFilePath = path.join(__dirname, 'server', 'data', 'filtered_donation_records.json');
    
    try {
        console.log('开始替换捐款记录文件...');
        
        // 检查文件是否存在
        if (!fs.existsSync(filteredFilePath)) {
            console.error('错误: 过滤后的文件不存在!');
            return;
        }
        
        // 读取过滤后的文件内容进行确认
        const filteredData = fs.readFileSync(filteredFilePath, 'utf8');
        const records = filteredData.trim().split('\n').map(line => {
            try {
                return JSON.parse(line);
            } catch (e) {
                console.error(`解析JSON失败: ${line}`);
                return null;
            }
        }).filter(record => record !== null);
        
        console.log(`过滤后的记录数: ${records.length}`);
        
        // 计算过滤后的总金额
        const filteredTotal = records.reduce((sum, record) => sum + parseFloat(record.amount), 0);
        console.log(`过滤后的总金额: ${filteredTotal.toFixed(2)}`);
        
        // 替换原始文件
        fs.copyFileSync(filteredFilePath, originalFilePath);
        
        console.log(`✓ 成功替换原始文件: ${originalFilePath}`);
        console.log('操作完成! 所有测试用户(admin, 123, chronie, administrator)的捐款记录已被移除。');
        
    } catch (error) {
        console.error('替换文件时出错:', error);
    }
}

// 执行替换
replaceDonationRecords();
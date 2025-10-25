const fs = require('fs');
const path = require('path');

// 要移除的测试用户列表
const testUsers = ['admin', '123', 'chronie', 'administrator'];

function filterDonationRecords() {
    const inputFilePath = path.join(__dirname, 'server', 'data', 'donation_records.json');
    const outputFilePath = path.join(__dirname, 'server', 'data', 'filtered_donation_records.json');
    
    try {
        console.log('开始过滤捐款记录...');
        console.log(`将移除以下测试用户的记录: ${testUsers.join(', ')}`);
        
        // 读取原始文件内容
        const data = fs.readFileSync(inputFilePath, 'utf8');
        
        // 解析每行的JSON记录
        const records = data.trim().split('\n').map(line => {
            try {
                return JSON.parse(line);
            } catch (e) {
                console.error(`解析JSON失败: ${line}`, e);
                return null;
            }
        }).filter(record => record !== null);
        
        console.log(`原始记录总数: ${records.length}`);
        
        // 过滤掉测试用户的记录
        const filteredRecords = records.filter(record => !testUsers.includes(record.username));
        
        // 统计移除的记录数
        const removedCount = records.length - filteredRecords.length;
        console.log(`移除的记录数: ${removedCount}`);
        console.log(`剩余的记录数: ${filteredRecords.length}`);
        
        // 计算原始总金额和过滤后的总金额
        const originalTotal = records.reduce((sum, record) => sum + parseFloat(record.amount), 0);
        const filteredTotal = filteredRecords.reduce((sum, record) => sum + parseFloat(record.amount), 0);
        
        console.log(`原始总金额: ${originalTotal.toFixed(2)}`);
        console.log(`过滤后总金额: ${filteredTotal.toFixed(2)}`);
        console.log(`移除的金额: ${(originalTotal - filteredTotal).toFixed(2)}`);
        
        // 按用户统计剩余的主要贡献者
        const userStats = {};
        filteredRecords.forEach(record => {
            const username = record.username;
            if (!userStats[username]) {
                userStats[username] = { amount: 0, count: 0 };
            }
            userStats[username].amount += parseFloat(record.amount);
            userStats[username].count += 1;
        });
        
        console.log('\n剩余的主要贡献者:');
        Object.entries(userStats)
            .sort((a, b) => b[1].amount - a[1].amount)
            .forEach(([username, stats]) => {
                console.log(`${username}: 金额 ${stats.amount.toFixed(2)}, 贡献次数 ${stats.count}`);
            });
        
        // 将过滤后的记录写入新文件
        const outputData = filteredRecords.map(record => JSON.stringify(record)).join('\n');
        fs.writeFileSync(outputFilePath, outputData, 'utf8');
        
        console.log(`\n过滤后的记录已保存到: ${outputFilePath}`);
        
        // 可选：备份原始文件
        const backupFilePath = path.join(__dirname, 'server', 'data', 'donation_records_backup.json');
        fs.copyFileSync(inputFilePath, backupFilePath);
        console.log(`原始记录已备份到: ${backupFilePath}`);
        
        // 可选：替换原始文件
        // fs.writeFileSync(inputFilePath, outputData, 'utf8');
        // console.log(`已替换原始文件: ${inputFilePath}`);
        
    } catch (error) {
        console.error('处理捐款记录时出错:', error);
    }
}

// 执行过滤
filterDonationRecords();
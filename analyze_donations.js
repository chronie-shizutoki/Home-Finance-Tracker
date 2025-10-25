const fs = require('fs');
const path = require('path');

// 读取捐款记录文件
function analyzeDonationRecords() {
    const filePath = path.join(__dirname, 'server', 'data', 'donation_records.json');
    
    try {
        // 读取文件内容
        const data = fs.readFileSync(filePath, 'utf8');
        
        // 解析每行的JSON记录
        const records = data.trim().split('\n').map(line => {
            try {
                return JSON.parse(line);
            } catch (e) {
                console.error(`解析JSON失败: ${line}`, e);
                return null;
            }
        }).filter(record => record !== null);
        
        console.log(`总共记录数: ${records.length}\n`);
        
        // 按时间排序（最新的在前）
        const sortedRecords = [...records].sort((a, b) => 
            new Date(b.timestamp) - new Date(a.timestamp)
        );
        
        // 统计总金额
        const totalAmount = records.reduce((sum, record) => sum + parseFloat(record.amount), 0);
        console.log(`总收益金额: ${totalAmount.toFixed(2)}`);
        
        // 统计近期（比如最近7天）的收益
        const now = new Date();
        const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        
        const recentRecords = records.filter(record => 
            new Date(record.timestamp) >= sevenDaysAgo
        );
        
        const recentAmount = recentRecords.reduce((sum, record) => sum + parseFloat(record.amount), 0);
        console.log(`最近7天收益: ${recentAmount.toFixed(2)}`);
        console.log(`最近7天交易笔数: ${recentRecords.length}\n`);
        
        // 按月统计
        const monthlyStats = {};
        records.forEach(record => {
            const month = record.timestamp.substring(0, 7); // 格式：YYYY-MM
            if (!monthlyStats[month]) {
                monthlyStats[month] = { amount: 0, count: 0 };
            }
            monthlyStats[month].amount += parseFloat(record.amount);
            monthlyStats[month].count += 1;
        });
        
        console.log('按月统计:');
        Object.keys(monthlyStats).sort().forEach(month => {
            console.log(`${month}: 金额 ${monthlyStats[month].amount.toFixed(2)}, 笔数 ${monthlyStats[month].count}`);
        });
        
        // 按用户统计Top 10贡献者
        const userStats = {};
        records.forEach(record => {
            const username = record.username;
            if (!userStats[username]) {
                userStats[username] = { amount: 0, count: 0 };
            }
            userStats[username].amount += parseFloat(record.amount);
            userStats[username].count += 1;
        });
        
        console.log('\nTop 10贡献者:');
        Object.entries(userStats)
            .sort((a, b) => b[1].amount - a[1].amount)
            .slice(0, 10)
            .forEach(([username, stats]) => {
                console.log(`${username}: 金额 ${stats.amount.toFixed(2)}, 贡献次数 ${stats.count}`);
            });
        
        // 查看最近的10笔交易
        console.log('\n最近的10笔交易:');
        sortedRecords.slice(0, 10).forEach(record => {
            console.log(`${record.timestamp} - ${record.username}: ${record.amount}, 状态: ${record.status}`);
        });
        
    } catch (error) {
        console.error('读取或分析文件时出错:', error);
    }
}

// 执行分析
analyzeDonationRecords();
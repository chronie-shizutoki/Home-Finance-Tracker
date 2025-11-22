const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// 数据库文件路径
const dbPath = path.join(__dirname, '../database.sqlite');

// 创建数据库连接
const db = new sqlite3.Database(dbPath);

db.serialize(() => {
  console.log('开始数据库迁移...');
  
  // 1. 创建临时表，使用新的结构
  console.log('创建临时表...');
  db.run(`
    CREATE TABLE IF NOT EXISTS expenses_temp (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      type TEXT NOT NULL,
      remark TEXT,
      amount REAL NOT NULL,
      date TEXT NOT NULL
    );
  `, (err) => {
    if (err) {
      console.error('创建临时表失败:', err.message);
      db.close();
      return;
    }
    
    // 2. 迁移数据，对time列进行处理
    console.log('开始迁移数据...');
    db.run(`
      INSERT INTO expenses_temp (id, type, remark, amount, date)
      SELECT 
        id,
        type,
        remark,
        amount,
        date(strftime('%Y-%m-%d', datetime(time, '+9 hours'))) AS date
      FROM expenses;
    `, function(err) {
      if (err) {
        console.error('迁移数据失败:', err.message);
        db.close();
        return;
      }
      console.log(`成功迁移 ${this.changes} 条记录`);
      
      // 3. 删除旧表
      console.log('删除旧表...');
      db.run('DROP TABLE expenses;', (err) => {
        if (err) {
          console.error('删除旧表失败:', err.message);
          db.close();
          return;
        }
        
        // 4. 重命名临时表
        console.log('重命名临时表...');
        db.run('ALTER TABLE expenses_temp RENAME TO expenses;', (err) => {
          if (err) {
            console.error('重命名临时表失败:', err.message);
            db.close();
            return;
          }
          
          // 5. 验证结果
          console.log('验证迁移结果...');
          db.get('PRAGMA table_info(expenses);', (err, row) => {
            if (err) {
              console.error('验证表结构失败:', err.message);
            } else {
              console.log('表结构验证成功!');
              // 显示表中的列信息
              db.all('PRAGMA table_info(expenses);', (err, columns) => {
                if (err) {
                  console.error('获取列信息失败:', err.message);
                } else {
                  console.log('当前表结构:');
                  columns.forEach(col => {
                    console.log(`- ${col.name}: ${col.type}${col.notnull ? ' NOT NULL' : ''}${col.pk ? ' PRIMARY KEY' : ''}`);
                  });
                }
                console.log('数据库迁移完成!');
                db.close();
              });
            }
          });
        });
      });
    });
  });
});

// 捕获未处理的错误
db.on('error', (err) => {
  console.error('数据库错误:', err.message);
});
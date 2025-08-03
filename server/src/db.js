const Sequelize = require('sequelize')
const path = require('path')
const sequelize = new Sequelize({ dialect: 'sqlite', storage: path.join(__dirname, '../database.sqlite') })

const Expense = require('./models/expense')(sequelize)
const Todo = require('./models/todo')(sequelize)

const syncDatabase = async () => {
  try {
    await sequelize.sync({ force: false })
    console.log('Database synchronized successfully.')
  } catch (error) {
    console.error('Unable to synchronize the database:', error)
    throw error
  }
}

module.exports = {
  sequelize,
  Expense,
  Todo,
  syncDatabase
}

const path = require('path')
const http = require('http')
const express = require('express')
const fs = require('fs')
console.log('Top-level NODE_ENV:', process.env.NODE_ENV);
const { syncDatabase } = require('./db')
const cors = require('cors')
require('dotenv').config()

const app = express()
const PORT = process.env.PORT || 3010

// Middlewares
const corsOptions = {
  origin: ['http://localhost:5173', 'http://192.168.0.197:5173' ,'http://192.168.0.197:3200' ,'http://192.168.0.197:8000'],
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true,
  optionsSuccessStatus: 200
}

app.use(cors(corsOptions))
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

// API Routes
app.use('/api/expenses', require('./routes/expense'))
app.use('/api/todos', require('./routes/todos'))
app.use('/api/json-files', require('./routes/jsonFiles'))
app.use('/api/payments', require('./routes/payment'))
app.use('/api/debts', require('./routes/debt'))
app.use('/api/miniapp', require('./routes/miniapp'))
app.use('/api', require('./routes/api'))

// 根路径路由
app.use('/', require('./routes/baseRoutes'))
app.use('/', require('./routes/exportRoutes'))

// Serve frontend in production
console.log('NODE_ENV:', process.env.NODE_ENV);
if (process.env.NODE_ENV === 'production') {
  const distPath = path.join(__dirname, '../../client/dist');
  console.log('Serving static files from:', distPath);
    // 写入调试信息到文件
    const debugInfo = `NODE_ENV: ${process.env.NODE_ENV}\nDist Path: ${distPath}\nPath exists: ${fs.existsSync(distPath)}\n`;
    fs.writeFileSync('debug.log', debugInfo);
    app.use(express.static(distPath))

  // Handle SPA routing - 必须放在最后
  app.get(/.*/, (req, res) => {
    res.sendFile(path.join(distPath, 'index.html'))
  })
}

// Global error handler
app.use((err, req, res, next) => {
  console.error('An error occurred:', err.stack)
  res.status(err.status || 500).json({
    success: false,
    error: 'Internal Server Error'
  })
})

const startServer = async () => {
  try {
    await syncDatabase()
    const server = http.createServer(app)
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`✅ Server is running on port ${PORT}`)
    })
  } catch (error) {
    console.error('❌ Failed to start server:', error)
    process.exit(1)
  }
}

startServer()


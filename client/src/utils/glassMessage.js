import { createApp } from 'vue'
import GlassMessage from '../components/GlassMessage.vue'

// Message Container
let container = null

// Create message container
const createContainer = () => {
  if (container) return container
  
  container = document.createElement('div')
  container.className = 'glass-message-container'
  document.body.appendChild(container)
  
  return container
}

// Create message instance
const createMessage = (options) => {
  // Ensure container exists
  const messageContainer = createContainer()
  
  // Process options
  const messageOptions = typeof options === 'string' ? { message: options } : options
  
  // Create Vue app instance
  const app = createApp(GlassMessage, {
    ...messageOptions,
    onClose: () => {
      // Destroy app on close
      app.unmount(messageElement)
      messageElement.remove()
      
      // Remove container if empty after close
      if (messageContainer.children.length === 0) {
        messageContainer.remove()
        container = null
      }
      
      // Call user-provided onClose callback
      if (messageOptions.onClose) {
        messageOptions.onClose()
      }
    }
  })
  
  // Mount app
  const messageElement = document.createElement('div')
  messageContainer.appendChild(messageElement)
  app.mount(messageElement)
  
  return {
    close: () => {
      app.unmount(messageElement)
      messageElement.remove()
    }
  }
}

// Create message methods for different types of messages
const messageMethods = {
  success(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'success'
    })
  },
  warning(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'warning'
    })
  },
  error(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'error'
    })
  },
  info(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'info'
    })
  },
  closeAll() {
    if (container) {
      container.innerHTML = ''
      container.remove()
      container = null
    }
  }
}

export default messageMethods

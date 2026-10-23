const { sendChatMessage } = require('../../api/ai')
const { createClientId } = require('../../utils/id')

const suggestions = [
  '100 元以内有什么肩颈护理？',
  '周末下午有哪些技师有空？',
  '帮我查一下最近可以预约的时间'
]

Page({
  data: {
    messages: [],
    suggestions,
    inputValue: '',
    conversationId: '',
    sending: false,
    scrollIntoView: '',
    retryContent: '',
    contextPrompt: ''
  },

  onLoad(options) {
    const conversationId = wx.getStorageSync('aiConversationId') || createClientId('conversation')
    wx.setStorageSync('aiConversationId', conversationId)

    const serviceName = options.serviceName ? decodeURIComponent(options.serviceName) : ''
    const contextText = serviceName
      ? `你正在查看“${serviceName}”，可以直接询问该服务的技师和可预约时间。`
      : '告诉我你的护理偏好、预算和时间，我会查询真实服务、技师与可预约时段。'
    const contextPrompt = serviceName
      ? `当前页面服务：${serviceName}（服务ID：${options.serviceId || '未知'}）。`
      : ''

    this.setData({
      conversationId,
      contextPrompt,
      messages: [{ id: 'welcome', role: 'assistant', content: `你好，我是栖心智能预约顾问。${contextText}` }]
    })
  },

  handleInput(event) {
    this.setData({ inputValue: event.detail.value })
  },

  useSuggestion(event) {
    this.setData({ inputValue: event.currentTarget.dataset.text })
    this.sendMessage()
  },

  sendMessage() {
    const content = this.data.inputValue.trim()
    if (!content || this.data.sending) return

    const userMessage = { id: createClientId('user'), role: 'user', content }
    this.setData({
      messages: [...this.data.messages, userMessage],
      inputValue: '',
      sending: true,
      retryContent: ''
    })
    this.scrollToBottom()
    this.requestAnswer(content)
  },

  async requestAnswer(content) {
    try {
      const answer = await sendChatMessage({
        message: this.data.contextPrompt ? `${this.data.contextPrompt}\n用户问题：${content}` : content,
        conversationId: this.data.conversationId,
        requestId: createClientId('ai-request')
      })
      const assistantMessage = { id: createClientId('assistant'), role: 'assistant', content: answer }
      this.setData({ messages: [...this.data.messages, assistantMessage] })
    } catch (error) {
      const errorMessage = {
        id: createClientId('error'),
        role: 'error',
        content: error.message || '智能服务暂时不可用，请稍后再试。'
      }
      this.setData({ messages: [...this.data.messages, errorMessage], retryContent: content })
    } finally {
      this.setData({ sending: false })
      this.scrollToBottom()
    }
  },

  retryLast() {
    if (!this.data.retryContent || this.data.sending) return
    const messages = this.data.messages.filter((item) => item.role !== 'error')
    const content = this.data.retryContent
    this.setData({ messages, retryContent: '', sending: true })
    this.requestAnswer(content)
  },

  newConversation() {
    wx.showModal({
      title: '开始新对话？',
      content: '当前页面消息会被清空，并创建新的会话上下文。',
      success: (result) => {
        if (!result.confirm) return
        const conversationId = createClientId('conversation')
        wx.setStorageSync('aiConversationId', conversationId)
        this.setData({
          conversationId,
          messages: [{ id: 'welcome-new', role: 'assistant', content: '新的对话已开始。告诉我你想预约的服务、日期或预算吧。' }],
          retryContent: ''
        })
      }
    })
  },

  scrollToBottom() {
    this.setData({ scrollIntoView: '' })
    setTimeout(() => this.setData({ scrollIntoView: 'message-bottom' }), 30)
  }
})

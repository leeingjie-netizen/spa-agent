const { requireLogin } = require('../../utils/session')
const { sendChatMessage } = require('../../api/ai')
const { createClientId } = require('../../utils/id')
const { renderMarkdown } = require('../../utils/markdown')
const assistant = (id, content) => ({ id, role: 'assistant', content, nodes: renderMarkdown(content) })

const suggestions = [
  '100 元以内有什么肩颈护理？',
  '周末下午有哪些技师有空？',
  '帮我查一下最近可以预约的时间'
]

Page({
  data: {
    messages: [],
    keyboardHeight: 0,
    suggestions,
    inputValue: '',
    hasInput: false,
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
      messages: [assistant('welcome', `你好，我是栖心智能预约顾问。${contextText}`)]
    })
  },

  handleInput(event) {
    this.setData({ inputValue: event.detail.value, hasInput: !!event.detail.value.trim() })
  },

  useSuggestion(event) {
    this.setData({ inputValue: event.currentTarget.dataset.text, hasInput: true })
    this.sendMessage()
  },

  keyboardChanged(event) { this.setData({ keyboardHeight: event.detail.height || 0 }); this.scrollToBottom() },

  sendMessage() {
    if (!requireLogin()) return
    const content = this.data.inputValue.trim()
    if (!content || this.data.sending) return

    const userMessage = { id: createClientId('user'), role: 'user', content }
    this.setData({
      messages: [...this.data.messages, userMessage],
      inputValue: '',
      hasInput: false,
      sending: true,
      retryContent: ''
    })
    this.scrollToBottom()
    this.requestAnswer(content)
  },

  async requestAnswer(content) {
    try {
      const answer = await sendChatMessage({
        message: this.data.contextPrompt && !/^确认(预约|取消)[。！!]?$/.test(content) ? `${this.data.contextPrompt}\n用户问题：${content}` : content,
        conversationId: this.data.conversationId,
        requestId: createClientId('ai-request')
      })
      if (this._unloaded) return
      const text = typeof answer === 'string' ? answer : answer && answer.message
      if (!text) throw new Error('智能顾问未返回有效回复，请稍后重试')
      if (typeof answer !== 'string' || !answer.trim()) throw new Error('智能顾问暂未返回内容，请稍后重试')
      const assistantMessage = assistant(createClientId('assistant'), text)
      this.setData({ messages: [...this.data.messages, assistantMessage] })
    } catch (error) {
      if (this._unloaded) return
      const errorMessage = {
        id: createClientId('error'),
        role: 'error',
        content: error.message || '智能服务暂时不可用，请稍后再试。'
      }
      this.setData({ messages: [...this.data.messages, errorMessage], retryContent: content })
    } finally {
      if (this._unloaded) return
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

  onUnload() { this._unloaded = true; clearTimeout(this._scrollTimer) },

  newConversation() {
    if (this.data.sending) return
    wx.showModal({
      title: '开始新对话？',
      content: '当前页面消息会被清空，并创建新的会话上下文。',
      success: (result) => {
        if (!result.confirm) return
        const conversationId = createClientId('conversation')
        wx.setStorageSync('aiConversationId', conversationId)
        this.setData({
          conversationId,
          messages: [assistant('welcome-new', '新的对话已开始。告诉我你想预约的服务、日期或预算吧。')],
          retryContent: ''
        })
      }
    })
  },

  scrollToBottom() {
    this.setData({ scrollIntoView: '' })
    clearTimeout(this._scrollTimer)
    this._scrollTimer = setTimeout(() => this.setData({ scrollIntoView: 'message-bottom' }), 30)
  }
})

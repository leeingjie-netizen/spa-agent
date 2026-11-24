const environments = {
  development: {
    name: '本地开发',
    baseUrl: 'http://127.0.0.1:18080',
    devLoginEnabled: true
  },
  production: {
    name: '正式环境',
    baseUrl: 'https://api.example.com',
    devLoginEnabled: false
  },
  staging: {
    name: '预发布环境',
    baseUrl: 'https://staging-api.example.com',
    devLoginEnabled: false
  }
}

const currentEnvironment = 'development'

function getRuntimeConfig() {
  let selected = currentEnvironment
  if (typeof wx !== 'undefined' && typeof wx.getAccountInfoSync === 'function') {
    const version = wx.getAccountInfoSync().miniProgram.envVersion
    selected = ({ develop: 'development', trial: 'staging', release: 'production' })[version] || 'production'
  }
  return {
    environment: selected,
    ...environments[selected]
  }
}

module.exports = {
  getRuntimeConfig
}

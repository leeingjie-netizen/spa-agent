const environments = {
  development: {
    name: '本地开发',
    baseUrl: 'http://127.0.0.1:18080',
    devLoginEnabled: true
  },
  production: {
    name: '正式环境',
    baseUrl: 'https://api.example.com'
  }
}

const currentEnvironment = 'development'

function getRuntimeConfig() {
  return {
    environment: currentEnvironment,
    ...environments[currentEnvironment]
  }
}

module.exports = {
  getRuntimeConfig
}

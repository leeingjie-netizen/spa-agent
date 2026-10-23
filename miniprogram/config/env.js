const environments = {
  development: {
    name: '本地开发',
    baseUrl: 'http://127.0.0.1:8080',
    demoUserId: '1'
  },
  production: {
    name: '正式环境',
    baseUrl: 'https://api.example.com',
    demoUserId: ''
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

const { test } = require('node:test')
const assert = require('node:assert/strict')
const { getRuntimeConfig } = require('../miniprogram/config/env')
for (const [version, expected] of [['develop','development'], ['trial','staging'], ['release','production'], ['unknown','production']]) {
  test(`mini program environment ${version}`, () => {
    global.wx = { getAccountInfoSync: () => ({ miniProgram: { envVersion: version } }) }
    try {
      const config = getRuntimeConfig()
      assert.equal(config.environment, expected)
      assert.equal(config.devLoginEnabled, expected === 'development')
      if (expected !== 'development') assert.match(config.baseUrl, /^https:\/\//)
    } finally { delete global.wx }
  })
}

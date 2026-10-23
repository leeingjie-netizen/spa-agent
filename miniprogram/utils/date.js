const weekNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function buildDateOptions(days) {
  const today = new Date()
  const result = []

  for (let index = 0; index < days; index += 1) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + index)
    result.push({
      value: formatDate(date),
      monthDay: `${pad(date.getMonth() + 1)}.${pad(date.getDate())}`,
      week: index === 0 ? '今天' : weekNames[date.getDay()]
    })
  }

  return result
}

function trimTime(value) {
  return value ? String(value).slice(0, 5) : ''
}

module.exports = {
  buildDateOptions,
  formatDate,
  trimTime
}

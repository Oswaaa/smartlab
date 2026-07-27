const BUILT_INS = new Set(['true', 'false', 'delta', 'avg', 'rate'])
const IDENTIFIER_START = /[A-Za-z_]/
const IDENTIFIER_PART = /[A-Za-z0-9_]/

export function extractExpressionVariables(expression) {
  const result = []
  for (const match of String(expression || '').matchAll(/@([A-Za-z_][A-Za-z0-9_]*)/g)) {
    if (!result.includes(match[1])) result.push(match[1])
  }
  return result
}

export function toBackendExpression(expression) {
  return String(expression || '').replace(/@([A-Za-z_][A-Za-z0-9_]*)/g, '$1').trim()
}

export function validateDisplayExpressionSyntax(expression) {
  const source = String(expression || '')
  let index = 0
  while (index < source.length) {
    const character = source[index]
    if (character === "'" || character === '"') {
      const quote = character
      index++
      while (index < source.length && source[index] !== quote) {
        if (source[index] === '\\' && index + 1 < source.length) index += 2
        else index++
      }
      if (index >= source.length) throw new Error('表达式字符串缺少结束引号')
      index++
      continue
    }
    if (character === '@') {
      if (!IDENTIFIER_START.test(source[index + 1] || '')) throw new Error('@后必须填写合法变量名')
      index += 2
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      continue
    }
    if (IDENTIFIER_START.test(character)) {
      const start = index++
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      const identifier = source.slice(start, index)
      if (!BUILT_INS.has(identifier.toLowerCase())) {
        throw new Error(`标识符${identifier}不是@变量；变量必须使用@引用，字符串常量必须使用引号`)
      }
      continue
    }
    index++
  }
}

// Model content is emitted only as text nodes, never as untrusted HTML or URLs.
function inline(text) {
 const nodes = [], pattern = /(\*\*([^*\n]+)\*\*|`([^`\n]+)`)/g
 let at = 0, match
 while ((match = pattern.exec(text))) {
  if (match.index > at) nodes.push({ type: 'text', text: text.slice(at, match.index) })
  nodes.push({ name: match[2] ? 'strong' : 'span', attrs: { style: match[2] ? 'font-weight:600;color:inherit;' : 'background:rgba(49,92,76,.07);padding:1px 4px;border-radius:4px;' }, children: [{ type: 'text', text: match[2] || match[3] }] })
  at = pattern.lastIndex
 }
 if (at < text.length) nodes.push({ type: 'text', text: text.slice(at) })
 return nodes
}
function renderMarkdown(content) {
 return String(content || '').replace(/\r\n?/g, '\n').split('\n').filter((line,index,lines)=>line.trim() || !index || lines[index-1].trim()).map(line => {
  if (!line.trim()) return {name:'div',attrs:{style:'height:8px;'},children:[]}
  const heading = line.match(/^#{1,4}\s+(.+)$/), bullet = line.match(/^\s*[-*+]\s+(.+)$/), ordered = line.match(/^\s*(\d+)[.)、]\s+(.+)$/)
  const text = heading ? heading[1] : bullet ? `• ${bullet[1]}` : ordered ? `${ordered[1]}. ${ordered[2]}` : line
  return { name: 'div', attrs: { style: `margin-bottom:4px;${heading ? 'font-weight:600;' : ''}word-break:break-word;overflow-wrap:anywhere;` }, children: inline(text) }
 })
}
module.exports = { renderMarkdown }

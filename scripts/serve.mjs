import { createServer } from 'node:http'
import { statSync, readFileSync } from 'node:fs'
import { join, resolve, extname } from 'node:path'

const ROOT = resolve(process.argv[2] || 'dist')
const PORT = Number(process.env.PORT || 3000)
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.wasm': 'application/wasm'
}

createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost')
  let path = resolve(ROOT, '.' + decodeURIComponent(url.pathname))
  if (path !== ROOT && !path.startsWith(ROOT + '/')) { res.writeHead(403); res.end(); return }
  try {
    if (statSync(path).isDirectory()) path = join(path, 'index.html')
    res.writeHead(200, {
      'Content-Type': mime[extname(path).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-cache'
    })
    res.end(readFileSync(path))
  } catch {
    res.writeHead(404)
    res.end('Not found')
  }
}).listen(PORT, '0.0.0.0', () => console.log(`serving ${ROOT} on http://0.0.0.0:${PORT}`))

// Local visual preview with a simulated Electron bridge; never shipped.
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const bridge = `<script>
const previewState = { minecraftVersion: '1.21.1', configured: true, installed: false, signedIn: false, checkedForSession: false, accountName: '' };
window.launcher = {
status: async () => ({ ...previewState }),
signIn: async () => { previewState.signedIn = true; previewState.accountName = 'Jugador'; return { message: 'Cuenta conectada para la prueba visual.' }; },
install: async () => { previewState.installed = true; previewState.checkedForSession = true; return { message: 'Modpack actualizado para la prueba visual.' }; },
play: async () => ({ ready: true, message: 'Inicio de prueba solicitado.' }),
openFolder: async () => ({ message: 'Carpeta de prueba.' })
};</script>`;
http.createServer(async (req, res) => {
  const file = { '/': 'index.html', '/style.css': 'style.css', '/renderer.js': 'renderer.js' }[req.url];
  if (!file) { res.writeHead(404).end(); return; }
  let content = await fs.readFile(path.join(__dirname, '../src', file), 'utf8');
  if (file === 'index.html') content = content.replace('<script src="renderer.js">', bridge + '<script src="renderer.js">');
  res.setHeader('Content-Type', file.endsWith('.css') ? 'text/css' : file.endsWith('.js') ? 'application/javascript' : 'text/html; charset=utf-8');
  res.end(content);
}).listen(4178, '127.0.0.1', () => console.log('Preview: http://127.0.0.1:4178'));

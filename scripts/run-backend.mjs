import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const jar = join(root, 'backend/target/developer-portfolio-0.2.0.jar')
if (!existsSync(jar)) throw new Error('Run backend Maven package before starting the API')
const port = Number(process.env.PORTFOLIO_PORT ?? 19186)
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid API port')
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java') : 'java'
const child = spawn(java, ['-Xmx256m', '-XX:+UseSerialGC', '-jar', jar,
  '--server.address=127.0.0.1', '--server.port=' + port], { cwd: root, stdio: 'inherit' })
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal))
child.on('error', error => { console.error(error.message); process.exitCode = 1 })
child.on('exit', (code, signal) => { process.exitCode = code ?? (signal ? 0 : 1) })

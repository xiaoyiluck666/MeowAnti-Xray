#!/usr/bin/env node
'use strict'

const crypto = require('crypto')
const net = require('net')
const zlib = require('zlib')

const PROTOCOL_VERSION = 775

const CLIENTBOUND = {
  LOGIN_SET_COMPRESSION: 0x03,
  LOGIN_SUCCESS: 0x02,
  LOGIN_COOKIE_REQUEST: 0x04,
  CONFIG_FINISH: 0x03,
  CONFIG_COOKIE_REQUEST: 0x04,
  CONFIG_PLUGIN_MESSAGE: 0x05,
  CONFIG_SELECT_KNOWN_PACKS: 0x0e,
  CONFIG_PING: 0x13,
  PLAY_CHUNK_BATCH_START: 0x0c,
  PLAY_CHUNK_BATCH_FINISHED: 0x0b,
  PLAY_KEEP_ALIVE: 0x2c,
  PLAY_LEVEL_CHUNK_WITH_LIGHT: 0x2d,
  PLAY_PING: 0x3d,
  PLAY_PLAYER_POSITION: 0x48
}

const SERVERBOUND = {
  LOGIN_ACKNOWLEDGED: 0x03,
  LOGIN_COOKIE_RESPONSE: 0x02,
  CONFIG_CLIENT_INFORMATION: 0x00,
  CONFIG_COOKIE_RESPONSE: 0x04,
  CONFIG_PLUGIN_MESSAGE: 0x05,
  CONFIG_SELECT_KNOWN_PACKS: 0x07,
  CONFIG_PONG: 0x09,
  CONFIG_FINISH_ACKNOWLEDGED: 0x03,
  PLAY_ACCEPT_TELEPORTATION: 0x00,
  PLAY_CHUNK_BATCH_RECEIVED: 0x0b,
  PLAY_TICK_END: 0x0d,
  PLAY_KEEP_ALIVE: 0x1c,
  PLAY_POSITION_LOOK: 0x1f,
  PLAY_PONG: 0x2c,
  PLAY_PLAYER_LOADED: 0x2c
}

function parseArgs(argv) {
  const args = {
    host: '127.0.0.1',
    port: 25565,
    clients: 1,
    duration: 60,
    name: 'LoadBot',
    startX: -34.5,
    startY: 73,
    startZ: 29.5,
    spacing: 48,
    speed: 3,
    moveInterval: 250,
    connectDelay: 500,
    viewDistanceHint: 12,
    rconTeleport: false,
    rconHost: null,
    rconPort: 25575,
    rconPassword: '',
    gamemode: '',
    teleportInterval: 2500,
    teleportStep: 192,
    teleportY: null,
    verbose: false
  }

  for (let i = 2; i < argv.length; i++) {
    const key = argv[i]
    const next = argv[i + 1]
    switch (key) {
      case '--host':
        args.host = next
        i++
        break
      case '--port':
        args.port = Number(next)
        i++
        break
      case '--clients':
        args.clients = Number(next)
        i++
        break
      case '--duration':
        args.duration = Number(next)
        i++
        break
      case '--name':
        args.name = next
        i++
        break
      case '--start-x':
        args.startX = Number(next)
        i++
        break
      case '--start-y':
        args.startY = Number(next)
        i++
        break
      case '--start-z':
        args.startZ = Number(next)
        i++
        break
      case '--spacing':
        args.spacing = Number(next)
        i++
        break
      case '--speed':
        args.speed = Number(next)
        i++
        break
      case '--move-interval':
        args.moveInterval = Number(next)
        i++
        break
      case '--connect-delay':
        args.connectDelay = Number(next)
        i++
        break
      case '--view-distance-hint':
        args.viewDistanceHint = Number(next)
        i++
        break
      case '--rcon-teleport':
        args.rconTeleport = true
        break
      case '--rcon-host':
        args.rconHost = next
        i++
        break
      case '--rcon-port':
        args.rconPort = Number(next)
        i++
        break
      case '--rcon-password':
        args.rconPassword = next
        i++
        break
      case '--gamemode':
        args.gamemode = next
        i++
        break
      case '--teleport-interval':
        args.teleportInterval = Number(next)
        i++
        break
      case '--teleport-step':
        args.teleportStep = Number(next)
        i++
        break
      case '--teleport-y':
        args.teleportY = Number(next)
        i++
        break
      case '--verbose':
        args.verbose = true
        break
      case '--help':
      case '-h':
        printHelp()
        process.exit(0)
        break
      default:
        throw new Error(`Unknown argument: ${key}`)
    }
  }

  if (!Number.isInteger(args.port) || args.port < 1 || args.port > 65535) throw new Error('Invalid --port')
  if (!Number.isInteger(args.clients) || args.clients < 1 || args.clients > 200) throw new Error('Invalid --clients')
  if (!Number.isFinite(args.duration) || args.duration <= 0) throw new Error('Invalid --duration')
  if (!Number.isFinite(args.startX) || !Number.isFinite(args.startY) || !Number.isFinite(args.startZ)) throw new Error('Invalid start position')
  if (!Number.isFinite(args.speed) || args.speed <= 0) throw new Error('Invalid --speed')
  if (!Number.isInteger(args.moveInterval) || args.moveInterval < 50) throw new Error('Invalid --move-interval')
  if (args.rconTeleport && !args.rconPassword) throw new Error('--rcon-teleport requires --rcon-password')
  if (args.gamemode && !['survival', 'creative', 'adventure', 'spectator'].includes(args.gamemode)) throw new Error('Invalid --gamemode')
  if (!Number.isInteger(args.rconPort) || args.rconPort < 1 || args.rconPort > 65535) throw new Error('Invalid --rcon-port')
  if (!Number.isInteger(args.teleportInterval) || args.teleportInterval < 250) throw new Error('Invalid --teleport-interval')
  if (!Number.isFinite(args.teleportStep) || args.teleportStep <= 0) throw new Error('Invalid --teleport-step')
  if (args.teleportY !== null && !Number.isFinite(args.teleportY)) throw new Error('Invalid --teleport-y')
  if (!args.rconHost) args.rconHost = args.host
  return args
}

function printHelp() {
  console.log(`Minecraft 26.1.2 network load runner

Usage:
  node tools/mc-2612-load-runner.js --clients 4 --duration 90

Options:
  --host <host>                 Server host, default 127.0.0.1
  --port <port>                 Server port, default 25565
  --clients <n>                 Number of fake network clients, default 1
  --duration <seconds>          Test duration after launch, default 60
  --name <prefix>               Username prefix, default LoadBot
  --start-x <number>            Initial movement anchor X, default -34.5
  --start-y <number>            Initial movement anchor Y, default 73
  --start-z <number>            Initial movement anchor Z, default 29.5
  --spacing <blocks>            Offset between clients, default 48
  --speed <blocks>              Blocks moved per movement packet, default 3
  --move-interval <ms>          Movement interval, default 250
  --connect-delay <ms>          Delay between client connects, default 500
  --rcon-teleport               Drive route by RCON /tp commands instead of movement packets
  --rcon-password <password>    RCON password, required by --rcon-teleport
  --rcon-host <host>            RCON host, default same as --host
  --rcon-port <port>            RCON port, default 25575
  --gamemode <mode>             Optional RCON gamemode before routing
  --teleport-interval <ms>      RCON teleport interval, default 2500
  --teleport-step <blocks>      RCON route step, default 192
  --teleport-y <number>         RCON teleport Y, default current spawn Y
  --verbose                     Log protocol noise
`)
}

function varInt(value) {
  let n = value >>> 0
  const bytes = []
  while (true) {
    if ((n & ~0x7f) === 0) {
      bytes.push(n)
      break
    }
    bytes.push((n & 0x7f) | 0x80)
    n >>>= 7
  }
  return Buffer.from(bytes)
}

function readVarInt(buffer, offset = 0) {
  let value = 0
  let shift = 0
  let cursor = offset
  while (true) {
    if (cursor >= buffer.length) return null
    const byte = buffer[cursor++]
    value |= (byte & 0x7f) << shift
    if ((byte & 0x80) === 0) return { value, size: cursor - offset }
    shift += 7
    if (shift > 35) throw new Error('VarInt too large')
  }
}

function mcString(value) {
  const body = Buffer.from(value, 'utf8')
  return Buffer.concat([varInt(body.length), body])
}

function bool(value) {
  return Buffer.from([value ? 1 : 0])
}

function int8(value) {
  const buffer = Buffer.alloc(1)
  buffer.writeInt8(value)
  return buffer
}

function uint8(value) {
  return Buffer.from([value & 0xff])
}

function uint16(value) {
  const buffer = Buffer.alloc(2)
  buffer.writeUInt16BE(value)
  return buffer
}

function float32(value) {
  const buffer = Buffer.alloc(4)
  buffer.writeFloatBE(value)
  return buffer
}

function float64(value) {
  const buffer = Buffer.alloc(8)
  buffer.writeDoubleBE(value)
  return buffer
}

function offlineUuid(username) {
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${username}`).digest()
  hash[6] = (hash[6] & 0x0f) | 0x30
  hash[8] = (hash[8] & 0x3f) | 0x80
  return hash
}

function packet(id, payload = Buffer.alloc(0)) {
  return Buffer.concat([varInt(id), payload])
}

function clientInformationPayload() {
  return Buffer.concat([
    mcString('en_us'),
    int8(12),
    varInt(0),
    bool(true),
    uint8(0x7f),
    varInt(1),
    bool(false),
    bool(true),
    varInt(0)
  ])
}

class LoadClient {
  constructor(index, args, metrics) {
    this.index = index
    this.args = args
    this.metrics = metrics
    this.username = `${args.name}${String(index + 1).padStart(2, '0')}`
    this.state = 'handshaking'
    this.compressionThreshold = -1
    this.incoming = Buffer.alloc(0)
    this.socket = null
    this.moveTimer = null
    this.connectedAt = 0
    this.playAt = 0
    this.canMove = false
    this.closed = false
    this.moves = 0
    this.chunks = 0
    this.batches = 0
    this.keepAlives = 0
    this.errors = 0
    this.positionCorrections = 0

    const lane = index % 8
    const row = Math.floor(index / 8)
    this.x = args.startX + lane * args.spacing
    this.y = args.startY
    this.z = args.startZ + row * args.spacing
    this.yaw = (index % 4) * 90
    this.pitch = 0
    this.direction = index % 2 === 0 ? 1 : -1
  }

  connect() {
    this.socket = net.createConnection({ host: this.args.host, port: this.args.port }, () => {
      this.connectedAt = Date.now()
      this.metrics.connected++
      this.log(`connected`)
      this.login()
    })

    this.socket.on('data', data => {
      this.incoming = Buffer.concat([this.incoming, data])
      try {
        this.processIncoming()
      } catch (error) {
        this.errors++
        this.metrics.errors++
        console.error(`[${this.username}] parse error: ${error.stack || error.message}`)
        this.close()
      }
    })

    this.socket.on('close', () => {
      if (this.closed) return
      this.closed = true
      if (this.moveTimer) clearInterval(this.moveTimer)
      this.metrics.closed++
      if (this.state === 'play' && this.metrics.inPlay > 0) this.metrics.inPlay--
      this.log(`closed moves=${this.moves} chunks=${this.chunks} batches=${this.batches}`)
    })

    this.socket.on('error', error => {
      this.errors++
      this.metrics.errors++
      console.error(`[${this.username}] socket error: ${error.message}`)
    })
  }

  login() {
    this.state = 'login'
    this.sendHandshake(2)
    this.send(0x00, Buffer.concat([mcString(this.username), offlineUuid(this.username)]))
  }

  sendHandshake(nextState) {
    this.send(0x00, Buffer.concat([
      varInt(PROTOCOL_VERSION),
      mcString(this.args.host),
      uint16(this.args.port),
      varInt(nextState)
    ]), false)
  }

  send(id, payload = Buffer.alloc(0), compressed = true) {
    if (!this.socket || this.socket.destroyed) return
    const body = packet(id, payload)
    let framed = body
    if (compressed && this.compressionThreshold >= 0) {
      framed = Buffer.concat([varInt(0), body])
    }
    this.socket.write(Buffer.concat([varInt(framed.length), framed]))
  }

  processIncoming() {
    while (true) {
      const length = readVarInt(this.incoming, 0)
      if (!length) return
      const start = length.size
      const end = start + length.value
      if (this.incoming.length < end) return

      let body = this.incoming.slice(start, end)
      this.incoming = this.incoming.slice(end)

      if (this.compressionThreshold >= 0) {
        const dataLength = readVarInt(body, 0)
        if (!dataLength) continue
        body = body.slice(dataLength.size)
        if (dataLength.value > 0) body = zlib.inflateSync(body)
      }

      const id = readVarInt(body, 0)
      if (!id) continue
      this.handlePacket(id.value, body.slice(id.size))
    }
  }

  handlePacket(id, payload) {
    if (this.state === 'login') {
      this.handleLoginPacket(id, payload)
    } else if (this.state === 'configuration') {
      this.handleConfigurationPacket(id, payload)
    } else if (this.state === 'play') {
      this.handlePlayPacket(id, payload)
    }
  }

  handleLoginPacket(id, payload) {
    if (id === CLIENTBOUND.LOGIN_SET_COMPRESSION) {
      const threshold = readVarInt(payload, 0)
      if (threshold) this.compressionThreshold = threshold.value
      this.log(`compression=${this.compressionThreshold}`)
      return
    }
    if (id === CLIENTBOUND.LOGIN_SUCCESS) {
      this.metrics.loggedIn++
      this.state = 'configuration'
      this.send(SERVERBOUND.LOGIN_ACKNOWLEDGED)
      this.send(SERVERBOUND.CONFIG_CLIENT_INFORMATION, clientInformationPayload())
      return
    }
    if (id === CLIENTBOUND.LOGIN_COOKIE_REQUEST) {
      const key = readVarInt(payload, 0)
      if (key) this.send(SERVERBOUND.LOGIN_COOKIE_RESPONSE, Buffer.concat([varInt(key.value), bool(false)]))
      return
    }
    this.noise('login', id, payload)
  }

  handleConfigurationPacket(id, payload) {
    if (id === CLIENTBOUND.CONFIG_FINISH) {
      this.state = 'play'
      this.playAt = Date.now()
      this.metrics.inPlay++
      this.send(SERVERBOUND.CONFIG_FINISH_ACKNOWLEDGED)
      if (!this.args.rconTeleport) this.moveTimer = setInterval(() => this.move(), this.args.moveInterval)
      return
    }
    if (id === CLIENTBOUND.CONFIG_COOKIE_REQUEST) {
      this.send(SERVERBOUND.CONFIG_COOKIE_RESPONSE, payload)
      return
    }
    if (id === CLIENTBOUND.CONFIG_PLUGIN_MESSAGE) {
      this.send(SERVERBOUND.CONFIG_PLUGIN_MESSAGE, payload)
      return
    }
    if (id === CLIENTBOUND.CONFIG_SELECT_KNOWN_PACKS) {
      this.send(SERVERBOUND.CONFIG_SELECT_KNOWN_PACKS, varInt(0))
      return
    }
    if (id === CLIENTBOUND.CONFIG_PING) {
      this.send(SERVERBOUND.CONFIG_PONG)
      return
    }
    this.noise('configuration', id, payload)
  }

  handlePlayPacket(id, payload) {
    if (id === CLIENTBOUND.PLAY_KEEP_ALIVE) {
      this.keepAlives++
      this.send(SERVERBOUND.PLAY_KEEP_ALIVE, payload)
      return
    }
    if (id === CLIENTBOUND.PLAY_PING) {
      this.send(SERVERBOUND.PLAY_PONG, payload)
      return
    }
    if (id === CLIENTBOUND.PLAY_PLAYER_POSITION) {
      this.acceptPosition(payload)
      return
    }
    if (id === CLIENTBOUND.PLAY_CHUNK_BATCH_START) {
      this.batches++
      this.metrics.batches++
      return
    }
    if (id === CLIENTBOUND.PLAY_CHUNK_BATCH_FINISHED) {
      this.send(SERVERBOUND.PLAY_CHUNK_BATCH_RECEIVED, float32(20))
      return
    }
    if (id === CLIENTBOUND.PLAY_LEVEL_CHUNK_WITH_LIGHT) {
      this.chunks++
      this.metrics.chunks++
      return
    }
    this.noise('play', id, payload)
  }

  acceptPosition(payload) {
    let offset = 0
    const teleportId = readVarInt(payload, offset)
    if (!teleportId) return
    offset += teleportId.size
    if (payload.length < offset + 8 * 6 + 4 * 2) return

    this.x = payload.readDoubleBE(offset)
    offset += 8
    this.y = payload.readDoubleBE(offset)
    offset += 8
    this.z = payload.readDoubleBE(offset)
    offset += 8
    offset += 24
    this.yaw = payload.readFloatBE(offset)
    offset += 4
    this.pitch = payload.readFloatBE(offset)

    this.send(SERVERBOUND.PLAY_ACCEPT_TELEPORTATION, varInt(teleportId.value))
    if (!this.canMove) this.send(SERVERBOUND.PLAY_PLAYER_LOADED)
    this.canMove = true
    this.positionCorrections++
    this.metrics.positionPackets++
    this.reportPosition()
    if (this.positionCorrections === 1 || this.args.verbose) {
      this.log(`position x=${this.x.toFixed(1)} y=${this.y.toFixed(1)} z=${this.z.toFixed(1)}`)
    }
  }

  reportPosition() {
    if (!this.canMove) return
    this.send(SERVERBOUND.PLAY_POSITION_LOOK, Buffer.concat([
      float64(this.x),
      float64(this.y),
      float64(this.z),
      float32(this.yaw),
      float32(this.pitch),
      uint8(1)
    ]))
    this.send(SERVERBOUND.PLAY_TICK_END)
  }

  move() {
    if (!this.canMove) return
    this.moves++
    this.metrics.moves++

    const sweep = Math.max(32, this.args.viewDistanceHint * 16)
    const step = this.args.speed * this.direction
    this.x += step

    if (Math.abs(this.x - this.args.startX) > sweep + this.index * 4) {
      this.direction *= -1
      this.z += 16
      this.x += this.args.speed * this.direction
    }

    this.yaw = (this.yaw + 11) % 360
    this.reportPosition()
  }

  close() {
    if (this.closed) return
    this.closed = true
    if (this.moveTimer) clearInterval(this.moveTimer)
    if (this.socket && !this.socket.destroyed) this.socket.end()
  }

  log(message) {
    console.log(`[${this.username}] ${message}`)
  }

  noise(state, id, payload) {
    if (this.args.verbose) console.log(`[${this.username}] ${state} packet id=0x${id.toString(16)} bytes=${payload.length}`)
  }
}

class RconClient {
  constructor(host, port, password) {
    this.host = host
    this.port = port
    this.password = password
    this.socket = null
    this.incoming = Buffer.alloc(0)
    this.nextId = 100
    this.pending = new Map()
  }

  async connect() {
    this.socket = net.createConnection({ host: this.host, port: this.port })
    this.socket.on('data', data => this.handleData(data))
    await once(this.socket, 'connect')
    const auth = await this.packet(3, this.password)
    if (auth.id === -1) throw new Error('RCON authentication failed')
  }

  command(command) {
    return this.packet(2, command)
  }

  packet(type, body) {
    if (!this.socket || this.socket.destroyed || this.socket.writableEnded) {
      return Promise.reject(new Error(`RCON socket is closed before command: ${body}`))
    }
    const id = this.nextId++
    const payload = Buffer.from(body, 'utf8')
    const length = 4 + 4 + payload.length + 2
    const buffer = Buffer.alloc(4 + length)
    buffer.writeInt32LE(length, 0)
    buffer.writeInt32LE(id, 4)
    buffer.writeInt32LE(type, 8)
    payload.copy(buffer, 12)
    buffer.writeUInt8(0, 12 + payload.length)
    buffer.writeUInt8(0, 13 + payload.length)

    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.socket.write(buffer)
      setTimeout(() => {
        if (!this.pending.has(id)) return
        this.pending.delete(id)
        reject(new Error(`RCON command timed out: ${body}`))
      }, 5000)
    })
  }

  handleData(data) {
    this.incoming = Buffer.concat([this.incoming, data])
    while (this.incoming.length >= 4) {
      const length = this.incoming.readInt32LE(0)
      if (this.incoming.length < 4 + length) return
      const frame = this.incoming.slice(4, 4 + length)
      this.incoming = this.incoming.slice(4 + length)
      const id = frame.readInt32LE(0)
      const type = frame.readInt32LE(4)
      const body = frame.slice(8, Math.max(8, frame.length - 2)).toString('utf8')
      const pending = this.pending.get(id)
      if (pending) {
        this.pending.delete(id)
        pending.resolve({ id, type, body })
      }
    }
  }

  close() {
    for (const pending of this.pending.values()) pending.reject(new Error('RCON socket closed'))
    this.pending.clear()
    if (this.socket && !this.socket.destroyed) this.socket.end()
  }
}

async function main() {
  const args = parseArgs(process.argv)
  const metrics = {
    connected: 0,
    loggedIn: 0,
    inPlay: 0,
    closed: 0,
    chunks: 0,
    batches: 0,
    moves: 0,
    teleports: 0,
    positionPackets: 0,
    errors: 0
  }
  let stopping = false
  const clients = Array.from({ length: args.clients }, (_, index) => new LoadClient(index, args, metrics))

  console.log(`Starting ${args.clients} fake network client(s) against ${args.host}:${args.port} for ${args.duration}s`)
  for (const client of clients) {
    client.connect()
    await sleep(args.connectDelay)
  }

  let rcon = null
  let teleportTimer = null
  if (args.rconTeleport) {
    rcon = new RconClient(args.rconHost, args.rconPort, args.rconPassword)
    await rcon.connect()
    console.log(`[rcon] connected to ${args.rconHost}:${args.rconPort}`)
    teleportTimer = setInterval(() => driveTeleports(rcon, clients, args).catch(error => {
      if (stopping) return
      metrics.errors++
      console.error(`[rcon] teleport error: ${error.message}`)
    }), args.teleportInterval)
    await driveTeleports(rcon, clients, args)
  }

  const startedAt = Date.now()
  const progressTimer = setInterval(() => {
    const seconds = ((Date.now() - startedAt) / 1000).toFixed(1)
    console.log(`[summary] t=${seconds}s connected=${metrics.connected}/${args.clients} play=${metrics.inPlay} moves=${metrics.moves} teleports=${metrics.teleports} positions=${metrics.positionPackets} chunks=${metrics.chunks} batches=${metrics.batches} errors=${metrics.errors}`)
  }, 10000)

  await sleep(args.duration * 1000)
  stopping = true
  clearInterval(progressTimer)
  if (teleportTimer) clearInterval(teleportTimer)
  for (const client of clients) client.close()
  if (rcon) rcon.close()
  await sleep(1000)

  const elapsed = (Date.now() - startedAt) / 1000
  const final = {
    seconds: Number(elapsed.toFixed(1)),
    clients: args.clients,
    connected: metrics.connected,
    inPlay: metrics.inPlay,
    moves: metrics.moves,
    teleports: metrics.teleports,
    positionPackets: metrics.positionPackets,
    chunks: metrics.chunks,
    batches: metrics.batches,
    errors: metrics.errors,
    chunksPerSecond: Number((metrics.chunks / elapsed).toFixed(2))
  }
  console.log(`[final] ${JSON.stringify(final)}`)
  if (metrics.inPlay === 0 || metrics.chunks === 0 || metrics.errors > 0) process.exitCode = 1
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function driveTeleports(rcon, clients, args) {
  const active = clients.filter(client => client.canMove && !client.closed)
  if (active.length === 0) return
  for (const client of active) {
    if (args.gamemode && client.gamemode !== args.gamemode) {
      await rcon.command(`gamemode ${args.gamemode} ${client.username}`)
      client.gamemode = args.gamemode
    }
    const hop = Math.max(0, Math.floor((Date.now() - client.playAt) / args.teleportInterval))
    const lane = client.index % 8
    const row = Math.floor(client.index / 8)
    const x = args.startX + lane * args.spacing + hop * args.teleportStep
    const z = args.startZ + row * args.spacing + (hop % 2) * args.teleportStep
    const y = args.teleportY ?? client.y
    await rcon.command(`tp ${client.username} ${x.toFixed(1)} ${y.toFixed(1)} ${z.toFixed(1)}`)
    client.metrics.teleports++
  }
}

function once(emitter, event) {
  return new Promise((resolve, reject) => {
    const onEvent = (...args) => {
      cleanup()
      resolve(...args)
    }
    const onError = error => {
      cleanup()
      reject(error)
    }
    const cleanup = () => {
      emitter.off(event, onEvent)
      emitter.off('error', onError)
    }
    emitter.once(event, onEvent)
    emitter.once('error', onError)
  })
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exit(1)
})

import net from "node:net";

const MAX_PACKET_BYTES = 1024 * 1024;
const host = process.argv[2] ?? "127.0.0.1";
const port = Number.parseInt(process.argv[3] ?? "25575", 10);
const command = process.argv.slice(4).join(" ");
const password = process.env.VIDSCREEN_RCON_PASSWORD;

if (!password || !command || !Number.isInteger(port) || port < 1 || port > 65535) {
  console.error("Usage: VIDSCREEN_RCON_PASSWORD=... node scripts/rcon.mjs [host] [port] <command>");
  process.exit(2);
}

function encodePacket(id, type, body) {
  const bodyBytes = Buffer.from(body, "utf8");
  const payloadLength = bodyBytes.length + 10;
  if (payloadLength > MAX_PACKET_BYTES) {
    throw new Error("RCON request exceeds limit");
  }
  const packet = Buffer.allocUnsafe(payloadLength + 4);
  packet.writeInt32LE(payloadLength, 0);
  packet.writeInt32LE(id, 4);
  packet.writeInt32LE(type, 8);
  bodyBytes.copy(packet, 12);
  packet.writeUInt16LE(0, packet.length - 2);
  return packet;
}

class PacketReader {
  #buffer = Buffer.alloc(0);
  #waiting = [];

  constructor(socket) {
    socket.on("data", data => {
      this.#buffer = Buffer.concat([this.#buffer, data]);
      this.#drain();
    });
    socket.on("error", error => this.#fail(error));
    socket.on("close", () => this.#fail(new Error("RCON connection closed")));
  }

  read() {
    return new Promise((resolve, reject) => {
      this.#waiting.push({ resolve, reject });
      this.#drain();
    });
  }

  #drain() {
    while (this.#waiting.length > 0 && this.#buffer.length >= 4) {
      const payloadLength = this.#buffer.readInt32LE(0);
      if (payloadLength < 10 || payloadLength > MAX_PACKET_BYTES) {
        this.#fail(new Error("Invalid RCON packet length"));
        return;
      }
      const packetLength = payloadLength + 4;
      if (this.#buffer.length < packetLength) {
        return;
      }
      const packet = this.#buffer.subarray(0, packetLength);
      this.#buffer = this.#buffer.subarray(packetLength);
      const waiter = this.#waiting.shift();
      waiter.resolve({
        id: packet.readInt32LE(4),
        type: packet.readInt32LE(8),
        body: packet.subarray(12, packetLength - 2).toString("utf8"),
      });
    }
  }

  #fail(error) {
    for (const waiter of this.#waiting.splice(0)) {
      waiter.reject(error);
    }
  }
}

const socket = net.createConnection({ host, port });
socket.setTimeout(10_000, () => socket.destroy(new Error("RCON connection timed out")));
const reader = new PacketReader(socket);

try {
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });

  socket.write(encodePacket(1, 3, password));
  let authenticated = false;
  for (let attempts = 0; attempts < 2; attempts++) {
    const response = await reader.read();
    if (response.id === -1) {
      throw new Error("RCON authentication failed");
    }
    if (response.id === 1 && response.type === 2) {
      authenticated = true;
      break;
    }
  }
  if (!authenticated) {
    throw new Error("RCON authentication response was invalid");
  }

  socket.write(encodePacket(2, 2, command));
  const response = await reader.read();
  if (response.id !== 2) {
    throw new Error("RCON command response was invalid");
  }
  process.stdout.write(response.body);
} finally {
  socket.end();
}

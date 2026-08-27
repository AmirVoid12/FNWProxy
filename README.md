# 🌉 FNWProxy

<p align="center">
  <img src="https://skillicons.dev/icons?i=java,redis" />
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.1-blue?style=flat-square" />
  <img alt="Platform" src="https://img.shields.io/badge/platform-Velocity-purple?style=flat-square" />
  <img alt="Status" src="https://img.shields.io/badge/status-unstable%20%2F%20in%20development-orange?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/license-private-lightgrey?style=flat-square" />
</p>

**FNWProxy** is the network proxy plugin of the **FNW** Minecraft server network, built for **Velocity**. It handles player routing, lobby load balancing, limbo fallback, reconnection to a player's last server, and cross-server communication via Redis — acting as the entry point for the entire network.

👨‍💻 Developed by: [AmirVoid12](https://amirvoid12.ir) — Tabriz 🇮🇷

---

## ⚠️ Project Status

> This plugin, along with its related repositories, is **not fully stable yet**.
> We are actively working on fixing bugs and improving stability, and the project is **being updated at all times** ⏳.
> If you run into a bug or issue, feel free to open an issue — feedback is always welcome 🙏.

---

## 📜 About This Repository

This project was originally private and belonged entirely to **FlameNetwork** 🔒. For private reasons, FlameNetwork has been shut down 🛑, and as a result these sources have been made **public** 🌍. They are still being **debugged and updated continuously** 🔧, so expect frequent changes, fixes, and improvements over time.

---

## 🔗 Related Repositories

FNWProxy is part of a larger ecosystem that includes the following plugins. All of these projects work together as part of the FNW network, but just like FNWProxy, they are **not fully stable yet** and are under continuous development.

| Project | Description | Link |
|---|---|---|
| 🔥 **FNWCore** | The core plugin of the network — ranks, teleportation, homes, warps, vanish, tab/scoreboard, and more | [github.com/AmirVoid12/FNWCore](https://github.com/AmirVoid12/FNWCore) |
| 🌫️ **FNWLimbo** | Limbo server (keeps players in a waiting/loading state) | [github.com/AmirVoid12/FNWLimbo](https://github.com/AmirVoid12/FNWLimbo) |
| 🏠 **FNWLobby** | Dedicated lobby plugin for the network | [github.com/AmirVoid12/FNWLobby](https://github.com/AmirVoid12/FNWLobby) |

> 💡 FNWProxy is the entry point of the network — it's built to work with FNWCore, FNWLimbo, and FNWLobby (and other Spigot/Paper backend servers) rather than as a standalone piece.

---

## ✨ Features

- 🧭 Smart initial-server resolution on connect: reconnects players to their **last server** if it's still online, otherwise picks a lobby, otherwise falls back to limbo
- ⚖️ **Load-balanced lobby selection** — routes players to the lobby with the fewest players, using live server data pulled from Redis (`fnw:gamemode:*` sets)
- 🌫️ **Limbo fallback** — automatically publishes a limbo placement request when no lobby is available
- 🔴 Redis-backed gamemode registry, letting backend servers register themselves under custom gamemode groups (e.g. `lobby-1`, `lobby-2`)
- 🎮 `/gamemode <name>` style routing command — connects players to the best available server for a given gamemode
- 🧩 Custom **Alias command framework** — annotation-driven (`@AliasCommand`) command registration with permission checks, tab-completion, and error handling built in
- 🔌 Connect/Disconnect/Kick event listeners for tracking player state and last-known server across the network
- ⚙️ Central `Handler` kernel and `Config` system for wiring modules together at startup

---

## 🖥️ Platform

```
Velocity 3.3.0+   (Java 17)
```

---

## 🚀 Usage / Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/AmirVoid12/FNWProxy.git
   cd FNWProxy
   ```

2. **Build the project**
   Unlike FNWCore/FNWLimbo, FNWProxy does **not** require a local `server.jar` — it depends on the Velocity API directly from Maven, so a plain build is enough:
   ```bash
   ./gradlew build
   ```
   The compiled plugin jar will be generated in `build/libs/`.

3. **Drop the jar** into your Velocity proxy's `plugins/` folder, start the proxy once to generate the config, then edit `config.yml` (Redis host, port, credentials, etc.) to match your environment.

4. **Register your backend servers** in Velocity's own `velocity.toml` as usual, and make sure your backend Spigot/Paper servers (FNWCore, FNWLimbo, FNWLobby) point to the **same Redis instance**, since coordination between the proxy and the backend servers happens entirely through Redis.

### ⚠️ Known Issue — Java Version

FNWProxy targets **Java 17**, same as Velocity itself. Unlike FNWCore/FNWLimbo (which target Spigot 1.8.8 and require Java 8 for the backend server), the proxy itself has no Java 8 requirement — just make sure you're running it with **Java 17 or newer**, since Velocity will refuse to start on older versions.

---

## 🛠️ Built With

<p>
  <img src="https://skillicons.dev/icons?i=java" />
</p>

- ☕ Java 17
- 🌀 Velocity API 3.3.0
- 🐘 Gradle (Shadow plugin)
- 🔴 Jedis (Redis)
- 🍃 Kyori Adventure

---

## 📄 License

This project was originally private and belongs to **FlameNetwork**. Due to private reasons, FlameNetwork has been shut down, and these sources have since been made public. The code is provided as-is and is continuously debugged and updated.

---

<p align="center">
  Made with ❤️ by <a href="https://amirvoid12.ir">AmirVoid12</a>
</p>

<p align="center">
  ⭐ <b>Don't forget to star this repository!</b> ⭐
</p>

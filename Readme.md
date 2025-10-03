# 🎧 JOPA – Joint Operations for Peer-to-peer Audio/Video

![Joint Operations for Peer-to-peer Audio/Video ]("images/icon_1024.png")

> 👉 "Just One Phone App"... or if you want the corporate version:  
> **Joint Operations for Peer-to-peer Audio/Video**.
>
> Either way – it’s JOPA, and yes, we know what it sounds like.  
> But it’s also the future of calling. 💥

---

## 🚀 What is JOPA?
JOPA is **a peer-to-peer WebRTC-based dialer app**.  
It’s light, fast, and doesn’t care about corporate bloatware.

- 🔗 **True P2P calls** – no shady middlemen.
- 🎥 **Video + Audio** – works out of the box.
- 🔒 **Secure by default** – ICE, STUN, TURN, all the acronyms.
- 🕶️ **Ironically serious** – funny name, dead serious tech.

---

## 🛠️ Tech Stack
- **Kotlin** + **Android SDK 36**
- **WebRTC (AOSP 137)**
- **Coroutines + OkHttp + Serialization**
- **PeerConnectionFactory, EGL, ADM**
- **TURN/STUN support**

---

## 📦 Installation
1. Clone this JOPA:
   ```bash
   git clone https://github.com/digkill/JopaCall
   ```

2. Add your secrets to local.properties:
```
local.properties

properties

app.ws_base=wss://site/ws

app.turn_url=turns:site:5349?transport=tcp
app.turn_user=user
app.turn_pass=pass
```

3. Build & run:
```
./gradlew assembleDebug
```

🎮 Usage

Open JOPA

Allow mic & camera (don’t be shy)

Dial your buddy

Profit.

🤝 Contributing

Want to contribute? Fork it, PR it, meme it.
Just don’t rename it – the name stays.

🧠 Philosophy

We believe calling apps should be:

Minimal

Transparent

Fun to use

And not owned by a megacorp

⚠️ Disclaimer

This is experimental.
If it crashes, burns, or starts Skynet – it’s on you.

🐇 Final Note

Yes, the name is JOPA.
Yes, it’s on purpose.
Yes, it works.
Enjoy your calls. 🎤🎬
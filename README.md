# 🎉 PartySync

**Synchronized wireless audio for everyone**

Turn any Android device into a wireless speaker and create epic multi-room audio experiences with your friends!

## ✨ Features

- 🎵 **Real-time Audio Sync** - Perfect synchronization across multiple devices
- 📱 **WiFi Direct & Hotspot** - Works without internet connection
- 🎧 **Multi-Device Support** - Connect unlimited Android devices
- 🎪 **Party Mode** - Easy room creation and joining
- 🔧 **Smart Configuration** - Automatic device optimization
- 🎼 **Music Library** - Play your favorite tracks synchronized

## 🚀 Why PartySync?

Ever wanted to fill your entire house with music but only have small phone speakers? PartySync transforms every Android phone and tablet into a synchronized wireless speaker system!

Perfect for:
- **House Parties** 🏠 - Fill every room with synchronized music
- **Outdoor Events** 🏕️ - No WiFi? No problem! 
- **Workshops & Classes** 🎓 - Everyone hears the same thing at the same time
- **Gaming Sessions** 🎮 - Synchronized audio for multiplayer experiences

## 🛠️ Tech Stack

- **Kotlin** - Modern Android development
- **Jetpack Compose** - Beautiful, reactive UI
- **WiFi Direct** - Direct device-to-device communication
- **Local Hotspot** - Internet-free networking
- **ExoPlayer** - Professional audio streaming
- **Coroutines** - Smooth async operations

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Host Device   │    │  Client Device  │    │  Client Device  │
│                 │    │                 │    │                 │
│  ┌───────────┐  │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │ SyncMgr   │◄─┼────┼─►│ SyncMgr   │  │    │  │ SyncMgr   │  │
│  └───────────┘  │    │  └───────────┘  │    │  └───────────┘  │
│  ┌───────────┐  │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │ AudioStr  │  │    │  │ AudioStr  │  │    │  │ AudioStr  │  │
│  └───────────┘  │    │  └───────────┘  │    │  └───────────┘  │
│  ┌───────────┐  │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │ Network   │  │    │  │ Network   │  │    │  │ Network   │  │
│  └───────────┘  │    │  └───────────┘  │    │  └───────────┘  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🤝 Contributing

We'd love your help making PartySync even better! Here's how you can contribute:

### 🐛 Found a Bug?
- Check existing [issues](https://github.com/gauravyad69/partysync/issues)
- Create a detailed bug report with steps to reproduce

### 💡 Have an Idea?
- Audio codec improvements
- New synchronization algorithms  
- UI/UX enhancements
- Cross-platform support (iOS, Desktop)
- Bluetooth audio support

### 🛠️ Want to Code?
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### 📋 Areas We Need Help With:

- **🔊 Audio Processing** - Better codecs, lower latency
- **📡 Networking** - Mesh networking, peer discovery
- **🎨 UI/UX** - Material 3 design, accessibility
- **🧪 Testing** - Unit tests, integration tests
- **📱 Platform Support** - iOS app, desktop companion
- **📚 Documentation** - API docs, tutorials

## 🏃‍♂️ Quick Start

```bash
# Clone the repository
git clone https://github.com/gauravyad69/partysync.git

# Open in Android Studio
cd partysync

# Build and run
./gradlew assembleDebug
```

## 📋 Requirements

- **Android 7.0 (API 24)+**
- **WiFi Direct or Mobile Hotspot capability**
- **Storage permissions** for music access

## 🎯 Roadmap

- [ ] **v1.0** - Basic WiFi Direct sync
- [ ] **v1.1** - Bluetooth support  
- [ ] **v1.2** - Cross-platform (iOS)
- [ ] **v1.3** - Mesh networking
- [ ] **v2.0** - Cloud sync capabilities

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- ExoPlayer team for amazing audio capabilities
- Android WiFi Direct documentation
- Jetpack Compose team for modern UI framework

## 📞 Support

- 📧 Email: gauravyad2077@gmail.com
- 🐦 X(Twitter): [@gauravyad69](https://x.com/gauravyad69)
- 💬 Discussions: [GitHub Discussions](https://github.com/gauravyad69/partysync/discussions)

---

**Star ⭐ this repo if you find PartySync useful!**

*Made with ❤️ for the Android community*
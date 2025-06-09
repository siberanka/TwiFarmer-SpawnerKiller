# Farmer SpawnerKiller Module

A lightweight module for the Farmer plugin that automatically kills mobs spawned by mob spawners.

---

## 📦 Installation

1. Place the `SpawnerKiller` folder into your `plugins/Farmer/modules/` directory.  
2. Restart your server.  
3. A `spawner-killer.yml` and matching language file will be generated under `plugins/Farmer/modules/SpawnerKiller/`.

---

## ⚙️ Features

- **Automatic Mob Elimination**  
  Detects and kills any mob hatched from an active spawner in a Farmer region (or globally if no Farmer is set).

- **Farmer-Dependent or Standalone**  
  Can require an active Farmer to operate, or run without one based on configuration.

- **Burn-on-Kill Option**  
  Optionally “cook” each kill by using fire damage to yield cooked drops (e.g. cooked meat).

- **Permission-Controlled**  
  Enable or disable the module’s behavior at runtime via a single permission node.

- **Mob Blacklist / Whitelist**  
  Define exactly which mob types should be eliminated or ignored.

---

## 🤝 Contributing

1. Fork the repository.  
2. Add your enhancements or bug fixes.  
3. Open a pull request against the `master` branch.

Please follow existing code style and update documentation as needed.

---

Thank you for using the **SpawnerKiller** module—happy farming!  

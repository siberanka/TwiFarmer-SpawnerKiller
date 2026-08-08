# Farmer SpawnerKiller Wiki

## Türkçe

### Gereksinimler ve kurulum

| Bileşen | Gereksinim |
| --- | --- |
| Farmer | v6-b125 veya daha yeni uyumlu sürüm |
| Sunucu | Paper 1.21.x / 26.x, Leaf veya Folia |
| Java | 1.21.x için Java 21, 26.x için Java 25 |
| İsteğe bağlı | SpawnerMeta 25.8, WildStacker API 2025.2 |

1. Sunucuyu durdurun.
2. Modül JAR dosyasını `plugins/Farmer/modules/` klasörüne yerleştirin.
3. Sunucuyu başlatın.
4. `plugins/Farmer/modules/spawnerkiller/config.yml` içinde `status: true` yapın.
5. Farmer'ı yeniden yükleyin veya sunucuyu yeniden başlatın.

Bu modül bağımsız bir Bukkit eklentisi değildir; normal `plugins` klasörüne kurulmaz.

### Kullanım

Modül açıkken Farmer ana menüsündeki modüller bölümünde **Spawner Öldürücü** görünür. `customPerm` iznine sahip kullanıcı ilgili Farmer için modülü açıp kapatabilir.

Bir spawner doğumu geldiğinde:

1. Varlık türü seçili beyaz/kara liste kipine göre doğrulanır.
2. `requireFarmer` açıksa konumda etkin ve izin veren bir Farmer aranır.
3. Farmer mevcutsa gereken seviye ve kaydedilmiş modül durumu uygulanır.
4. Vanilla, SpawnerMeta veya WildStacker yolu belirlenir.
5. Ganimet, pişirme ve deneyim hesabı sınırlar içinde hazırlanır.
6. Varlık kaldırma ve ödül üretme işlemi varlığın sahibi olan Paper/Folia zamanlayıcısında tamamlanır.

SpawnerMeta büyük doğum grupları birden fazla tick'e bölünebilir. WildStacker miktarı işlemden hemen önce yeniden doğrulanır; geçersiz veya aşırı değerler kapalı biçimde reddedilir.

### Komutlar

SpawnerKiller ayrı bir komut kaydetmez. Yönetim ve yeniden yükleme için Farmer'ın `/farmer` ve `/farmer reload` komutları kullanılır.

### İzinler

| İzin | Açıklama |
| --- | --- |
| `farmer.spawnerkiller` | Varsayılan `customPerm`; Farmer menüsünden SpawnerKiller durumunu değiştirmeye izin verir. |
| `farmer.admin` | Farmer yönetimi ve SpawnerKiller güncelleme bildirimlerini alır. |

`customPerm` düğümü yapılandırmadan değiştirilebilir.

### Seviye kilidi

`required-farmer-level` bir tabanlı Farmer seviyesidir ve varsayılanı `1` değeridir.

- Değer yükseltilirse düşük seviyeli mevcut Farmer'larda modül etkisiz olur.
- Kilitli Farmer'larda kullanıcı durum değiştiremez.
- Önceden kaydedilmiş açık/kapalı tercih silinmez.
- Farmer gereken seviyeye ulaştığında veya gereksinim düşürüldüğünde tercih yeniden uygulanır.
- Farmer yükseltme menüsü SpawnerKiller'ın açılacağı seviyeyi gösterir.

`requireFarmer: false` ile Farmer bulunmayan alanlarda çalışmaya izin verildiğinde doğrulanabilecek Farmer seviyesi veya kişisel modül durumu yoktur. Seviye tabanlı sunucular için `requireFarmer: true` kullanın.

### Yapılandırma

Dosya: `plugins/Farmer/modules/spawnerkiller/config.yml`

#### Genel davranış

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `status` | `false` | Modülü ve Farmer menü girişini açar. |
| `requireFarmer` | `true` | İşlem için konumda Farmer bulunmasını zorunlu kılar. `false` Farmer dışı alanlara izin verir. |
| `cookFoods` | `true` | Et, patates gibi desteklenen yiyecek ganimetlerini pişmiş karşılığına dönüştürür. |
| `removeMob` | `true` | Doğan yaratığı görünür dünyada bırakmadan kaldırıp ganimeti üretir. |
| `defaultStatus` | `true` | Yeni Farmer'ların başlangıç SpawnerKiller durumudur. |
| `required-farmer-level` | `1` | Modülün Farmer içinde kullanılabildiği en düşük seviye. |
| `customPerm` | `farmer.spawnerkiller` | Menüden durum değiştirme izni. |
| `wildstacker-recovery-radius` | `16` | WildStacker bir entity oluşturmadan mevcut yığını büyüttüğünde, yalnız spawner kaynaklı yığınlar için taranan blok yarıçapı (`1-64`). |

#### Varlık filtreleri

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `mode` | `blacklist` | `whitelist` yalnızca beyaz listedekileri; `blacklist` kara liste dışındakileri işler. |
| `whitelist` | `VILLAGER` | Beyaz liste kipinde izin verilen Bukkit `EntityType` adları. |
| `blacklist` | `VILLAGER` | Kara liste kipinde reddedilen Bukkit `EntityType` adları. |

Örnek:

```yaml
mode: whitelist
whitelist:
  - ZOMBIE
  - SKELETON
  - CREEPER
blacklist:
  - VILLAGER
```

Yalnızca etkin `mode` ile ilişkili liste karar verir. Varlık adları büyük/küçük harften bağımsız biçimde normalleştirilir; geçersiz adlar dosya bakımı sırasında düzeltilir.

#### Güncelleme denetimi

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `update-checker.enable` | `true` | Kararlı SpawnerKiller GitHub sürümlerini arka planda denetler. |
| `update-checker.check-interval-hours` | `6` | Denetim aralığı; güvenli aralık `1-168` saattir. |
| `update-checker.connect-timeout-seconds` | `5` | Bağlantı zaman aşımı; `2-30`. |
| `update-checker.request-timeout-seconds` | `8` | İstek zaman aşımı; `3-60`. |

Yeni sürüm bildirimi konsola ve çevrimiçi operatörlere veya `farmer.admin` izni olan oyunculara sürüm başına bir kez gönderilir.

#### Üretim optimizasyonu

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `optimize-module.enable` | `false` | Aşağıdaki bütün optimizasyonların anahtarıdır. Kapalıyken alt değerler etkisizdir. |
| `optimize-module.async-precheck` | `true` | Değişmez kuyruk/filtre hazırlığını sınırlı Paper asenkron yoluna taşır. |
| `optimize-module.async-stack-drops` | `true` | WildStacker ganimet hesabını asenkron yapar, sonucu varlık bölgesinde yeniden doğrular. |
| `optimize-module.processing-delay-ticks` | `2` | Varlık işini kendi `EntityScheduler` zamanlayıcısında geciktirir; `0` ek gecikme yoktur. |
| `optimize-module.max-entities-per-run` | `64` | Bir çalışmada işlenen SpawnerMeta varlığı; daha büyük gruplar sonraki tick'lere yayılır. |
| `optimize-module.max-queued-entities` | `512` | Genel bekleyen varlık sınırı. Taşma doğru bölgede anlık güvenli işleme döner. |
| `optimize-module.max-pending-per-region` | `64` | Her 8x8 chunk bölgesi için bekleyen iş sınırı. |
| `optimize-module.collapse-duplicate-spawns` | `true` | Aynı varlık için yinelenen Bukkit/SpawnerMeta bildirimlerini birleştirir. |
| `optimize-module.batch-drops` | `true` | Benzer elle üretilen ganimetleri yasal Minecraft yığınlarına böler. |
| `optimize-module.max-stack-process-amount` | `100000` | Bozuk veya kötü niyetli yığın miktarları için sert üst sınır. |
| `optimize-module.audit-log-rate-limit-ms` | `5000` | Aynı tür operasyon uyarıları arasındaki en kısa süre. |

Bukkit dünyası ve varlıkları asenkron iş parçacığında değiştirilmez. Asenkron hesap sonuçları varlık kimliği, geçerliliği, yığın miktarı ve bölge durumu yeniden doğrulandıktan sonra uygulanır.

### SpawnerMeta ve WildStacker

- SpawnerMeta algılandığında toplu doğum yolu ile Bukkit yedek olayları birlikte dinlenir; entity kimliği üzerinden tekilleştirme çift ganimet üretimini engeller.
- `max-entities-per-run`, büyük SpawnerMeta gruplarının tick'lere yayılmasını sağlar.
- WildStacker algılandığında gerçek yığın miktarı ve eklentinin ganimet hesabı kullanılır.
- WildStacker Paper pre-spawn optimizasyonunda yeni entity üretmeden mevcut bir yığını büyütürse, modül bağlı entity'yi ve yapılandırılmış yarıçaptaki yalnız `SPAWNER` kaynaklı yığınları bölge güvenli biçimde yeniden denetler.
- `async-stack-drops` yalnızca hesap kısmını asenkron yapar; varlığın kaldırılması ve ganimet üretimi varlık zamanlayıcısında gerçekleşir.
- Deneyim hesabı yaratığın çalışma anındaki Paper ödülünü kullanır; yeni 26.x varlık türleri sabit eski bir tabloya bağlı değildir.

### Dil dosyaları

Modül Farmer'ın seçili dilini izler ve `plugins/Farmer/modules/spawnerkiller/lang/` altında paketli dilleri sağlar. Modül adı, açık/kapalı/kilitli durum, seviye gereksinimi, menü açıklamaları ve güncelleme bildirimi dahil oyuncuya gösterilen metinler buradan düzenlenir.

### Otomatik dosya bakımı

Başlangıçta ve modül yeniden yüklemesinde yapılandırma ile dil dosyaları denetlenir. Eksik bilinen girdiler eklenir; bozuk YAML, yanlış türler, anlamsız değerler, aşırı sayılar, geçersiz izinler, filtre kipi ve varlık adları düzeltilir. Geçerli özel ve bilinmeyen genişletme girdileri korunur.

Mevcut bir dosya değiştirilmeden önce aynı klasörde UTC zaman damgalı `*.bak-*` yedeği oluşturulur. Aynı dosya için yalnızca en yeni 20 yedek tutulur. YAML'ın aynı tam sayı değerini `Integer` veya `Long` olarak okuması değişiklik sayılmaz ve yeni yedek üretmez.

### Sorun giderme

- Menü girişi yoksa JAR yolunu ve `status: true` değerini kontrol edin.
- Modül kilitliyse Farmer seviyesini `required-farmer-level` ile karşılaştırın.
- Kullanıcı durumu değiştiremiyorsa `customPerm` iznini doğrulayın.
- Yaratık işlenmiyorsa `mode`, etkin liste, `requireFarmer`, Farmer'ın kaydedilmiş modül durumu ve seviyesini kontrol edin.
- SpawnerMeta veya WildStacker yolu kullanılmıyorsa bağımlılığın desteklenen sürümünü ve başlangıç günlüğündeki entegrasyon satırını inceleyin.
- Kuyruk taşması uyarısı varsa optimizasyon sınırlarını yalnızca ölçüm yaptıktan sonra yükseltin.

### Derleme

```bash
mvn -o clean package
```

Üretilen modül JAR dosyası `target/` klasöründedir.

---

## English

### Requirements and installation

| Component | Requirement |
| --- | --- |
| Farmer | v6-b125 or a newer compatible build |
| Server | Paper 1.21.x / 26.x, Leaf, or Folia |
| Java | Java 21 for 1.21.x, Java 25 for 26.x |
| Optional | SpawnerMeta 25.8, WildStacker API 2025.2 |

1. Stop the server.
2. Place the module JAR in `plugins/Farmer/modules/`.
3. Start the server.
4. Set `status: true` in `plugins/Farmer/modules/spawnerkiller/config.yml`.
5. Reload Farmer or restart the server.

This module is not a standalone Bukkit plugin and does not belong in the normal `plugins` directory.

### Usage

When enabled, **Spawner Killer** appears in the modules section of the Farmer menu. A user with `customPerm` may toggle it for the current Farmer.

When a spawner spawn is received:

1. The entity type is validated against the selected whitelist/blacklist mode.
2. If `requireFarmer` is enabled, an active Farmer that permits processing must exist at the location.
3. Where a Farmer exists, its required level and saved module state apply.
4. The vanilla, SpawnerMeta, or WildStacker path is selected.
5. Drops, cooking, and experience are prepared within configured bounds.
6. Entity removal and reward creation complete on the Paper/Folia scheduler that owns the entity.

Large SpawnerMeta batches may be divided across ticks. WildStacker amount is revalidated immediately before commit; invalid or excessive values fail closed.

### Commands

SpawnerKiller registers no separate commands. Use Farmer's `/farmer` and `/farmer reload` commands for management and reloads.

### Permissions

| Permission | Description |
| --- | --- |
| `farmer.spawnerkiller` | Default `customPerm`; allows SpawnerKiller to be toggled through the Farmer menu. |
| `farmer.admin` | Farmer administration and SpawnerKiller update notifications. |

The `customPerm` node may be changed in configuration.

### Level gate

`required-farmer-level` is a one-based Farmer level and defaults to `1`.

- Raising it disables the module for existing lower-level Farmers.
- Users cannot toggle it for locked Farmers.
- The previously saved enabled/disabled preference is retained.
- The preference applies again after reaching the level or lowering the requirement.
- Farmer's upgrade menu displays the level at which SpawnerKiller unlocks.

When `requireFarmer: false` permits processing where no Farmer exists, there is no Farmer level or personal module state to validate. Use `requireFarmer: true` on level-gated servers.

### Configuration

File: `plugins/Farmer/modules/spawnerkiller/config.yml`

#### General behavior

| Setting | Default | Description |
| --- | --- | --- |
| `status` | `false` | Enables the module and Farmer menu entry. |
| `requireFarmer` | `true` | Requires a Farmer at the spawn location. `false` permits areas without Farmer. |
| `cookFoods` | `true` | Converts supported food drops such as meat or potatoes to cooked variants. |
| `removeMob` | `true` | Removes the spawned mob before it remains visible and produces its rewards. |
| `defaultStatus` | `true` | Initial SpawnerKiller state for newly created Farmers. |
| `required-farmer-level` | `1` | Lowest Farmer level that may use the module. |
| `customPerm` | `farmer.spawnerkiller` | Permission required to toggle the menu state. |
| `wildstacker-recovery-radius` | `16` | Block radius (`1-64`) scanned only for spawner-origin stacks when WildStacker grows a stack without creating an entity. |

#### Entity filters

| Setting | Default | Description |
| --- | --- | --- |
| `mode` | `blacklist` | `whitelist` processes listed entities only; `blacklist` processes everything except listed entities. |
| `whitelist` | `VILLAGER` | Bukkit `EntityType` names allowed in whitelist mode. |
| `blacklist` | `VILLAGER` | Bukkit `EntityType` names rejected in blacklist mode. |

Example:

```yaml
mode: whitelist
whitelist:
  - ZOMBIE
  - SKELETON
  - CREEPER
blacklist:
  - VILLAGER
```

Only the list associated with the active `mode` decides processing. Entity names are normalized case-insensitively; invalid names are repaired by file maintenance.

#### Update checker

| Setting | Default | Description |
| --- | --- | --- |
| `update-checker.enable` | `true` | Checks stable SpawnerKiller GitHub releases asynchronously. |
| `update-checker.check-interval-hours` | `6` | Check interval; safe range `1-168` hours. |
| `update-checker.connect-timeout-seconds` | `5` | Connection timeout; `2-30`. |
| `update-checker.request-timeout-seconds` | `8` | Request timeout; `3-60`. |

A new release is reported once per version to console and online operators or players with `farmer.admin`.

#### Production optimization

| Setting | Default | Description |
| --- | --- | --- |
| `optimize-module.enable` | `false` | Master switch for all optimizations below. Children are inert while disabled. |
| `optimize-module.async-precheck` | `true` | Moves immutable queue/filter preparation to a bounded Paper async path. |
| `optimize-module.async-stack-drops` | `true` | Calculates WildStacker loot asynchronously and revalidates on the entity region. |
| `optimize-module.processing-delay-ticks` | `2` | Defers work on the entity's `EntityScheduler`; `0` adds no configured delay. |
| `optimize-module.max-entities-per-run` | `64` | SpawnerMeta entities processed per run; larger groups spread across ticks. |
| `optimize-module.max-queued-entities` | `512` | Global queued-entity limit. Overflow falls back to immediate region-safe work. |
| `optimize-module.max-pending-per-region` | `64` | Pending-work limit for each 8x8 chunk region. |
| `optimize-module.collapse-duplicate-spawns` | `true` | Coalesces duplicate Bukkit/SpawnerMeta notifications for the same entity. |
| `optimize-module.batch-drops` | `true` | Splits similar generated drops into legal Minecraft item stacks. |
| `optimize-module.max-stack-process-amount` | `100000` | Hard ceiling for corrupt or hostile stack amounts. |
| `optimize-module.audit-log-rate-limit-ms` | `5000` | Minimum interval between repeated warnings in one category. |

Bukkit worlds and entities are never mutated asynchronously. Async calculation results apply only after entity identity, validity, stack amount, and region state are revalidated.

### SpawnerMeta and WildStacker

- When SpawnerMeta is detected, its batch path and Bukkit fallback events are both observed; entity-identity coalescing prevents duplicate rewards.
- `max-entities-per-run` spreads large SpawnerMeta batches across ticks.
- When WildStacker is detected, the real stack amount and its loot calculation are used.
- If WildStacker's Paper pre-spawn optimization grows an existing stack without creating an entity, the linked entity and only `SPAWNER`-origin stacks within the configured radius are rechecked on their owning regions.
- `async-stack-drops` moves calculation only; entity removal and drop creation remain on the entity scheduler.
- Experience uses each mob's runtime Paper reward, so new 26.x entity types do not depend on an obsolete fixed table.

### Language files

The module follows Farmer's selected language and provides bundled languages under `plugins/Farmer/modules/spawnerkiller/lang/`. The module name, enabled/disabled/locked states, level requirement, menu descriptions, update notice, and other player-facing text are editable there.

### Automatic file maintenance

Configuration and language files are validated on startup and module reload. Missing known entries are added; malformed YAML, wrong types, meaningless values, excessive numbers, malformed permissions, filter modes, and entity names are repaired. Valid custom and unknown extension entries are preserved.

Before an existing file is modified, a UTC timestamped `*.bak-*` copy is created beside it. Only the newest 20 backups are retained for each file. Reading the same integral YAML value as `Integer` or `Long` is not treated as a change and does not create another backup.

### Troubleshooting

- If no menu entry exists, check the JAR path and `status: true`.
- If the module is locked, compare the Farmer level with `required-farmer-level`.
- If a user cannot toggle it, verify `customPerm`.
- If a mob is not processed, check `mode`, the active list, `requireFarmer`, the Farmer's saved module state, and its level.
- If SpawnerMeta or WildStacker is not used, verify the supported dependency version and inspect the integration line at startup.
- If queue-overflow warnings appear, increase optimization bounds only after measuring server load.

### Building

```bash
mvn -o clean package
```

The module JAR is written under `target/`.

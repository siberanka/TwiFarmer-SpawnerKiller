package xyz.geik.farmer.modules.spawnerkiller.service;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.api.FarmerAPI;
import xyz.geik.farmer.api.managers.FarmerManager;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Owns the hot path. Async work is limited to immutable admission/filter data;
 * every Bukkit, Farmer and stacking API mutation runs on the entity's owning
 * Paper/Folia region thread.
 *
 * @author siberanka
 */
public final class SpawnProcessor implements AutoCloseable {

    private static final Set<String> PASSIVE_TYPES = Set.of(
            "OCELOT", "CHICKEN", "COW", "HORSE", "PIG", "RABBIT", "SHEEP", "SQUID",
            "WOLF", "BAT", "DONKEY", "MULE", "LLAMA", "TRADER_LLAMA", "MUSHROOM_COW",
            "MOOSHROOM", "PARROT", "POLAR_BEAR", "BEE", "STRIDER", "COD", "FOX",
            "PIGLIN", "SALMON", "TROPICAL_FISH", "TROPICALFISH", "SKELETON_HORSE",
            "SKELETONHORSE", "TURTLE", "PANDA", "DOLPHIN");

    private final SpawnerKiller module;
    private final Plugin plugin;
    private final NamespacedKey processedKey;
    private final boolean wildStackerAvailable;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger asyncDropPlans = new AtomicInteger();
    private final Set<UUID> pendingEntities = ConcurrentHashMap.newKeySet();
    private final Map<RegionKey, AtomicInteger> pendingRegions = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWarnings = new ConcurrentHashMap<>();

    public SpawnProcessor(SpawnerKiller module, Plugin plugin) {
        this.module = module;
        this.plugin = plugin;
        this.processedKey = new NamespacedKey(plugin, "spawnerkiller-processed");
        this.wildStackerAvailable = Bukkit.getPluginManager().isPluginEnabled("WildStacker");
    }

    public void submit(Entity entity) {
        submit(entity, 0L);
    }

    public void submit(Entity entity, long extraDelayTicks) {
        if (entity == null || closed.get() || !module.isOperational()) {
            return;
        }

        // SpawnerMeta callbacks are not assumed to be on the owning region.
        if (!Bukkit.isOwnedByCurrentRegion(entity)) {
            entity.getScheduler().execute(plugin,
                    () -> submit(entity, extraDelayTicks),
                    null,
                    0L);
            return;
        }

        if (!(entity instanceof LivingEntity) || !entity.isValid() || entity.isDead()
                || !module.shouldProcess(entity.getType())) {
            return;
        }

        SpawnerKiller.OptimizationSettings optimization = module.getOptimizationSettings();
        if (!optimization.enable()) {
            processOnOwnedRegion(entity, optimization);
            return;
        }

        Reservation reservation = reserve(entity, optimization);
        if (reservation != null && reservation.duplicate) {
            return;
        }
        if (reservation == null) {
            audit("queue-overflow", "SpawnerKiller queue limit reached; using immediate region-safe processing.", null);
            processOnOwnedRegion(entity, optimization);
            return;
        }

        long delay = Math.max(0L, optimization.processingDelayTicks() + extraDelayTicks);
        if (optimization.asyncPrecheck()) {
            EntityType type = entity.getType();
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                    if (closed.get() || !module.shouldProcess(type)) {
                        reservation.release();
                        return;
                    }
                    scheduleOwned(entity, delay, optimization, reservation);
                });
            }
            catch (RuntimeException exception) {
                reservation.release();
                audit("async-scheduler", "Async precheck was rejected during shutdown; no entity state was changed.", exception);
            }
        }
        else if (delay == 0L) {
            try {
                processOnOwnedRegion(entity, optimization);
            }
            finally {
                reservation.release();
            }
        }
        else {
            scheduleOwned(entity, delay, optimization, reservation);
        }
    }

    private void scheduleOwned(Entity entity, long delay, SpawnerKiller.OptimizationSettings optimization,
                               Reservation reservation) {
        if (closed.get()) {
            reservation.release();
            return;
        }
        boolean scheduled = entity.getScheduler().execute(plugin, () -> {
            try {
                processOnOwnedRegion(entity, optimization);
            }
            finally {
                reservation.release();
            }
        }, reservation::release, delay);
        if (!scheduled) {
            reservation.release();
        }
    }

    private Reservation reserve(Entity entity, SpawnerKiller.OptimizationSettings optimization) {
        UUID entityId = entity.getUniqueId();
        boolean duplicateTracked = optimization.collapseDuplicateSpawns();
        if (duplicateTracked && !pendingEntities.add(entityId)) {
            return new Reservation(entityId, null, null, true, true);
        }

        if (queued.incrementAndGet() > optimization.maxQueuedEntities()) {
            queued.decrementAndGet();
            if (duplicateTracked) {
                pendingEntities.remove(entityId);
            }
            return null;
        }

        Location location = entity.getLocation();
        RegionKey key = new RegionKey(location.getWorld().getUID(), location.getBlockX() >> 7,
                location.getBlockZ() >> 7);
        AtomicInteger regionCount = pendingRegions.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (regionCount.incrementAndGet() > optimization.maxPendingPerRegion()) {
            decrementRegion(key, regionCount);
            queued.decrementAndGet();
            if (duplicateTracked) {
                pendingEntities.remove(entityId);
            }
            return null;
        }
        return new Reservation(entityId, key, regionCount, duplicateTracked);
    }

    private void processOnOwnedRegion(Entity entity, SpawnerKiller.OptimizationSettings optimization) {
        if (closed.get() || !module.isOperational() || !Bukkit.isOwnedByCurrentRegion(entity)
                || !(entity instanceof LivingEntity livingEntity) || !entity.isValid() || entity.isDead()
                || !module.shouldProcess(entity.getType())) {
            return;
        }
        if (entity.getPersistentDataContainer().has(processedKey, PersistentDataType.BYTE)) {
            return;
        }
        if (!farmerAllows(entity.getLocation())) {
            return;
        }

        // Mark before mutation so two integrations cannot commit the same kill/drop transaction.
        entity.getPersistentDataContainer().set(processedKey, PersistentDataType.BYTE, (byte) 1);
        try {
            if (module.isCookFoods()) {
                entity.setFireTicks(Math.max(entity.getFireTicks(), 20));
            }
            if (!killWildStack(livingEntity, optimization)) {
                killVanilla(livingEntity);
            }
        }
        catch (Exception | LinkageError exception) {
            audit("process-failure", "SpawnerKiller rejected a failed entity transaction; entity was removed fail-closed.", exception);
            if (entity.isValid()) {
                entity.remove();
            }
        }
    }

    private boolean farmerAllows(Location location) {
        try {
            boolean hasFarmer = FarmerAPI.getFarmerManager().hasFarmer(location);
            if (!hasFarmer) {
                return !module.isRequireFarmer();
            }
            Object regionId = Main.getIntegration().getRegionID(location);
            Farmer farmer = FarmerManager.getFarmers().get(regionId);
            return farmer != null && farmer.getAttributeStatus("spawnerkiller");
        }
        catch (Exception | LinkageError exception) {
            audit("farmer-lookup", "SpawnerKiller denied a spawn transaction because Farmer state could not be verified.", exception);
            return false;
        }
    }

    private boolean killWildStack(LivingEntity entity, SpawnerKiller.OptimizationSettings optimization) {
        if (!wildStackerAvailable) {
            return false;
        }

        StackedEntity stacked;
        try {
            stacked = WildStackerAPI.getStackedEntity(entity);
        }
        catch (Exception | LinkageError exception) {
            audit("wildstacker-api", "WildStacker API was unavailable; falling back to the vanilla kill path.", exception);
            return false;
        }
        if (stacked == null) {
            return false;
        }

        int configuredLimit = optimization.enable() ? optimization.maxStackProcessAmount() : 1_000_000;
        int rawAmount = stacked.getStackAmount();
        if (rawAmount <= 0) {
            audit("invalid-stack", "WildStacker returned an invalid stack amount; removing the corrupt entity.", null);
            stacked.remove();
            return true;
        }
        int amount = Math.min(rawAmount, configuredLimit);
        if (amount != rawAmount) {
            audit("stack-clamped", "A hostile/invalid entity stack exceeded the configured safety ceiling and was clamped.", null);
        }

        EntityType entityType = entity.getType();
        if (optimization.enable() && optimization.asyncStackDrops()
                && scheduleAsyncStackDrops(entity, stacked, amount, entityType, optimization, 0)) {
            return true;
        }

        List<ItemStack> drops = entityType == EntityType.BLAZE
                ? Collections.emptyList()
                : cloneValidDrops(stacked.getDrops(0));
        commitWildStack(entity, stacked, amount, entityType, drops, optimization);
        return true;
    }

    private boolean scheduleAsyncStackDrops(LivingEntity entity, StackedEntity stacked, int amount,
                                            EntityType entityType,
                                            SpawnerKiller.OptimizationSettings optimization,
                                            int attempt) {
        if (closed.get() || asyncDropPlans.incrementAndGet() > optimization.maxQueuedEntities()) {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
            audit("drop-plan-overflow", "Async drop planning reached its bound; using the region fallback.", null);
            return false;
        }

        UUID stackedId = stacked.getUniqueId();
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    List<ItemStack> drops = entityType == EntityType.BLAZE
                            ? Collections.emptyList()
                            : cloneValidDrops(stacked.getDrops(0, amount));
                    scheduleDropCommit(entity, stackedId, amount, entityType, drops, optimization, attempt);
                }
                catch (Exception | LinkageError exception) {
                    scheduleFailedDropRemoval(entity, exception);
                }
            });
            return true;
        }
        catch (RuntimeException exception) {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
            audit("drop-plan-scheduler", "Async drop planning was rejected; using the region fallback.", exception);
            return false;
        }
    }

    private void scheduleDropCommit(LivingEntity entity, UUID stackedId, int plannedAmount,
                                    EntityType entityType, List<ItemStack> drops,
                                    SpawnerKiller.OptimizationSettings optimization, int attempt) {
        boolean scheduled = entity.getScheduler().execute(plugin, () -> {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
            if (closed.get() || !module.isOperational() || !entity.isValid() || entity.isDead()) {
                return;
            }
            try {
                StackedEntity current = WildStackerAPI.getStackedEntity(entity);
                if (current == null) {
                    return;
                }
                int currentAmount = current.getStackAmount();
                if (!stackedId.equals(current.getUniqueId()) || currentAmount != plannedAmount) {
                    if (attempt < 2 && currentAmount > 0) {
                        int safeAmount = Math.min(currentAmount, optimization.maxStackProcessAmount());
                        if (!scheduleAsyncStackDrops(entity, current, safeAmount, entity.getType(), optimization,
                                attempt + 1)) {
                            List<ItemStack> fallback = entity.getType() == EntityType.BLAZE
                                    ? Collections.emptyList()
                                    : cloneValidDrops(current.getDrops(0));
                            commitWildStack(entity, current, safeAmount, entity.getType(), fallback, optimization);
                        }
                    }
                    else {
                        audit("stale-drop-plan", "A repeatedly changing WildStacker entity was removed fail-closed.", null);
                        current.remove();
                    }
                    return;
                }
                commitWildStack(entity, current, plannedAmount, entityType, drops, optimization);
            }
            catch (Exception | LinkageError exception) {
                audit("drop-plan-commit", "An async drop plan failed validation and was removed fail-closed.", exception);
                if (entity.isValid()) {
                    entity.remove();
                }
            }
        }, () -> asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1)), 0L);
        if (!scheduled) {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private void scheduleFailedDropRemoval(LivingEntity entity, Throwable failure) {
        boolean scheduled = entity.getScheduler().execute(plugin, () -> {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
            audit("drop-plan-failure", "WildStacker drop calculation failed; the entity was removed fail-closed.", failure);
            if (!closed.get() && entity.isValid()) {
                entity.remove();
            }
        }, () -> asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1)), 0L);
        if (!scheduled) {
            asyncDropPlans.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private void commitWildStack(LivingEntity entity, StackedEntity stacked, int amount,
                                 EntityType entityType, List<ItemStack> drops,
                                 SpawnerKiller.OptimizationSettings optimization) {
        Location location = entity.getLocation().clone();
        stacked.remove();
        boolean batch = optimization.enable() && optimization.batchDrops();
        dropItems(location, drops, batch);
        createLegacyRewards(location, entityType, amount, batch);
    }

    private static List<ItemStack> cloneValidDrops(List<ItemStack> provided) {
        if (provided == null || provided.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemStack> drops = new ArrayList<>(provided.size());
        for (ItemStack item : provided) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                drops.add(item.clone());
            }
        }
        return drops;
    }

    private void killVanilla(LivingEntity entity) {
        Damageable damageable = entity;
        double lethalDamage = Math.max(1000.0D, damageable.getHealth() + 1.0D);
        damageable.damage(lethalDamage);
        if (entity.isValid() && !entity.isDead()) {
            damageable.setHealth(0.0D);
        }
        if (module.isRemoveMob() && entity.isValid()) {
            entity.remove();
        }
    }

    private void createLegacyRewards(Location location, EntityType entityType, int amount, boolean batchDrops) {
        String type = entityType.name();
        if (PASSIVE_TYPES.contains(type)) {
            spawnExperience(location, safeMultiply(ThreadLocalRandom.current().nextInt(1, 4), amount));
            return;
        }
        if (entityType == EntityType.GUARDIAN || entityType == EntityType.BLAZE) {
            spawnExperience(location, safeMultiply(10, amount));
            if (entityType == EntityType.BLAZE) {
                dropMaterial(location, Material.BLAZE_ROD, sampleCount(amount, 0.51D), batchDrops);
            }
            return;
        }
        if (entityType == EntityType.IRON_GOLEM || type.contains("SNOW_GOLEM")
                || entityType == EntityType.VILLAGER || type.contains("WANDERING_TRADER")) {
            return;
        }
        if (entityType == EntityType.SLIME || entityType == EntityType.MAGMA_CUBE) {
            spawnExperience(location, safeMultiply(3, amount));
            return;
        }
        if (type.contains("PHANTOM")) {
            dropMaterial(location, Material.PHANTOM_MEMBRANE, sampleCount(amount, 0.51D), batchDrops);
            spawnExperience(location, safeMultiply(5, amount));
            return;
        }
        if (type.contains("SPIDER")) {
            int twoStrings;
            int oneString;
            if (amount <= 4096) {
                twoStrings = 0;
                oneString = 0;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int index = 0; index < amount; index++) {
                    int roll = random.nextInt(100);
                    if (roll <= 33) {
                        twoStrings++;
                    }
                    else if (roll <= 66) {
                        oneString++;
                    }
                }
            }
            else {
                twoStrings = (int) Math.round(amount * 0.34D);
                oneString = (int) Math.round(amount * 0.33D);
            }
            dropMaterial(location, Material.STRING, safeAdd(safeMultiply(twoStrings, 2), oneString), batchDrops);
            dropMaterial(location, Material.SPIDER_EYE, sampleCount(amount, 0.21D), batchDrops);
        }
        spawnExperience(location, safeMultiply(5, amount));
    }

    private void dropItems(Location location, List<ItemStack> drops, boolean batch) {
        if (drops.isEmpty()) {
            return;
        }
        if (!batch) {
            for (ItemStack item : drops) {
                if (item.getAmount() <= item.getMaxStackSize()) {
                    location.getWorld().dropItemNaturally(location, item);
                }
                else {
                    dropMaterialStack(location, item, item.getAmount());
                }
            }
            return;
        }

        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : drops) {
            ItemStack target = null;
            for (ItemStack candidate : merged) {
                if (candidate.isSimilar(item)) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                target = item.clone();
                target.setAmount(0);
                merged.add(target);
            }
            target.setAmount(safeAdd(target.getAmount(), item.getAmount()));
        }
        for (ItemStack item : merged) {
            dropMaterialStack(location, item, item.getAmount());
        }
    }

    private void dropMaterial(Location location, Material material, int amount, boolean batch) {
        if (amount <= 0) {
            return;
        }
        if (!batch && amount <= 1024) {
            for (int index = 0; index < amount; index++) {
                location.getWorld().dropItemNaturally(location, new ItemStack(material));
            }
            return;
        }
        dropMaterialStack(location, new ItemStack(material), amount);
    }

    private void dropMaterialStack(Location location, ItemStack template, int amount) {
        int maximum = Math.max(1, template.getMaxStackSize());
        int safeAmount = Math.min(amount, maximum * 4096);
        if (safeAmount != amount) {
            audit("drop-ceiling", "A corrupt drop plan exceeded the item-entity safety ceiling and was clamped.", null);
        }
        int remaining = safeAmount;
        while (remaining > 0) {
            int stackSize = Math.min(maximum, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(stackSize);
            location.getWorld().dropItemNaturally(location, stack);
            remaining -= stackSize;
        }
    }

    private void spawnExperience(Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    private static int sampleCount(int trials, double probability) {
        if (trials <= 0) {
            return 0;
        }
        if (trials > 4096) {
            double expected = trials * probability;
            int whole = (int) Math.floor(expected);
            return whole + (ThreadLocalRandom.current().nextDouble() < expected - whole ? 1 : 0);
        }
        int successes = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < trials; index++) {
            if (random.nextDouble() < probability) {
                successes++;
            }
        }
        return successes;
    }

    private static int safeMultiply(int left, int right) {
        long result = (long) left * right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, result));
    }

    private static int safeAdd(int left, int right) {
        long result = (long) left + right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, result));
    }

    private void audit(String category, String message, Throwable throwable) {
        long now = System.currentTimeMillis();
        long interval = module.getOptimizationSettings().auditLogRateLimitMs();
        AtomicBoolean shouldLog = new AtomicBoolean();
        lastWarnings.compute(category, (ignored, previous) -> {
            if (previous == null || now - previous >= interval) {
                shouldLog.set(true);
                return now;
            }
            return previous;
        });
        if (!shouldLog.get()) {
            return;
        }
        if (throwable == null) {
            plugin.getLogger().warning(message);
        }
        else {
            plugin.getLogger().log(Level.WARNING, message, throwable);
        }
    }

    private void decrementRegion(RegionKey key, AtomicInteger count) {
        if (count.decrementAndGet() <= 0) {
            pendingRegions.remove(key, count);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        pendingEntities.clear();
        pendingRegions.clear();
        queued.set(0);
        asyncDropPlans.set(0);
        lastWarnings.clear();
    }

    private record RegionKey(UUID worldId, int regionX, int regionZ) {
    }

    private final class Reservation {
        private final UUID entityId;
        private final RegionKey regionKey;
        private final AtomicInteger regionCount;
        private final boolean duplicateTracked;
        private final AtomicBoolean released = new AtomicBoolean();
        private final boolean duplicate;

        private Reservation(UUID entityId, RegionKey regionKey, AtomicInteger regionCount,
                            boolean duplicateTracked) {
            this(entityId, regionKey, regionCount, duplicateTracked, false);
        }

        private Reservation(UUID entityId, RegionKey regionKey, AtomicInteger regionCount,
                            boolean duplicateTracked, boolean duplicate) {
            this.entityId = entityId;
            this.regionKey = regionKey;
            this.regionCount = regionCount;
            this.duplicateTracked = duplicateTracked;
            this.duplicate = duplicate;
        }

        private void release() {
            if (duplicate || !released.compareAndSet(false, true)) {
                return;
            }
            if (duplicateTracked) {
                pendingEntities.remove(entityId);
            }
            decrementRegion(regionKey, regionCount);
            queued.updateAndGet(value -> Math.max(0, value - 1));
        }
    }
}

package com.gemini.veintreebreaker;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class VeinTreeBreaker extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<Material> LOGS = new HashSet<>();
    private final Set<Material> ORES = new HashSet<>();
    private final Set<Material> LEAVES = new HashSet<>();
    private final Set<Material> ALLOWED_AXES = new HashSet<>();
    private final Set<Material> ALLOWED_PICKAXES = new HashSet<>();
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Set<UUID> autoPickupPlayers = new HashSet<>();
    
    private LanguageManager languageManager;
    private UpdateManager updateManager;
    private PlacedBlockTracker placedBlockTracker;
    
    // --- SETTINGS ---
    private boolean treesEnabled, veinsEnabled;
    private int treeMaxBlocks, veinMaxBlocks;
    private boolean treeReplant, treeBreakLeaves, treeRequireLeaves;
    private int treeLeafRadius;
    private int treeDelay, treeSpeed, veinDelay, veinSpeed;
    private boolean treeSound, treeParticles, veinSound, veinParticles;
    private String treeFinishSound, veinFinishSound;
    private boolean stopAtLava;
    private boolean autoUpdate;
    private boolean requireSneak, allowCreative;
    private boolean defaultToggleState;
    private boolean autoPickup;
    private List<String> allowedWorlds;

    // Balance Settings
    private boolean durabilityEnabled;
    private double durabilityCost;
    private int safetyThreshold;
    private boolean hungerEnabled;
    private float exhaustionCost;
    private boolean actionBarEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        updateManager = new UpdateManager(this);
        
        placedBlockTracker = new PlacedBlockTracker(this);
        getServer().getPluginManager().registerEvents(placedBlockTracker, this);
        
        loadConfigValues();
        
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("veintreebreaker").setExecutor(this);
        
        getLogger().info(languageManager.getRawMessage("plugin-enabled").replace("%version%", getDescription().getVersion()));

        if (autoUpdate) {
            updateManager.checkForUpdate(getServer().getConsoleSender(), true);
        }
        
        if (autoPickup) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                autoPickupPlayers.add(p.getUniqueId());
            }
        }
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public File getPluginFile() {
        return super.getFile();
    }
    
    public PlacedBlockTracker getPlacedBlockTracker() {
        return placedBlockTracker;
    }

    public boolean isTrackedMaterial(Material material) {
        return LOGS.contains(material) || ORES.contains(material) || LEAVES.contains(material);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        
        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("veintreebreaker.admin")) {
                sender.sendMessage(languageManager.getMessage("no-permission"));
                return true;
            }
            loadConfigValues();
            languageManager.loadLanguages();
            sender.sendMessage(languageManager.getMessage("config-reloaded"));
            return true;
        }
        
        if (sub.equals("update")) {
            if (!sender.hasPermission("veintreebreaker.admin")) {
                sender.sendMessage(languageManager.getMessage("no-permission"));
                return true;
            }
            updateManager.checkForUpdate(sender, false);
            return true;
        }
        
        if (sub.equals("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(languageManager.getMessage("only-players"));
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (disabledPlayers.contains(uuid)) {
                disabledPlayers.remove(uuid);
                player.sendMessage(languageManager.getMessage("toggle-on"));
            } else {
                disabledPlayers.add(uuid);
                player.sendMessage(languageManager.getMessage("toggle-off"));
            }
            return true;
        }

        if (sub.equals("autopickup")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(languageManager.getMessage("only-players"));
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            
            boolean newState;
            if (autoPickupPlayers.contains(uuid)) {
                autoPickupPlayers.remove(uuid);
                newState = false;
            } else {
                autoPickupPlayers.add(uuid);
                newState = true;
            }
            
            String msgKey = newState ? "autopickup-toggle-on" : "autopickup-toggle-off";
            sender.sendMessage(languageManager.getMessage(msgKey));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m      &r &2&lVeinTreeBreaker &8&m      "));
        sender.sendMessage("");

        String toggleStatus = "";
        String pickupStatus = "";
        if (sender instanceof Player) {
            Player p = (Player) sender;
            toggleStatus = disabledPlayers.contains(p.getUniqueId()) ? " &8(&cKAPALI&8)" : " &8(&aA IK&8)";
            pickupStatus = autoPickupPlayers.contains(p.getUniqueId()) ? " &8(&aA IK&8)" : " &8(&cKAPALI&8)";
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &e/vtb toggle &8 zelli ar/kapat r." + toggleStatus));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &e/vtb autopickup &8  &7Yerden toplamay ar/kapat r." + pickupStatus));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &e/vtb help &8  &7Bu men sterir."));
        
        if (sender.hasPermission("veintreebreaker.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &c/vtb reload &8  &7Ayarlar  yeniler."));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &c/vtb update &8  &7G ncellemeleri kontrol eder."));
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m                          "));
    }

    public void loadConfigValues() {
        reloadConfig();
        FileConfiguration config = getConfig();

        requireSneak = config.getBoolean("settings.require-sneak");
        allowCreative = config.getBoolean("settings.allow-creative");
        defaultToggleState = config.getBoolean("settings.default-toggle-state");
        autoPickup = config.getBoolean("settings.auto-pickup", false);
        autoUpdate = config.getBoolean("settings.auto-update", true);
        allowedWorlds = config.getStringList("settings.allowed-worlds");

        durabilityEnabled = config.getBoolean("gameplay.durability.enabled");
        durabilityCost = config.getDouble("gameplay.durability.cost-per-block");
        safetyThreshold = config.getInt("gameplay.durability.safety-threshold");
        hungerEnabled = config.getBoolean("gameplay.hunger.enabled");
        exhaustionCost = (float) config.getDouble("gameplay.hunger.exhaustion-per-block");
        actionBarEnabled = config.getBoolean("gameplay.messages.action-bar");

        treesEnabled = config.getBoolean("trees.enabled");
        treeMaxBlocks = config.getInt("trees.max-blocks");
        treeReplant = config.getBoolean("trees.auto-replant");
        treeRequireLeaves = config.getBoolean("trees.require-leaves", true);
        treeBreakLeaves = config.getBoolean("trees.break-leaves");
        treeLeafRadius = config.getInt("trees.leaf-radius");
        treeDelay = config.getInt("trees.animation.delay-ticks");
        treeSpeed = config.getInt("trees.animation.blocks-per-tick");
        treeSound = config.getBoolean("trees.animation.sound-enabled");
        treeParticles = config.getBoolean("trees.animation.particles-enabled");
        treeFinishSound = config.getString("trees.animation.finish-sound", "ENTITY_PLAYER_LEVELUP");

        parseMaterials(config.getStringList("trees.log-materials"), LOGS, "LOGS");
        parseTools(config.getStringList("trees.allowed-tools"), ALLOWED_AXES, "_AXE");
        
        LEAVES.clear();
        for (Material m : Material.values()) {
             if (Tag.LEAVES.isTagged(m) || m.name().contains("LEAVES") || m.name().contains("WART_BLOCK") || m.name().equals("SHROOMLIGHT")) {
                 LEAVES.add(m);
             }
        }

        veinsEnabled = config.getBoolean("veins.enabled");
        veinMaxBlocks = config.getInt("veins.max-blocks");
        veinDelay = config.getInt("veins.animation.delay-ticks");
        veinSpeed = config.getInt("veins.animation.blocks-per-tick");
        veinSound = config.getBoolean("veins.animation.sound-enabled");
        veinParticles = config.getBoolean("veins.animation.particles-enabled");
        veinFinishSound = config.getString("veins.animation.finish-sound", "BLOCK_AMETHYST_BLOCK_CHIME");
        stopAtLava = config.getBoolean("veins.stop-at-lava", true);

        parseMaterials(config.getStringList("veins.ore-materials"), ORES, "ORES");
        parseTools(config.getStringList("veins.allowed-tools"), ALLOWED_PICKAXES, "_PICKAXE");
    }

    private void parseMaterials(List<String> list, Set<Material> targetSet, String specialKey) {
        targetSet.clear();
        for (String key : list) {
            if (key.equalsIgnoreCase("ALL_" + specialKey)) {
                for (Material m : Material.values()) {
                    if (specialKey.equals("LOGS") && Tag.LOGS.isTagged(m)) targetSet.add(m);
                    if (specialKey.equals("ORES") && (m.name().endsWith("_ORE") || m.name().equals("ANCIENT_DEBRIS"))) targetSet.add(m);
                }
            } else {
                Material m = Material.getMaterial(key.toUpperCase());
                if (m != null) targetSet.add(m);
            }
        }
    }

    private void parseTools(List<String> list, Set<Material> targetSet, String suffix) {
        targetSet.clear();
        for (String key : list) {
            if (key.equalsIgnoreCase("ALL")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith(suffix)) targetSet.add(m);
                }
            } else {
                Material m = Material.getMaterial(key.toUpperCase());
                if (m != null) targetSet.add(m);
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (autoPickup) {
            autoPickupPlayers.add(player.getUniqueId());
        }
        
        if (player.hasPermission("veintreebreaker.admin") && updateManager.isUpdateAvailable()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(languageManager.getMessage("update-found").replace("%version%", updateManager.getLatestVersion()));
                }
            }.runTaskLater(this, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(player.getWorld().getName())) return;
        if (!allowCreative && player.getGameMode() == GameMode.CREATIVE) return;
        if (requireSneak && !player.isSneaking()) return;
        if (disabledPlayers.contains(player.getUniqueId())) return;

        Block startBlock = event.getBlock();
        
        if (placedBlockTracker.isPlayerPlaced(startBlock)) return;

        Material startType = startBlock.getType();
        ItemStack tool = player.getInventory().getItemInMainHand();

        boolean isTree = LOGS.contains(startType);
        boolean isVein = ORES.contains(startType);

        if (!isTree && !isVein) return;
        if (isTree && !treesEnabled) return;
        if (isVein && !veinsEnabled) return;
        if (isTree && !ALLOWED_AXES.contains(tool.getType())) return;
        if (isVein && !ALLOWED_PICKAXES.contains(tool.getType())) return;

        if (event.isCancelled()) return;

        boolean playerAutoPickup = autoPickupPlayers.contains(player.getUniqueId());

        List<Block> connectedBlocks = findConnected(startBlock, startType, isTree);
        
        if (isTree) {
            Set<Block> foundLeaves = new HashSet<>();
            for (Block log : connectedBlocks) {
                findLeavesAround(log, foundLeaves);
            }
            
            if (treeRequireLeaves && foundLeaves.isEmpty()) return;
            
            if (treeBreakLeaves) {
                connectedBlocks.addAll(foundLeaves);
            }
        }

        if (playerAutoPickup) {
            event.setDropItems(false);
            for (ItemStack drop : startBlock.getDrops(tool, player)) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(drop);
                for (ItemStack l : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), l);
                }
            }
            if (isVein) {
                int exp = getExpAmount(startType);
                if (exp > 0 && (tool == null || tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SILK_TOUCH) == 0)) {
                    player.giveExp(exp);
                }
            }
        }

        connectedBlocks.remove(startBlock);
        if (connectedBlocks.isEmpty()) return;

        if (isTree) {
            if (treeReplant) handleReplant(startBlock, startType);
            connectedBlocks.sort(Comparator.comparingInt(Block::getY).thenComparingDouble(b -> b.getLocation().distanceSquared(startBlock.getLocation())));
            animateSequentialBreak(player, connectedBlocks, tool, treeDelay, treeSpeed, treeSound, treeParticles, true, playerAutoPickup);
        } else {
            connectedBlocks.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(startBlock.getLocation())));
            animateSequentialBreak(player, connectedBlocks, tool, veinDelay, veinSpeed, veinSound, veinParticles, false, playerAutoPickup);
        }
    }

    private void findLeavesAround(Block center, Set<Block> leafResult) {
        for (int x = -treeLeafRadius; x <= treeLeafRadius; x++) {
            for (int y = -treeLeafRadius; y <= treeLeafRadius; y++) {
                for (int z = -treeLeafRadius; z <= treeLeafRadius; z++) {
                    Block rel = center.getRelative(x, y, z);
                    if (leafResult.contains(rel)) continue;
                    
                    if (LEAVES.contains(rel.getType()) && !placedBlockTracker.isPlayerPlaced(rel)) { 
                         leafResult.add(rel);
                    }
                }
            }
        }
    }

    private void handleReplant(Block block, Material logType) {
        Material saplingType = getSapling(logType);
        if (saplingType == null) return;

        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (!Tag.DIRT.isTagged(blockBelow.getType()) && blockBelow.getType() != Material.MOSS_BLOCK) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                block.setType(saplingType);
                if (treeParticles) block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3);
            }
        }.runTaskLater(this, 15L);
    }

    private List<Block> findConnected(Block start, Material target, boolean isTree) {
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        List<Block> result = new ArrayList<>();

        visited.add(start);
        queue.add(start);
        result.add(start);
        
        int max = isTree ? treeMaxBlocks : veinMaxBlocks;

        while (!queue.isEmpty() && result.size() < max) {
            Block current = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block relative = current.getRelative(x, y, z);
                        
                        if (!visited.contains(relative) && relative.getType() == target && !placedBlockTracker.isPlayerPlaced(relative)) {
                            visited.add(relative);
                            queue.add(relative);
                            result.add(relative);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void animateSequentialBreak(Player player, List<Block> blocks, ItemStack tool, int delay, int speed, boolean sound, boolean particles, boolean isTree, boolean playerAutoPickup) {
        new BukkitRunnable() {
            int index = 0;
            int coreBrokenCount = 0;
            double durabilityConsumed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || index >= blocks.size()) {
                    finish();
                    return;
                }

                for (int i = 0; i < speed; i++) {
                    if (index >= blocks.size()) break;
                    
                    Block block = blocks.get(index++);
                    Material type = block.getType();
                    boolean isLeaf = LEAVES.contains(type);

                    if (!isTree && stopAtLava && isTouchingLava(block)) {
                        continue; 
                    }

                    if (!isLeaf) {
                        if (durabilityEnabled && player.getGameMode() != GameMode.CREATIVE) {
                            if (checkAndDamageTool(player, tool)) {
                                player.sendMessage(languageManager.getMessage("tool-breaking"));
                                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                                this.cancel();
                                return;
                            }
                            durabilityConsumed += durabilityCost;
                        }

                        if (hungerEnabled && player.getGameMode() != GameMode.CREATIVE) {
                             player.setExhaustion(player.getExhaustion() + exhaustionCost);
                        }
                        coreBrokenCount++;
                    }

                    if (sound) block.getWorld().playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 0.8f, 1.4f);
                    if (particles) block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, block.getBlockData());

                    if (playerAutoPickup) {
                        Collection<ItemStack> drops = block.getDrops(tool, player);
                        for (ItemStack drop : drops) {
                            Map<Integer, ItemStack> left = player.getInventory().addItem(drop);
                            for (ItemStack l : left.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), l);
                            }
                        }
                        if (!isTree && !isLeaf) {
                            int exp = getExpAmount(type);
                            if (exp > 0 && (tool == null || tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SILK_TOUCH) == 0)) {
                                player.giveExp(exp);
                            }
                        }
                        block.setType(Material.AIR);
                    } else {
                        block.breakNaturally(tool);
                    }
                }
                
                if (actionBarEnabled) {
                    String msgKey = isTree ? "actionbar-tree" : "actionbar-vein";
                    String msg = languageManager.getMessage(msgKey)
                            .replace("%blocks%", String.valueOf(coreBrokenCount))
                            .replace("%durability%", String.valueOf((int)durabilityConsumed));
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                }
            }
            
            private void finish() {
                this.cancel();
                if (coreBrokenCount > 0) {
                    String finishSound = isTree ? treeFinishSound : veinFinishSound;
                    try {
                        Sound s = Sound.valueOf(finishSound);
                        player.getWorld().playSound(player.getLocation(), s, 0.5f, 1.0f);
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(this, delay, delay);
    }
    
    private int getExpAmount(Material type) {
        switch (type.name()) {
            case "COAL_ORE":
            case "DEEPSLATE_COAL_ORE":
                return (int) (Math.random() * 3);
            case "NETHER_GOLD_ORE":
                return (int) (Math.random() * 2);
            case "DIAMOND_ORE":
            case "DEEPSLATE_DIAMOND_ORE":
            case "EMERALD_ORE":
            case "DEEPSLATE_EMERALD_ORE":
                return 3 + (int) (Math.random() * 5);
            case "LAPIS_ORE":
            case "DEEPSLATE_LAPIS_ORE":
                return 2 + (int) (Math.random() * 4);
            case "REDSTONE_ORE":
            case "DEEPSLATE_REDSTONE_ORE":
                return 1 + (int) (Math.random() * 5);
            case "NETHER_QUARTZ_ORE":
                return 2 + (int) (Math.random() * 4);
            default:
                return 0;
        }
    }

    private boolean isTouchingLava(Block block) {
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            if (block.getRelative(face).getType() == Material.LAVA) return true;
        }
        return false;
    }
    
    private boolean checkAndDamageTool(Player player, ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable d = (Damageable) meta;
            int max = tool.getType().getMaxDurability();
            int current = d.getDamage();
            
            if (max > 0 && (max - current) <= safetyThreshold) return true;
            
            int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
            if (unbreakingLevel == 0 || Math.random() < (1.0 / (unbreakingLevel + 1))) {
                d.setDamage(current + 1);
                tool.setItemMeta((ItemMeta) d);
            }
        }
        return false;
    }

    private Material getSapling(Material log) {
        String name = log.name().replace("STRIPPED_", "").replace("_LOG", "").replace("_WOOD", "").replace("_STEM", "").replace("_HYPHAE", "");
        if (name.equals("CRIMSON")) return Material.CRIMSON_FUNGUS;
        if (name.equals("WARPED")) return Material.WARPED_FUNGUS;
        if (name.equals("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        if (name.equals("CHERRY")) return Material.CHERRY_SAPLING;
        return Material.getMaterial(name + "_SAPLING");
    }
}
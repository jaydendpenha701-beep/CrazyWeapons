package com.github.crazyweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrazyWeapons extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Long> lightningSwordCooldowns = new HashMap<>();
    private final Map<UUID, Long> fireWandCooldowns = new HashMap<>();
    private final Map<UUID, Long> lifestealBladeCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("cwgive").setExecutor(this);
        getLogger().info("CrazyWeapons has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CrazyWeapons has been disabled!");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        Component displayName = meta.displayName();
        if (displayName == null) return;

        String legacyName = LegacyComponentSerializer.legacySection().serialize(displayName);

        // Lightning Sword: Material.DIAMOND_SWORD, Name "§bLightning Sword"
        if (item.getType() == Material.DIAMOND_SWORD && legacyName.equals("§bLightning Sword")) {
            if (isCooldownActive(attacker.getUniqueId(), lightningSwordCooldowns, 3000)) {
                attacker.sendMessage(Component.text("Wait 3 seconds before using this again!", NamedTextColor.RED));
                return;
            }
            lightningSwordCooldowns.put(attacker.getUniqueId(), System.currentTimeMillis());

            Entity target = event.getEntity();
            target.getWorld().strikeLightning(target.getLocation());
            event.setDamage(event.getDamage() + 4.0);
        }

        // Lifesteal Blade: Material.NETHERITE_SWORD, Name "§cLifesteal Blade"
        if (item.getType() == Material.NETHERITE_SWORD && legacyName.equals("§cLifesteal Blade")) {
            if (isCooldownActive(attacker.getUniqueId(), lifestealBladeCooldowns, 2000)) {
                return; // Silently fail cooldown for lifesteal
            }
            lifestealBladeCooldowns.put(attacker.getUniqueId(), System.currentTimeMillis());

            double currentHealth = attacker.getHealth();
            AttributeInstance maxHealthAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0;

            attacker.setHealth(Math.min(maxHealth, currentHealth + 4.0));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        Component displayName = meta.displayName();
        if (displayName == null) return;

        String legacyName = LegacyComponentSerializer.legacySection().serialize(displayName);

        // Fire Wand: Material.BLAZE_ROD, Name "§6Fire Wand"
        if (item.getType() == Material.BLAZE_ROD && legacyName.equals("§6Fire Wand")) {
            if (isCooldownActive(player.getUniqueId(), fireWandCooldowns, 2000)) {
                player.sendMessage(Component.text("Wait 2 seconds before shooting again!", NamedTextColor.RED));
                return;
            }
            fireWandCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

            SmallFireball fireball = player.launchProjectile(SmallFireball.class);
            fireball.setYield(0F); // No block damage
            fireball.setIsIncendiary(false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lightningSwordCooldowns.remove(uuid);
        fireWandCooldowns.remove(uuid);
        lifestealBladeCooldowns.remove(uuid);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /cwgive <lightning|fire|lifesteal>", NamedTextColor.RED));
            return true;
        }

        ItemStack item;
        switch (args[0].toLowerCase()) {
            case "lightning" -> item = createCustomItem(Material.DIAMOND_SWORD, "§bLightning Sword");
            case "fire" -> item = createCustomItem(Material.BLAZE_ROD, "§6Fire Wand");
            case "lifesteal" -> item = createCustomItem(Material.NETHERITE_SWORD, "§cLifesteal Blade");
            default -> {
                player.sendMessage(Component.text("Invalid weapon type!", NamedTextColor.RED));
                return true;
            }
        }

        player.getInventory().addItem(item);
        player.sendMessage(Component.text("You have been given a crazy weapon!", NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createCustomItem(Material material, String legacyName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(legacyName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isCooldownActive(UUID uuid, Map<UUID, Long> cooldowns, long cooldownMillis) {
        if (cooldowns.containsKey(uuid)) {
            long timeElapsed = System.currentTimeMillis() - cooldowns.get(uuid);
            return timeElapsed < cooldownMillis;
        }
        return false;
    }
}

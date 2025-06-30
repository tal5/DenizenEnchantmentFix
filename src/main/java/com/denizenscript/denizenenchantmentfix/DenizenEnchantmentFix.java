package com.denizenscript.denizenenchantmentfix;

import com.denizenscript.denizen.events.bukkit.ScriptReloadEvent;
import com.denizenscript.denizen.scripts.containers.core.EnchantmentScriptContainer;
import com.denizenscript.denizencore.scripts.ScriptRegistry;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class DenizenEnchantmentFix extends JavaPlugin implements Listener {

    boolean hadError, is1_21;
    List<String> enchantmentScriptIds = new ArrayList<>();

    public DenizenEnchantmentFix(boolean hadError, boolean is1_21) {
        this.hadError = hadError;
        this.is1_21 = is1_21;
    }

    @Override
    public void onEnable() {
        if (hadError) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (is1_21) {
            getServer().getPluginManager().registerEvents(this, this);
        }
    }

    @Override
    public void onDisable() {
        if (!is1_21) {
            return;
        }
        YamlConfiguration enchantmentScriptData = new YamlConfiguration();
        enchantmentScriptData.set("enchantment_ids", enchantmentScriptIds);
        try {
            enchantmentScriptData.save(new File(getDataFolder(), "enchant_scripts.yml"));
        }
        catch (IOException e) {
            getSLF4JLogger().error("Failed to save 'enchant_scripts.yml'.", e);
        }
    }

    @EventHandler
    public void on(ScriptReloadEvent event) {
        getServer().reloadData();
        enchantmentScriptIds.clear();
        for (ScriptContainer scriptContainer : ScriptRegistry.scriptContainers.values()) {
            if (scriptContainer instanceof EnchantmentScriptContainer enchantmentScript && scriptContainer.shouldEnable()) {
                enchantmentScriptIds.add(enchantmentScript.id);
            }
        }
    }
}

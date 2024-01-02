package troy.autofish.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import troy.autofish.FabricModAutofish;
import troy.autofish.config.Config;

public class FishCountScreenBuilder {

    public static Screen buildScreen(FabricModAutofish modAutofish, Screen parentScreen) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parentScreen)
                .setTitle(Text.translatable("options.fishcount.title"))
                .transparentBackground()
                .setDoesConfirmSave(false); // Set to false if there's nothing to save

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory configCat = builder.getOrCreateCategory(Text.translatable("options.fishcount.config"));
    // Retrieve and sort fish counts
    Map<String, Integer> fishCounts = new TreeMap<>(modAutofish.getAutofish().getFishCounts()); // TreeMap sorts by keys

    if (fishCounts.isEmpty()) {
        configCat.addEntry(entryBuilder.startTextDescription(Text.of("No fish caught")).build());
    } else {
        // Add an entry for each fish
        for (Map.Entry<String, Integer> entry : fishCounts.entrySet()) {
            String entryText = entry.getKey() + ": " + entry.getValue(); // Modify as needed to include rarity
            configCat.addEntry(entryBuilder.startTextDescription(Text.of(entryText)).build());
        }
    }

        return builder.build();
    }


}
package irvyn.autofish.gui;

import java.util.Map;
import java.util.Map.Entry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import irvyn.autofish.FabricModAutofish;

public class FishCountScreenBuilder {

    public static Screen buildScreen(FabricModAutofish modAutofish, Screen parentScreen) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parentScreen)
                .setTitle(Text.translatable("options.fish_count.title"))
                .transparentBackground()
                .setDoesConfirmSave(false); // Set to false if there's nothing to save

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory configCat = builder.getOrCreateCategory(Text.translatable("options.fishcount.config"));
        // Retrieve and sort fish counts
        Map<String, Map<String, Integer>> fishCounts =  modAutofish.getAutofish().getFishCounts(); // TreeMap sorts by keys
    
        if (fishCounts.isEmpty()) {
            configCat.addEntry(entryBuilder.startTextDescription(Text.of("No fish caught")).build());
        } else {
            // Add an entry for each fish
            for (Entry<String, Map<String, Integer>> entry : fishCounts.entrySet()) {
                String fishName = entry.getKey();
                Map<String, Integer> rarityAndCount = entry.getValue();
            
                for (Entry<String, Integer> innerEntry : rarityAndCount.entrySet()) {
                    String rarity = innerEntry.getKey();
                    Integer count = innerEntry.getValue();
            
                    Formatting colorFormatting = convertHexToFormatting(modAutofish.getAutofish().getColourFromRarity(rarity));

                    // Create the Text for the entry with the color formatting
                    Text entryText = Text.literal(colorFormatting + fishName + " (" + rarity + "): " + count);

                    configCat.addEntry(entryBuilder.startTextDescription(entryText).build());
                }
            }
        }

            return builder.build();
    }

    // Method to convert hex color code to Minecraft Formatting
    // This is a simple example. You might need to adjust this based on your color codes
    private static Formatting convertHexToFormatting(String hexColor) {
        // #D9941D - Bronze
        // #E4E1DB - Silver
        // #FCDB2B - Gold
        // #18CDE4 - Diamond
        // #9B85B0 - Platinum
        // #9400D3 - Mythical
        switch(hexColor) {
            case "#D9941D": return Formatting.GOLD;
            case "#E4E1DB": return Formatting.GRAY;
            case "#FCDB2B": return Formatting.YELLOW;
            case "#18CDE4": return Formatting.AQUA;
            case "#9B85B0": return Formatting.LIGHT_PURPLE;
            case "#9400D3": return Formatting.DARK_PURPLE;

            // Add cases for different colors
            default: return Formatting.BLACK; // Default color
        }
    }


}
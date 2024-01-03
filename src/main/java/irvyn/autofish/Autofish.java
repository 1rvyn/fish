package irvyn.autofish;

import irvyn.autofish.monitor.FishMonitorMP;
import irvyn.autofish.monitor.FishMonitorMPMotion;
import irvyn.autofish.monitor.FishMonitorMPSound;
import irvyn.autofish.scheduler.ActionType;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Autofish {

    private MinecraftClient client;
    private FabricModAutofish modAutofish;
    private FishMonitorMP fishMonitorMP;

    private boolean hookExists = false;
    private boolean alreadyAlertOP = false;
    private boolean alreadyPassOP = false;
    private boolean moved = false;
    private float oldPitch = 0;
    private float oldYaw = 0;
    private long hookRemovedAt = 0L;
    private int fishCount = 0;

    public long timeMillis = 0L;

    private static final int FISH_COUNT_THRESHOLD = 10; 
    private Map<String, Map<String, Integer>> fishCounts = new HashMap<>();
    //private Map<String, String> fishRarities = new HashMap<>();

    /**
     * Returns a copy of the fishCounts map
     * to be used by the GUI screen
     * We return a copy to prevent the GUI from modifying the original map
     * @return
     */
    public Map<String, Map<String, Integer>> getFishCounts() {
        // return new HashMap<String, Map<String, Integer>>(); 
        return new HashMap<String, Map<String, Integer>>(fishCounts);
    }

     /**
     * Returns a copy of the fishRarities map
     * to be used by the GUI screen
     * We return a copy to prevent the GUI from modifying the original map
     * @return
     */
    // public Map<String, String> getFishRarity() {
    //     return new HashMap<String, String>(fishRarities); 
    // }
    




    public Autofish(FabricModAutofish modAutofish) {
        this.modAutofish = modAutofish;
        this.client = MinecraftClient.getInstance();
        setDetection();

        //Initiate the repeating action for persistent mode casting
        modAutofish.getScheduler().scheduleRepeatingAction(10000, () -> {
            if(!modAutofish.getConfig().isPersistentMode()) return;
            if(!isHoldingFishingRod()) return;
            if(hookExists) return;
            if(modAutofish.getScheduler().isRecastQueued()) return;

            useRod();
        });
    }

    public void tick(MinecraftClient client) {

        if (client.world != null && client.player != null && modAutofish.getConfig().isAutofishEnabled()) {

           timeMillis = Util.getMeasuringTimeMs(); //update current working time for this tick

            if (isHoldingFishingRod()) {
                if (client.player.fishHook != null) {
                    hookExists = true;
                    //MP catch listener
                    if (shouldUseMPDetection()) {//multiplayer only, send tick event to monitor
                        fishMonitorMP.hookTick(this, client, client.player.fishHook);
                    }
                } else {
                    removeHook();
                }
            } else { //not holding fishing rod
                removeHook();
            }
        }
    }

    /**
     * Callback from mixin for the catchingFish method of the EntityFishHook
     * for singleplayer detection only
     */
    public void tickFishingLogic(Entity owner, int ticksCatchable) {
        //This callback will come from the Server thread. Use client.execute() to run this action in the Render thread
        client.execute(() -> {
            if (modAutofish.getConfig().isAutofishEnabled() && !shouldUseMPDetection()) {
                //null checks for sanity
                if (client.player != null && client.player.fishHook != null) {
                    //hook is catchable and player is correct
                    if (ticksCatchable > 0 && owner.getUuid().compareTo(client.player.getUuid()) == 0) {
                        catchFish();
                    }
                }
            }
        });
    }

    /**
     * Callback from mixin when sound and motion packets are received
     * For multiplayer detection only
     */
    public void handlePacket(Packet<?> packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (shouldUseMPDetection()) {
                fishMonitorMP.handlePacket(this, packet, client);
            }
        }
    }


    /**
     * Callback from mixin when chat packets are received
     * For multiplayer detection only
     */
    public void handleChat(GameMessageS2CPacket packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (!client.isInSingleplayer()) {
                if (isHoldingFishingRod()) {
                    //check that either the hook exists, or it was just removed
                    //this prevents false casts if we are holding a rod but not fishing
                    if (hookExists || (timeMillis - hookRemovedAt < 2000)) {
                        //make sure there is actually something there in the regex field
                        if (org.apache.commons.lang3.StringUtils.deleteWhitespace(modAutofish.getConfig().getClearLagRegex()).isEmpty())
                            return;
                        //check if it matches
                        Matcher matcher = Pattern.compile(modAutofish.getConfig().getClearLagRegex(), Pattern.CASE_INSENSITIVE).matcher(StringHelper.stripTextFormat(packet.content().getString()));
                        
                        if (matcher.find()) {
                            queueRecast();
                        }

                        handleCustomFish(packet.content());
                    }
                }
            }
        }
    }

    public void handleCustomFish(Text textComponent) {
        // Extracting the fish name and rarity from the formatted Text component
        String rawText = textComponent.getString();
        Matcher fishNameMatcher = Pattern.compile("You caught a (.*?)!").matcher(rawText);
        if (fishNameMatcher.find()) {
            String fishName = fishNameMatcher.group(1);

            // Assuming the color code appears just before the fish name in the chat message
            // We retrieve the style of the text component that contains the fish name
            String rarity = getFishRarity(textComponent); // Default rarity
            if (rarity != null) {
                // here we will add the entry as we know the fishname and rarity
                fishCounts.putIfAbsent(fishName, new HashMap<>());
                fishCounts.get(fishName).put(rarity, fishCounts.get(fishName).getOrDefault(rarity, 0) + 1);
                System.out.println("Caught " + fishName + " with rarity " + rarity);
                System.out.println(fishCounts);
            }
        }
    }

    public String getFishRarity(Text textComponent) {
        String text = textComponent.toString();

        // Regular expression to find color codes
        Pattern pattern = Pattern.compile("color=(#[0-9A-Fa-f]{6})");
        Matcher matcher = pattern.matcher(text);
        // Map of color codes to rarities
        Map<String, String> colorToRarity = new HashMap<>();
        colorToRarity.put("#D9941D", "Bronze");
        colorToRarity.put("#E4E1DB", "Silver");
        colorToRarity.put("#FCDB2B", "Gold");
        colorToRarity.put("#18CDE4", "Diamond");
        colorToRarity.put("#9B85B0", "Platinum");
        colorToRarity.put("#9400D3", "Mythical"); // not yet confirmed

        while (matcher.find()) {
            String color = matcher.group(1);
            if (colorToRarity.containsKey(color)) {
                return colorToRarity.get(color);
            }
        }

        return "Unknown";
    }


    public String getColourFromRarity(String rarity) {
        // Define a map from rarity to colour
        Map<String, String> rarityColourMap = new HashMap<String, String>() {{
            put("Bronze", "#D9941D");
            put("Silver", "#E4E1DB");
            put("Gold", "#FCDB2B");
            put("Diamond", "#18CDE4");
            put("Platinum", "#9B85B0");
            put("Mythical", "#9400D3");
        }};

        return rarityColourMap.getOrDefault(rarity, "#FFFFFF"); // Default to white
    }


    public void catchFish() {
        if(!modAutofish.getScheduler().isRecastQueued()) { //prevents double reels
            detectOpenWater(client.player.fishHook);
            //queue actions
            queueRodSwitch();
            queueRecast();

            //reel in
            useRod();

            fishCount++;
            if (fishCount >= FISH_COUNT_THRESHOLD) {
                movePlayerHeadRandomly();
                fishCount = 0; // reset the counter to prevent overflow
            }

        }
    }

    // bypass servers anti-cheat by not fishing in the same 
    // spot for more than FISH_COUNT_THRESHOLD times
    public void movePlayerHeadRandomly() {
        if (client.player != null && modAutofish.getConfig().isRandomHeadMovementEnabled()) {
            // Reset the player's yaw and pitch if they recently moved
            if (moved) {
                client.player.setPitch(oldPitch);
                client.player.setYaw(oldYaw);
                moved = false;
                return;
            }
            Random random = new Random();

            // Calculate yaw and pitch changes
            float yawChange = random.nextFloat() * 60 - 30; // Random between -30 and 30 degrees
            float pitchChange = random.nextFloat() * 5 - 5; // Random small change

            // Ensure the changes are enough to move the fishing rod by at least 3 blocks
            yawChange = ensureMinimumDistance(yawChange, 3);
            pitchChange = ensureMinimumDistance(pitchChange, 0.1f);

            // Update the player's yaw and pitch
            client.player.setYaw(client.player.getYaw() + yawChange);
            client.player.setPitch(client.player.getPitch() + pitchChange);
            moved = true;
            oldPitch = client.player.getPitch()-pitchChange;
            oldYaw = client.player.getYaw()-yawChange;
            
        }
    }

    private float ensureMinimumDistance(float change, float minDistance) {
        if (Math.abs(change) < minDistance) {
            return change < 0 ? -minDistance : minDistance;
        }
        return change;
    }


    public void queueRecast() {
        modAutofish.getScheduler().scheduleAction(ActionType.RECAST, modAutofish.getConfig().getRecastDelay(), () -> {
            //State checks to ensure we can still fish once this runs
            if(hookExists) return;
            if(!isHoldingFishingRod()) return;
            if(modAutofish.getConfig().isNoBreak() && getHeldItem().getDamage() >= 63) return;

            useRod();
        });
    }

    private void queueRodSwitch(){
        modAutofish.getScheduler().scheduleAction(ActionType.ROD_SWITCH, modAutofish.getConfig().getRecastDelay() - 250, () -> {
            if(!modAutofish.getConfig().isMultiRod()) return;

            switchToFirstRod(client.player);
        });
    }

    private void detectOpenWater(FishingBobberEntity bobber){
        /*
         * To catch items in the treasure category, the bobber must be in open water,
         * defined as the 5×4×5 vicinity around the bobber resting on the water surface
         * (2 blocks away horizontally, 2 blocks above the water surface, and 2 blocks deep).
         * Each horizontal layer in this area must consist only of air and lily pads or water source blocks,
         * waterlogged blocks without collision (such as signs, kelp, or coral fans), and bubble columns.
         * (from Minecraft wiki)
         * */
        if(!modAutofish.getConfig().isOpenWaterDetectEnabled()) return;

        int x = bobber.getBlockX();
        int y = bobber.getBlockY();
        int z = bobber.getBlockZ();
        boolean flag = true;
        for(int yi = -2; yi <= 2; yi++){
            if(!(BlockPos.stream(x - 2, y + yi, z - 2, x + 2, y + yi, z + 2).allMatch((blockPos ->
                    // every block is water
                        bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.WATER
                    )) || BlockPos.stream(x - 2, y + yi, z - 2, x + 2, y + yi, z + 2).allMatch((blockPos ->
                    // or every block is air or lily pad
                        bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.AIR
                        || bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.LILY_PAD
            )))){
                // didn't pass the check
                if(!alreadyAlertOP){
                    bobber.getPlayerOwner().sendMessage(Text.translatable("info.autofish.open_water_detection.fail"),true);
                    alreadyAlertOP = true;
                    alreadyPassOP = false;
                }
                flag = false;
            }
        }
        if(flag && !alreadyPassOP) {
            bobber.getPlayerOwner().sendMessage(Text.translatable("info.autofish.open_water_detection.success"),true);
            alreadyPassOP = true;
            alreadyAlertOP = false;
        }


    }

    /**
     * Call this when the hook disappears
     */
    private void removeHook() {
        if (hookExists) {
            hookExists = false;
            hookRemovedAt = timeMillis;
            fishMonitorMP.handleHookRemoved();
        }
    }

    public void switchToFirstRod(ClientPlayerEntity player) {
        if(player != null) {
            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.main.size(); i++) {
                ItemStack slot = inventory.main.get(i);
                if (slot.getItem() == Items.FISHING_ROD) {
                    if (i < 9) { //hotbar only
                        if (modAutofish.getConfig().isNoBreak()) {
                            if (slot.getDamage() < 63) {
                                inventory.selectedSlot = i;
                                return;
                            }
                        } else {
                            inventory.selectedSlot = i;
                            return;
                        }
                    }
                }
            }
        }
    }

    public void useRod() {
        if(client.player != null && client.world != null) {
            Hand hand = getCorrectHand();
            ActionResult actionResult = client.interactionManager.interactItem(client.player, hand);
            if (actionResult.isAccepted()) {
                if (actionResult.shouldSwingHand()) {
                    client.player.swingHand(hand);
                }
                client.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
            }
        }
    }

    public boolean isHoldingFishingRod() {
        return isItemFishingRod(getHeldItem().getItem());
    }

    private Hand getCorrectHand() {
        if (!modAutofish.getConfig().isMultiRod()) {
            if (isItemFishingRod(client.player.getOffHandStack().getItem())) return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private ItemStack getHeldItem() {
        if (!modAutofish.getConfig().isMultiRod()) {
            if (isItemFishingRod(client.player.getOffHandStack().getItem()))
                return client.player.getOffHandStack();
        }
        return client.player.getMainHandStack();
    }

    private boolean isItemFishingRod(Item item) {
        return item == Items.FISHING_ROD || item instanceof FishingRodItem;
    }

    public void setDetection() {
        if (modAutofish.getConfig().isUseSoundDetection()) {
            fishMonitorMP = new FishMonitorMPSound();
        } else {
            fishMonitorMP = new FishMonitorMPMotion();
        }
    }

    private boolean shouldUseMPDetection(){
        if(modAutofish.getConfig().isForceMPDetection()) return true;
        return !client.isInSingleplayer();
    }
}

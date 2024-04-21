package irvyn.autofish;

import irvyn.autofish.monitor.FishMonitorMP;
import irvyn.autofish.monitor.FishMonitorMPMotion;
import irvyn.autofish.monitor.FishMonitorMPSound;
import irvyn.autofish.scheduler.ActionType;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.List;
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

    public long timeMillis = 0L;

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
   
    
    public Autofish(FabricModAutofish modAutofish) {
        this.modAutofish = modAutofish;
        this.client = MinecraftClient.getInstance();
        setDetection();

        //Initiate the repeating action for persistent mode casting
        modAutofish.getScheduler().scheduleRepeatingAction(10000, () -> {
            if(!modAutofish.getConfig().isPersistentMode()){
               
                return;
            } 
            if(!isHoldingFishingRod()) return;
            if(hookExists) return;
            if(modAutofish.getScheduler().isRecastQueued()) return;

            useRod();
        });
    }

    private int bambooHarvestCooldown = 0; // cooldown in ticks

    public void bambooHarvest() {
                PlayerInventory inventory = client.player.getInventory();
                if (bambooHarvestCooldown <= 0) {
                if (!inventory.offHand.get(0).isOf(Items.BAMBOO)) {

                    System.out.println("Not holding bamboo in offhand, trying to replenish");
                    
                    // refill from main inventory
                    for (int i = 0; i < inventory.main.size(); i++) {
                        final int index = i;
                        ItemStack stack = inventory.main.get(index);
                        if (stack.isOf(Items.BAMBOO)) {
                            // Move bamboo to offhand
                            client.execute(() -> {
                                // Select the bamboo slot
                                inventory.selectedSlot = index;
                                // Swap with offhand
                                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,
                                        45, // The slot ID for the offhand slot
                                        index,
                                        SlotActionType.SWAP,
                                        client.player);
                            });
                            break;
                        }
                    }
                }
                

                // Find axe in main hand
                boolean axeUsed = false;
                for (int i = 0; i < inventory.main.size() && !axeUsed; i++) {
                    final int axeIndex = i;
                    ItemStack slot = inventory.main.get(axeIndex);
                    if (isAxe(slot.getItem())) {
                        if (slot.getDamage() < slot.getMaxDamage() - 10) {

                            inventory.selectedSlot = axeIndex;
                            BlockPos placePos = client.player.getBlockPos().offset(client.player.getHorizontalFacing());
                            Vec3d hitVec = new Vec3d(placePos.getX() + 0.5, placePos.getY() + 0.5,
                                    placePos.getZ() + 0.5);
                            // Break bamboo
                            client.execute(() -> {
                                client.interactionManager.attackBlock(placePos, Direction.UP);
                                client.player.swingHand(Hand.MAIN_HAND);
                            });
                            axeUsed = true; // Set flag to true after using the axe
                            // Find bamboo in offhand
                            if (isBamboo(client.player.getOffHandStack().getItem())) {
                                // Place bamboo
                                // Use player's current block position instead of fixed location
                                client.execute(() -> {
                                    client.interactionManager.interactBlock(client.player, Hand.OFF_HAND,
                                            new BlockHitResult(hitVec, client.player.getHorizontalFacing(),
                                                    placePos, false));
                                });
                            }
                        }
                    }
                }
                bambooHarvestCooldown = 1;
            }
            
        }
    


    



    public void lichenHarvest() {
        if (client.player != null && modAutofish.getConfig().isLichenHarvestEnabled() && isHoldingShears()) {
            PlayerInventory inventory = client.player.getInventory();
            boolean foundShear = false;
            for (int i = 0; i < inventory.main.size(); i++) {
                ItemStack slot = inventory.main.get(i);
                if (slot.getItem() == Items.SHEARS) {
                    if (slot.getDamage() < 235) {
                        inventory.selectedSlot = i;
                        client.options.attackKey.setPressed(true);
                        return;
                    } else {
                        foundShear = false;
                        // stop harvesting
                        client.options.attackKey.setPressed(false);
                    }
                }
            }
            if (!foundShear) {
                modAutofish.getConfig().setLichenHarvestEnabled(false);
            }
        
        }
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
            if (modAutofish.getConfig().isLichenHarvestEnabled()) {
                lichenHarvest();
            }

            if (modAutofish.getConfig().isBambooHarvestEnabled()) {
                bambooHarvest();
                if (bambooHarvestCooldown > 0) {
                    bambooHarvestCooldown--;
                }
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
                        //check if it matches either of the CYT server messages
                        Matcher matcher = Pattern.compile(modAutofish.getConfig().getClearLagRegex(), Pattern.CASE_INSENSITIVE).matcher(StringHelper.stripTextFormat(packet.content().getString()));
                        Matcher matcher2 = Pattern.compile("This area is suffering from overfishing, cast your rod in a different spot for more fish. At least 3 blocks away.", Pattern.LITERAL).matcher(StringHelper.stripTextFormat(packet.content().getString()));
                        // check if it matches the manacube fishing message
                        Matcher matcher3 = Pattern.compile("might not be many fish in this area", Pattern.LITERAL).matcher(StringHelper.stripTextFormat(packet.content().getString()));
                        if (matcher.find()) {
                            queueRecast();
                        }

                        if (matcher2.find()) {
                            System.out.println("Moving head...");
                            movePlayerHeadRandomly();
                            queueRecast();
                        } 

                        if (matcher3.find()) {
                            System.out.println("Moving head...");
                        }

                        handleCustomFish(packet.content());
                    }
                }
            }
        }
    }

    public void handleOpenScreen(OpenScreenS2CPacket packet) {
       System.out.println(packet.getScreenHandlerType());
       System.out.println(packet);
    }

    public void handleContainerUpdate(ScreenHandlerSlotUpdateS2CPacket packet) {
        ItemStack itemStack = packet.getStack();
        System.out.println("Item: " + itemStack.getItem());
        System.out.println("Count: " + itemStack.getCount());
        System.out.println(itemStack.getNbt());

        // other attempt
        List<Text> tooltip = itemStack.getTooltip(null, TooltipContext.Default.BASIC);

        for (Text text : tooltip) {
            String line = text.getString();
            if (line.contains("-")) {
                System.out.println(line);
            }
            System.out.println("Lore: " + text.getString());
        }
    }
    

    public void handleCustomFish(Text textComponent) {
        // Extracting the fish name and rarity from the formatted Text component
        String rawText = textComponent.getString();
        Matcher fishNameMatcher = Pattern.compile("You caught a (.*?)!").matcher(rawText);
        Matcher crabMatcher = Pattern.compile("You sense that there night not be many fish left in this area\\. Try fishing at least 3 blocks away\\.").matcher(rawText);        if (crabMatcher.find()) {
            // we will move and recast if we catch a crab
            movePlayerHeadRandomly();
            useRod();
            System.out.println("You caught a crab!");
        }

        if (fishNameMatcher.find()) {
            String fishName = fishNameMatcher.group(1);
            // Assuming the color code appears just before the fish name in the chat message
            // We retrieve the style of the text component that contains the fish name
            String rarity = getFishRarity(textComponent); // Default rarity
            if (rarity != null) {
                // here we will add the entry as we know the fishname and rarity
                fishCounts.putIfAbsent(fishName, new HashMap<>());
                fishCounts.get(fishName).put(rarity, fishCounts.get(fishName).getOrDefault(rarity, 0) + 1);
                if (rarity.equals("Platinum")) {
                    // send a chat message saying "GG lets go"
                    String braggingMessage = "GG lets go";
                    // Ensure the client and network handler are not null
                    if (client != null && client.getNetworkHandler() != null) {
                        // Create a chat packet and send it
                        client.getNetworkHandler().sendChatMessage(braggingMessage);
                    }
                }
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

        }
    }

    // bypass servers anti-cheat by not fishing in the same 3 block area
    // TODO: make this smarter to detect if it moved the bobber 3 blocks
    // make it simple - reset after moving 3 blocks
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
            float yawChange = random.nextFloat() * 60 - 40; // Random between -30 and 30 degrees
            float pitchChange = random.nextFloat() * 5 - 5; // Random small change

            // Ensure the changes are enough to move the fishing rod by at least 3 blocks
            yawChange = ensureMinimumDistance(yawChange, 4);
            pitchChange = Math.max(ensureMinimumDistance(pitchChange, 0.1f), pitchChange);

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

    public boolean isHoldingShears() {
        return isItemShears(getHeldItem().getItem());
    }

    public boolean isHoldingAxe() {
        return isItemAxe(getHeldItem().getItem());
    }

    private boolean isAxe(Item item) {
        return item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }
    
    private boolean isBamboo(Item item) {
        return item == Items.BAMBOO; 
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

    private boolean isItemShears(Item item) {
        return item == Items.SHEARS;
    }

    private boolean isItemAxe(Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE || item == Items.GOLDEN_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
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

package irvyn.autofish.config;

import com.google.gson.annotations.Expose;

public class Config {

    @Expose boolean isAutofishEnabled = true;
    @Expose boolean isBambooHarvestEnabled = false;
    @Expose boolean isRandomHeadMovementEnabled = true;
    @Expose boolean isLichenHarvestEnabled = false;    
    @Expose boolean multiRod = false;
    @Expose boolean isOpenWaterDetectEnabled = true;
    @Expose boolean noBreak = false;
    @Expose boolean persistentMode = false;
    @Expose boolean useSoundDetection = false;
    @Expose boolean forceMPDetection = false;
    @Expose long recastDelay = 350;
    @Expose String clearLagRegex = "\\[ClearLag\\] Removed [0-9]+ Entities!";

    public boolean isAutofishEnabled() {
        return isAutofishEnabled;
    }

    public boolean isLichenHarvestEnabled() {
        return isLichenHarvestEnabled;
    }

    public boolean isBambooHarvestEnabled() {
        return isBambooHarvestEnabled;
    }

    public boolean isRandomHeadMovementEnabled() {
        return isRandomHeadMovementEnabled;
    }

    public boolean isOpenWaterDetectEnabled() {
        return isOpenWaterDetectEnabled;
    }

    public boolean isMultiRod() {
        return multiRod;
    }

    public boolean isNoBreak() {
        return noBreak;
    }

    public boolean isPersistentMode() { return persistentMode; }

    public boolean isUseSoundDetection() {
        return useSoundDetection;
    }

    public boolean isForceMPDetection() { return forceMPDetection; }

    public long getRecastDelay() {
        return recastDelay;
    }

    public String getClearLagRegex() {
        return clearLagRegex;
    }

    public void setAutofishEnabled(boolean autofishEnabled) { isAutofishEnabled = autofishEnabled; }

    public void setRandomHeadMovementEnabled(boolean randomHeadMovementEnabled) {
        isRandomHeadMovementEnabled = randomHeadMovementEnabled;
    }

    public void setLichenHarvestEnabled(boolean lichenHarvestEnabled) {
        isLichenHarvestEnabled = lichenHarvestEnabled;
    }

    public void setBambooHarvestEnabled(boolean bambooHarvestEnabled) {
        isBambooHarvestEnabled = bambooHarvestEnabled;
    }

    public void setMultiRod(boolean multiRod) {
        this.multiRod = multiRod;
    }

    public void setNoBreak(boolean noBreak) {
        this.noBreak = noBreak;
    }

    public void setPersistentMode(boolean persistentMode) { this.persistentMode = persistentMode; }

    public void setUseSoundDetection(boolean useSoundDetection) {
        this.useSoundDetection = useSoundDetection;
    }

    public void setForceMPDetection(boolean forceMPDetection) { this.forceMPDetection = forceMPDetection; }

    public void setRecastDelay(long recastDelay) {
        this.recastDelay = recastDelay;
    }

    public void setClearLagRegex(String clearLagRegex) {
        this.clearLagRegex = clearLagRegex;
    }

    public void setOpenWaterDetectEnabled(boolean openWaterDetectEnabled) {
        isOpenWaterDetectEnabled = openWaterDetectEnabled;
    }

    /**
     * @return true if anything was changed
     */
    public boolean enforceConstraints() {
        boolean changed = false;
        if (recastDelay < 500) {
            recastDelay = 500;
            changed = true;
        }
        if (clearLagRegex == null) {
            clearLagRegex = "";
            changed = true;
        }
        return changed;
    }
}

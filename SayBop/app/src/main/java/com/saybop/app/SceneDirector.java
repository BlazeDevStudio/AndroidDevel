package com.saybop.app;

/**
 * Beat-locked offline scene sequencing, independent of Android rendering APIs.
 * A section lasts exactly four beats; the sequence repeats every six sections.
 * Deterministic at arbitrary seeks, irrespective of rendering frame order.
 */
public final class SceneDirector {
    public static final int SCENE_COUNT=6;
    public static final int BEATS_PER_SECTION=4;
    private SceneDirector() {}
    public static int section(BeatAnalyzer.Grid grid,double seconds) {
        if(grid==null || !Double.isFinite(grid.bpm)||grid.bpm<=0 || !Double.isFinite(grid.phaseSeconds)
           || !Double.isFinite(seconds))throw new IllegalArgumentException("Invalid beat grid / time");
        double beats=(seconds-grid.phaseSeconds)/grid.beatSeconds();
        return Math.max(0,(int)Math.floor(beats/BEATS_PER_SECTION));
    }
    public static int scene(BeatAnalyzer.Grid grid,double seconds){
        return Math.floorMod(section(grid,seconds),SCENE_COUNT);
    }
    /** Black-blink-free section change mask: a brief additive flash centered on the downbeat. */
    public static float downbeatFlash(BeatAnalyzer.Grid grid,double seconds) {
        double beat=(seconds-grid.phaseSeconds)/grid.beatSeconds();
        double fractional=((beat%BEATS_PER_SECTION)+BEATS_PER_SECTION)%BEATS_PER_SECTION;
        return (float)Math.max(0,1.0-fractional/.35);
    }
    public static int typographyMotion(int selectedMode,int scene) {
        if(selectedMode!=5)return Math.max(0,Math.min(selectedMode,4));
        return Math.floorMod(scene,3);
    }
}

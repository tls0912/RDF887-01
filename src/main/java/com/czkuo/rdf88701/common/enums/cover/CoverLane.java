package com.czkuo.rdf88701.common.enums.cover;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 公蓋區流道：對應 MAIN / SUB。
 * - MAIN  → Site#14 / TR5 / GP7
 * - SUB   → Site#12 / TR4 / GP6
 */
@Getter
@RequiredArgsConstructor
public enum CoverLane {
    // MAIN  → Site#14 / TR5 / GP7
    MAIN("MAIN", "Site#14", "Site#13", "Transfer#5", 5L, 7L),
    // SUB   → Site#12 / TR4 / GP6
    SUB("SUB",  "Site#12", "Site#11", "Transfer#4", 4L, 6L);

    private final String laneName;      // RobotR029Task.lane 內容
    private final String poolSite;      // 公蓋池：Site#14 / Site#12
    private final String stagingSite;   // 待料站：Site#13 / Site#11
    private final String transferName;  // Transfer#5 / Transfer#4
    private final Long  transferId;     // 5 / 4
    private final Long  gripperId;      // 7 / 6

    public static CoverLane fromLane(String lane) {
        if (lane == null) return null;
        String l = lane.trim().toUpperCase();
        for (CoverLane cl : values()) {
            if (cl.laneName.equals(l)) return cl;
        }
        return null;
    }

    public static CoverLane fromPoolSite(String siteName) {
        if (siteName == null) return null;
        String s = siteName.trim();
        for (CoverLane cl : values()) {
            if (cl.poolSite.equalsIgnoreCase(s)) return cl;
        }
        return null;
    }
}

package com.sudhar.craze;

public enum TrackersEnum {
    BOOSTING(0),
    CSRT(1),
    KCF(2),
    MEDIANFLOW(3),
    MIL(4),
    TLD(5),
    MOSSE(6);

    int value;

    TrackersEnum(int value) {
        this.value = value;
    }

    public static TrackersEnum valueOf(final int value) {
        for (TrackersEnum l : TrackersEnum.values()) {
            if (l.value == value) return l;
        }
        throw new IllegalArgumentException("tracker not found. Amputated?");
    }
}

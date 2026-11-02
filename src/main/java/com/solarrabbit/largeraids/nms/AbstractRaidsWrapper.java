package com.solarrabbit.largeraids.nms;

public interface AbstractRaidsWrapper {
    AbstractRaidWrapper createOrExtendRaid(AbstractPlayerEntityWrapper player);

    void removeRaid(AbstractRaidWrapper raid);

    void setDirty();
}
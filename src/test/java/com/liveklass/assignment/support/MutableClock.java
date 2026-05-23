package com.liveklass.assignment.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MutableClock extends Clock {

    private Instant instant;
    private ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void set(LocalDateTime localDateTime) {
        this.instant = localDateTime.atZone(zone).toInstant();
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.instant, zone);
    }
}

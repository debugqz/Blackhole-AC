package com.blackhole.check;

public abstract class Check {

    private final String name;
    private volatile boolean enabled = true;

    protected Check(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

package com.meowantixray.platform;

public enum LoaderType {
    FABRIC("fabric"),
    NEOFORGE("neoforge");

    private final String modrinthLoaderId;

    LoaderType(String modrinthLoaderId) {
        this.modrinthLoaderId = modrinthLoaderId;
    }

    public String modrinthLoaderId() {
        return modrinthLoaderId;
    }
}

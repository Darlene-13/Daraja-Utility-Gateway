package io.github.darlene.utilitypaymentplatform.token;

public enum MeterType {

    Electricity("ELECTRICITY"),
    Water("WATER");

    private final String description;

    MeterType(String description){
        this.description = description;
    }
}

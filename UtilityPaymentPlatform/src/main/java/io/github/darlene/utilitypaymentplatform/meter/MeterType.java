package io.github.darlene.utilitypaymentplatform.meter;




public enum MeterType{

    WATER("Water"),
    ELECTRICITY("Electricity"),
    ;
    private String description;


    MeterType(String description) {
        this.description = description;
    }

}
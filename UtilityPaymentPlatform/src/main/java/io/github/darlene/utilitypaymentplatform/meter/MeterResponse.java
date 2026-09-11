package io.github.darlene.utilitypaymentplatform.meter;

public record MeterResponse(String meterNumber) {

    public static MeterResponse from(Meter meter){
        return new MeterResponse(meter.getMeterNumber());
    }
}

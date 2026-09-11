package io.github.darlene.utilitypaymentplatform.meter;



public  class MeterNumberNotFoundException extends RuntimeException{

    public MeterNumberNotFoundException(String meterNumber){
        super("No meter found with the number: " + meterNumber);
    }

}
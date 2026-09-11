package io.github.darlene.utilitypaymentplatform.meter;


import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/api/v1/meters")
@AllArgsConstructor
public class MeterController {


    private final MeterValidationService meterValidationService;


    @GetMapping("/{meterNumber}")
    public MeterResponse getMeter(@PathVariable String meterNumber){

        Meter meter = meterValidationService.validate(meterNumber);
        return MeterResponse.from(meter);

    }

}
package io.github.darlene.utilitypaymentplatform.ussd;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ussd")
@AllArgsConstructor
public class UssdController {

    private final UssdMenuService ussdMenuService;

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleUssd(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "text", defaultValue = "") String text
    ){
        return ussdMenuService.handle(phoneNumber, text);
    }

}

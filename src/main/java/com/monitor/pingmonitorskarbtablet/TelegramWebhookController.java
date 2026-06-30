package com.monitor.pingmonitorskarbtablet;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelegramWebhookController {

    @PostMapping("/telegram")
    public void handleUpdate(@RequestBody String updateJson) {
        System.out.println("Update: " + updateJson);
        // тут можна розпарсити JSON і перевірити callback_data
        // якщо "STATUS" → викликати /status і відправити результат через TelegramNotifier.sendMessage()
    }
}
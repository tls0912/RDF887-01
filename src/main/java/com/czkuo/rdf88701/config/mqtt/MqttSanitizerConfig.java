package com.czkuo.rdf88701.config.mqtt;

import com.czkuo.rdf88701.application.mqtt.util.MqttPayloadSanitizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttSanitizerConfig {

    @Bean
    public MqttPayloadSanitizer mqttPayloadSanitizer() {
        // 這裡可以直接 new，也可以根據需求客製設定
        return new MqttPayloadSanitizer()
                .setMaxStringLength(2000)      // 最長字串長度
                .setMaxArrayItems(100)         // 陣列最多保留幾筆
                .setBinaryPreviewLen(0)        // 不要預覽 base64 內容
                .setTreatDataUrlAsBinary(true) // 遇到 data:image/png;base64,... 就清掉
                ;
    }
}

package com.czkuo.rdf88701.config.serial;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "serial")
public class SerialProperties {

    private boolean enabled = true;

    /** 用「defaults」避免底線；另外提供 setDefault(...) 讓 YAML 寫 default: 也能綁定 */
    private Defaults defaults = new Defaults();

    private Reconnect reconnect = new Reconnect();

    private List<Port> ports = new ArrayList<>();

    // 允許 YAML 使用 serial.default: {...}
    public void setDefault(Defaults def) { this.defaults = def; }

    @Data
    public static class Defaults {
        private int baudRate = 115200;
        private int dataBits = 8;
        private int stopBits = 1;
        private String parity = "NONE";      // NONE / EVEN / ODD / MARK / SPACE
        private String flowControl = "NONE"; // NONE / RTS_CTS / XON_XOFF
        private int readTimeoutMs = 200;
        private int writeTimeoutMs = 200;
        private boolean dtr = false;
        private boolean rts = false;
    }

    @Data
    public static class Reconnect {
        private boolean enabled = true;
        private int initialDelayMs = 500;
        private int maxDelayMs = 30_000;
        private double multiplier = 2.0;
        private int jitterMs = 500;
    }

    @Data
    public static class Port {
        private String id;            // e.g., "COM5"
        private String alias;         // e.g., "card1"
        private String protocol = "LINE"; // LINE / STX_ETX / FIXED
        private String delimiter;     // for LINE
        private String stx;           // hex for STX_ETX
        private String etx;           // hex for STX_ETX
        private Integer frameLength;  // for FIXED

        // overrides
        private Integer baudRate;
        private Integer dataBits;
        private Integer stopBits;
        private String parity;
        private String flowControl;
        private Integer readTimeoutMs;
        private Integer writeTimeoutMs;
        private Boolean dtr;
        private Boolean rts;
    }
}

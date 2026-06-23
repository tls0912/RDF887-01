package com.czkuo.rdf88701.application.service.card;

import com.czkuo.rdf88701.config.serial.CardReaderProperties;
import com.czkuo.rdf88701.infra.event.model.card.CardReadEvent;
import com.czkuo.rdf88701.infra.event.model.serial.SerialFrameEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardReaderService {

    private final CardReaderProperties props;
    private final ApplicationEventPublisher publisher;

    private final Set<String> enabledAliases = new HashSet<>();
    private final Map<String, Boolean> reverseMap = new HashMap<>();

    // 清除非十六進位字元、擷取數字 fallback 用
    private static final Pattern NON_HEX = Pattern.compile("[^0-9A-Fa-f]");
    private static final Pattern DIGITS  = Pattern.compile("\\d+");

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("[Card] card-reader disabled");
            return;
        }
        for (CardReaderProperties.Reader r : props.getReaders()) {
            if (r.getAlias() == null || r.getAlias().isBlank()) continue;
            enabledAliases.add(r.getAlias());
            boolean reverse = r.getReverse() != null ? r.getReverse() : props.isDefaultReverse();
            reverseMap.put(r.getAlias(), reverse);
        }
        log.info("[Card] 啟用讀卡機別名：{}，defaultReverse={}", enabledAliases, props.isDefaultReverse());
    }

    @EventListener
    public void onSerialFrame(SerialFrameEvent ev) {
        if (!props.isEnabled()) return;
        final String alias = ev.getAlias();
        if (!enabledAliases.contains(alias)) return; // 不是讀卡機來源

        final byte[] frame = ev.getPayload(); // LINE 模式已去掉 delimiter
        if (frame == null || frame.length == 0) return;

        String rawText = new String(frame, StandardCharsets.US_ASCII);
        if (props.isTrimCr()) rawText = rawText.replace("\r", "");
        rawText = rawText.replace("\n", "").trim();

        // 步驟1：產出乾淨的 hex（支援反轉、大小寫）
        String hexId = parseCardId(
                rawText,
                reverseMap.getOrDefault(alias, props.isDefaultReverse()),
                props.isUppercase()
        );

        // 步驟2：安全轉十進位（支援超過 long 的卡號）
        String decId = hexToDecimalSafe(hexId);

        // 步驟3：fallback（有些機型直接送十進位）
        if (decId == null) {
            Matcher m = DIGITS.matcher(rawText);
            decId = m.find() ? m.group() : hexId; // 最後仍保留 hex，避免空值
            //log.debug("[Card] Hex→Dec 解析失敗，使用 fallback。raw='{}', hex='{}', dec='{}'", rawText, hexId, decId);
        }

        log.info("【{}】Card ID: {} (hex: {})", alias, decId, hexId);

        // 丟事件（維持用十進位字串）
        publisher.publishEvent(new CardReadEvent(this, alias, decId, frame));
    }

    /** 先清雜訊，再（可選）轉大寫，最後按位元組兩兩反轉 */
    static String parseCardId(String raw, boolean reverse, boolean uppercase) {
        String s = NON_HEX.matcher(raw).replaceAll(""); // 僅保留 0-9A-F
        if (uppercase) s = s.toUpperCase(Locale.ROOT);
        if (reverse) s = reverseByPairs(s);
        return s.trim();
    }

    /** 兩兩倒序："12345678" -> "78563412"；奇數長度保守丟棄最後一碼避免錯配 */
    static String reverseByPairs(String s) {
        int n = s.length();
        if ((n & 1) == 1) n = n - 1;
        StringBuilder out = new StringBuilder(n);
        for (int i = n - 2; i >= 0; i -= 2) {
            out.append(s.charAt(i)).append(s.charAt(i + 1));
        }
        return out.toString();
    }

    /** 將十六進位字串安全轉為十進位字串；失敗回傳 null */
    static String hexToDecimalSafe(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String clean = NON_HEX.matcher(hex).replaceAll("");
        if (clean.isEmpty()) return null;
        try {
            return new BigInteger(clean, 16).toString(10);
        } catch (Exception ex) {
            //log.debug("[Card] hexToDecimalSafe 失敗: hex='{}', err={}", hex, ex.getMessage());
            return null;
        }
    }
}

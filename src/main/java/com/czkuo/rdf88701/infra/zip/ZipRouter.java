package com.czkuo.rdf88701.infra.zip;

import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.config.zip.ZipStockerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ZipRouter {

    private final ZipStockerProperties props;

    /** 取得預設目標（設定錯誤時回退 ZIPA） */
    public ZipTarget defaultTarget() {
        String s = props.getDefaultTarget();
        try {
            return (s == null) ? ZipTarget.ZIPA : ZipTarget.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ZipTarget.ZIPA;
        }
    }

    /** 依目標取得 Base URL；若未配置則回退舊 baseUrl；再不然丟錯 */
    public String baseUrlOf(ZipTarget target) {
        if (target != null && props.getTargets() != null && !props.getTargets().isEmpty()) {
            String key = target.name().toLowerCase(Locale.ROOT);
            ZipStockerProperties.Target t = props.getTargets().get(key);
            if (t != null && StringUtils.hasText(t.getBaseUrl())) {
                return t.getBaseUrl();
            }
        }
        if (StringUtils.hasText(props.getBaseUrl())) {
            return props.getBaseUrl();
        }
        throw new IllegalStateException("No zipstocker base-url configured for target=" + target);
    }
}

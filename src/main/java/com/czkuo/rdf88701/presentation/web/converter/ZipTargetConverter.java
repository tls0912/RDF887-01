package com.czkuo.rdf88701.presentation.web.converter;

import com.czkuo.rdf88701.common.enums.ZipTarget;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Component
public class ZipTargetConverter implements Converter<String, ZipTarget> {
    @Override
    public ZipTarget convert(String source) {
        if (source == null) return null;
        String s = source.trim().toUpperCase(Locale.ROOT);
        if ("A".equals(s)) s = "ZIPA";
        else if ("B".equals(s)) s = "ZIPB";
        return ZipTarget.valueOf(s);
    }
}

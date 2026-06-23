package com.czkuo.rdf88701.infra.adapter.plc.protocol.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PLC 協議 options 映射工具：
 * 提供 YAML 中 kebab-case 格式的 map，自動轉為對應 Java POJO。
 */
@Component
public class PlcOptionMapper {

    /**
     * 使用 kebab-case 命名規則的 ObjectMapper
     * - ex: overall-timeout-ms → overallTimeoutMs
     */
    private final ObjectMapper kebabCaseMapper;

    public PlcOptionMapper() {
        this.kebabCaseMapper = new ObjectMapper();
        this.kebabCaseMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }

    /**
     * 將 Map 格式的設定轉換為指定類型的 options 物件
     *
     * @param options 來源設定 Map（YAML 載入）
     * @param clazz   目標 class 型別
     * @param <T>     回傳型別
     * @return 映射後的 options 物件
     */
    public <T> T convert(Map<String, Object> options, Class<T> clazz) {
        return kebabCaseMapper.convertValue(options, clazz);
    }
}

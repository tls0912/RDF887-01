package com.czkuo.rdf88701.application.service.r029;

import org.apache.commons.lang3.StringUtils;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 寬鬆版 R029 Base 解析器
 * 支援任意 <tag>（非空且不含底線），例如:
 *   base:   AAA_BBB_1
 *   子項:    AAA_BBB_1_1
 *   併項:    AAA_BBB_1+2_3
 */
public final class R029BaseIdParser {

    // 嚴謹版（tag 為英數開頭）；若不符再用寬鬆版
    private static final Pattern STRICT_DERIVED = Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*)_([1-9][0-9]*)$");
    private static final Pattern STRICT_PURE    = Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*)$");

    // 寬鬆版
    // (?:\\+[1-9][0-9]*)* 這個 * 允許 +數量 重複 0 次（沒有 +）或多次（任意個 +）。
    private static final Pattern LOOSE_DERIVED  = Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*(?:\\+[1-9][0-9]*)*)_([1-9][0-9]*)$");
    private static final Pattern LOOSE_PURE     = Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*(?:\\+[1-9][0-9]*)*)$");

    private R029BaseIdParser() {}

    /** 僅判斷是否是 base（不含併項/子項尾巴） */
    private static boolean isPureBase(String s) {
        return STRICT_PURE.matcher(s).matches();
    }

    /** 嘗試用嚴謹→寬鬆兩階段做比對 */
    private static Matcher matchDerived(String s) {
        Matcher m = STRICT_DERIVED.matcher(s);
        if (m.matches()) return m;
        m = LOOSE_DERIVED.matcher(s);
        return m.matches() ? m : null;
    }

    /**
     * 由新載具 ID 解析出所有候選的 base（可能多個，用於與任務 base 求交集）
     * @param newCarrierId 例：X_P_1_1、X_BIN_1+2_3、X_P_2
     * @return 例：["X_P_1"] 或 ["X_BIN_1","X_BIN_2"]
     */
    public static List<String> extractCandidateBases(String newCarrierId) {
        if (StringUtils.isBlank(newCarrierId)) return Collections.emptyList();

        // 原本就是 base
        if (isPureBase(newCarrierId)) {
            return Collections.singletonList(newCarrierId);
        }

        // 派生（子/併）
        Matcher m = matchDerived(newCarrierId);
        if (m == null) return Collections.emptyList();

        String prefix = m.group(1);  // "..._"
        String tag    = m.group(2);  // 任意非空、非底線
        String idxSet = m.group(3);  // "1" 或 "1+2+3"

        return Arrays.stream(idxSet.split("\\+"))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(n -> prefix + "_" + tag + "_" + n)  // 還原各個 base
                .distinct()
                .collect(Collectors.toList());
    }

    /** 與任務 base 求交集（任務 base 是完整字串，如 AAA_BBB_1） */
    public static List<String> intersectWithTaskBases(String newCarrierId, Collection<String> taskBaseIds) {
        if (taskBaseIds == null || taskBaseIds.isEmpty()) return Collections.emptyList();
        Set<String> taskSet = taskBaseIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
        List<String> cand = extractCandidateBases(newCarrierId);
        return cand.stream().filter(taskSet::contains).toList();
    }

    /** 把多個同前綴同 tag 的 base 合併為 canonical 形式：prefix + tag + "_" + (n1+n2+...) */
    public static String canonicalizeMultipleBases(List<String> bases) {
        if (bases == null || bases.isEmpty()) return null;
        if (bases.size() == 1) return bases.get(0);

        // 嘗試抓共同 prefix 與 tag：依「最後兩段」為 <tag>_<n>
        String[] sp0 = bases.get(0).split("_");
        if (sp0.length < 2) return String.join("+", bases);
        String tag0 = sp0[sp0.length - 2];
        String prefix0 = String.join("_", Arrays.asList(sp0).subList(0, sp0.length - 2)) + "_";

        List<String> idx = new ArrayList<>();
        for (String b : bases) {
            String[] sp = b.split("_");
            if (sp.length < 2) return String.join("+", bases);
            String tag = sp[sp.length - 2];
            String prefix = String.join("_", Arrays.asList(sp).subList(0, sp.length - 2)) + "_";
            if (!tag0.equals(tag) || !prefix0.equals(prefix)) {
                // 前綴或 tag 不一致，就不強行合併
                return String.join("+", bases);
            }
            idx.add(sp[sp.length - 1]);
        }

        // 去重 + 盡量用數值排序
        try {
            idx = idx.stream().distinct()
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .toList();
        } catch (NumberFormatException ignored) {
            idx = idx.stream().distinct().sorted().toList();
        }
        return prefix0 + tag0 + "_" + String.join("+", idx);
    }
}

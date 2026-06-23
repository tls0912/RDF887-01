package com.czkuo.rdf88701.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Lambda 查詢條件幫助工具
 */
public class LambdaQueryHelper<T> {

    private final LambdaQueryWrapper<T> wrapper;

    public LambdaQueryHelper() {
        this.wrapper = new LambdaQueryWrapper<>();
    }

    /** 建立新的 Helper 實例 */
    public static <T> LambdaQueryHelper<T> of() {
        return new LambdaQueryHelper<>();
    }

    /** 取得最終的 Wrapper */
    public LambdaQueryWrapper<T> getWrapper() {
        return this.wrapper;
    }

    /** 如果值非空則加上 eq 條件 */
    public <R> LambdaQueryHelper<T> eqIfPresent(SFunction<T, R> column, Supplier<R> valueSupplier) {
        R value = valueSupplier.get();
        if (value != null) {
            wrapper.eq(column, value);
        }
        return this;
    }

    /** 如果值非空則加上 ge 條件 */
    public <R extends Comparable<R>> LambdaQueryHelper<T> geIfPresent(SFunction<T, R> column, Supplier<R> valueSupplier) {
        R value = valueSupplier.get();
        if (value != null) {
            wrapper.ge(column, value);
        }
        return this;
    }

    /** 如果值非空則加上 le 條件 */
    public <R extends Comparable<R>> LambdaQueryHelper<T> leIfPresent(SFunction<T, R> column, Supplier<R> valueSupplier) {
        R value = valueSupplier.get();
        if (value != null) {
            wrapper.le(column, value);
        }
        return this;
    }

    /** 如果字串非空則加上 like 條件 */
    public LambdaQueryHelper<T> likeIfPresent(SFunction<T, String> column, Supplier<String> valueSupplier) {
        String value = valueSupplier.get();
        if (value != null && !value.isBlank()) {
            wrapper.like(column, value);
        }
        return this;
    }

    /** 如果集合非空則加上 in 條件 */
    public <R> LambdaQueryHelper<T> inIfPresent(SFunction<T, R> column, Supplier<Collection<R>> valueSupplier) {
        Collection<R> values = valueSupplier.get();
        if (values != null && !values.isEmpty()) {
            wrapper.in(column, values);
        }
        return this;
    }

    /** 如果有上下限則加上 between 條件，支援只給上限或下限 */
    public <R extends Comparable<R>> LambdaQueryHelper<T> betweenIfPresent(
            SFunction<T, R> column,
            Supplier<R> startSupplier,
            Supplier<R> endSupplier) {
        R start = startSupplier.get();
        R end = endSupplier.get();
        if (start != null && end != null) {
            wrapper.between(column, start, end);
        } else if (start != null) {
            wrapper.ge(column, start);
        } else if (end != null) {
            wrapper.le(column, end);
        }
        return this;
    }

    /** 根據布林值決定是否加上排序條件 */
    public LambdaQueryHelper<T> orderByIfPresent(boolean condition, SFunction<T, ?> column, boolean isAsc) {
        if (condition) {
            wrapper.orderBy(true, isAsc, column);
        }
        return this;
    }
}

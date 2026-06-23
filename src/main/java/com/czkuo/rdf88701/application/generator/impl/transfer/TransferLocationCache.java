package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TransferLocationCache {

    private static final Map<String, Long> LOCATION_ID_BY_NAME = new ConcurrentHashMap<>();

    private TransferLocationCache() {
    }

    static Long requireLocationId(LocationPointRepository repository, String name) {
        return LOCATION_ID_BY_NAME.computeIfAbsent(name, key ->
                repository.findByName(key)
                        .map(LocationPoint::getId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid location name: " + key)));
    }

    static Long findLocationId(LocationPointRepository repository, String name) {
        return LOCATION_ID_BY_NAME.computeIfAbsent(name, key ->
                repository.findByName(key).map(LocationPoint::getId).orElse(null));
    }
}

package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class GripperLocationCache {

    private static final Map<String, Long> LOCATION_ID_BY_NAME = new ConcurrentHashMap<>();

    private GripperLocationCache() {
    }

    static Long requireLocationId(LocationPointRepository repository, String name) {
        return LOCATION_ID_BY_NAME.computeIfAbsent(name, key ->
                repository.findByName(key)
                        .map(LocationPoint::getId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid location name: " + key)));
    }
}

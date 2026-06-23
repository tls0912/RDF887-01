package com.czkuo.rdf88701.application.service.transfer;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class TransferServiceLocationCache {

    private static final Map<String, Long> LOCATION_ID_BY_NAME = new ConcurrentHashMap<>();

    private TransferServiceLocationCache() {
    }

    static Optional<Long> findLocationId(LocationPointRepository repository, String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(LOCATION_ID_BY_NAME.computeIfAbsent(name, key ->
                repository.findByName(key).map(LocationPoint::getId).orElse(null)));
    }
}

package com.czkuo.rdf88701.domain.event;

import lombok.Getter;


public record HistoryEvent<T>(T entity, String changeType) {

}

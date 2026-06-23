package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class GenericHistoryInsertService<T, H> {

    private final ConcurrentLinkedQueue<HistoryEvent<T>> queue
            = new ConcurrentLinkedQueue<>();

    public void offer(T entity, String changeType) {
        queue.offer(new HistoryEvent<>(entity, changeType));
    }

    public List<HistoryEvent<T>> poll(int batchSize) {

        List<HistoryEvent<T>> result = new ArrayList<>(batchSize);

        while (result.size() < batchSize) {

            HistoryEvent<T> event = queue.poll();

            if (event == null) {
                break;
            }

            result.add(event);
        }

        return result;
    }

    public abstract H toHistory(HistoryEvent<T> event);

    public int size() {
        return queue.size();
    }
}

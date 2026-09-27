package de.agiehl.bgoffers.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class ScraperExecutionCoordinator {

    private final ReentrantLock lock = new ReentrantLock();

    public void run(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}

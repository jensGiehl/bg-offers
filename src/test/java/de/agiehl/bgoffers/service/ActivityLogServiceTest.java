package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ActivityLogServiceTest {

    @Test
    void doesNotPersistEventsDuringADryRun() {
        var repository = mock(ActivityLogRepository.class);
        var dryRunContext = new DryRunContext();
        var service = new ActivityLogService(repository, dryRunContext, Clock.systemUTC());

        dryRunContext.run(() -> service.recordTelegramDelivery(true));

        verify(repository, never()).save(any());
    }

    @Test
    void persistsEventsOutsideADryRun() {
        var repository = mock(ActivityLogRepository.class);
        var service = new ActivityLogService(repository, new DryRunContext(), Clock.systemUTC());

        service.recordTelegramDelivery(true);

        verify(repository).save(any());
    }

    @Test
    void doesNotPersistEventsFromDryRunVirtualThreads() {
        var repository = mock(ActivityLogRepository.class);
        var dryRunContext = new DryRunContext();
        var service = new ActivityLogService(repository, dryRunContext, Clock.systemUTC());

        dryRunContext.run(() -> recordFromVirtualThread(service));

        verify(repository, never()).save(any());
    }

    private void recordFromVirtualThread(ActivityLogService service) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> service.recordTelegramDelivery(true)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Virtual thread was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Virtual thread failed", exception.getCause());
        }
    }
}

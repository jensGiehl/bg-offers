package de.agiehl.bgoffers.service;

import de.agiehl.bgoffers.domain.ExternalHealthCheck;
import de.agiehl.bgoffers.domain.ExternalHealthCheckStatus;
import de.agiehl.bgoffers.enrichment.BggLookupService;
import de.agiehl.bgoffers.notification.OfferNotifier;
import de.agiehl.bgoffers.repository.ExternalHealthCheckStatusRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalHealthCheckServiceTest {

    @Test
    void sendsOneRecoveryAfterAFailureAndWaitsForAnotherFailureBeforeSendingAnother() {
        var bgg = mock(BggLookupService.class);
        var repository = repository();
        var notifier = mock(OfferNotifier.class);
        when(bgg.healthCheck()).thenReturn(false, true, true, false, true);
        when(notifier.sendHealthRecovery(any())).thenReturn(true);
        var service = new ExternalHealthCheckService(bgg, repository, notifier);

        service.verify();
        service.verify();
        service.verify();
        service.verify();
        service.verify();

        verify(notifier, times(2)).sendHealthAlert(contains("BoardGameGeek"));
        verify(notifier, times(2)).sendHealthRecovery(contains("BoardGameGeek"));
        verify(notifier, never()).sendHealthAlert(contains("brettspiel-angebote.de"));
        verify(notifier, never()).sendHealthRecovery(contains("brettspiel-angebote.de"));
        verify(repository, never()).findById(ExternalHealthCheck.PRICE_COMPARISON);
    }

    @Test
    void retriesARecoveryNotificationUntilItWasDelivered() {
        var bgg = mock(BggLookupService.class);
        var repository = repository();
        var notifier = mock(OfferNotifier.class);
        when(bgg.healthCheck()).thenReturn(false, true, true, true);
        when(notifier.sendHealthRecovery(any())).thenReturn(false, true);
        var service = new ExternalHealthCheckService(bgg, repository, notifier);

        service.verify();
        service.verify();
        service.verify();
        service.verify();

        verify(notifier, times(2)).sendHealthRecovery(contains("BoardGameGeek"));
    }

    @Test
    void reportsOnlyTheBggFailureWithItsTestDetails() {
        var bgg = mock(BggLookupService.class);
        var repository = repository();
        var notifier = mock(OfferNotifier.class);
        var messages = ArgumentCaptor.forClass(String.class);
        when(bgg.healthCheck()).thenReturn(false);
        var service = new ExternalHealthCheckService(bgg, repository, notifier);

        service.verify();

        verify(notifier).sendHealthAlert(messages.capture());
        assertThat(messages.getAllValues())
                .singleElement().asString().contains("Magical Athlete", "BoardGameGeek");
    }

    private ExternalHealthCheckStatusRepository repository() {
        var repository = mock(ExternalHealthCheckStatusRepository.class);
        var statuses = new EnumMap<ExternalHealthCheck, ExternalHealthCheckStatus>(ExternalHealthCheck.class);
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(statuses.get(
                invocation.getArgument(0, ExternalHealthCheck.class))));
        when(repository.save(any())).thenAnswer(invocation -> {
            var status = invocation.getArgument(0, ExternalHealthCheckStatus.class);
            statuses.put(status.getCheck(), status);
            return status;
        });
        return repository;
    }
}

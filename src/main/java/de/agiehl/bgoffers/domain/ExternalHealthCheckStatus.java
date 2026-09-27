package de.agiehl.bgoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "external_health_check")
public class ExternalHealthCheckStatus {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "check_name", nullable = false, length = 32)
    private ExternalHealthCheck check;

    @Column(name = "recovery_notification_pending", nullable = false)
    private boolean recoveryNotificationPending;

    protected ExternalHealthCheckStatus() {
    }

    public static ExternalHealthCheckStatus start(ExternalHealthCheck check) {
        var status = new ExternalHealthCheckStatus();
        status.check = check;
        return status;
    }

    public ExternalHealthCheck getCheck() {
        return check;
    }

    public boolean isRecoveryNotificationPending() {
        return recoveryNotificationPending;
    }

    public void markFailed() {
        recoveryNotificationPending = true;
    }

    public void markRecoveryNotificationSent() {
        recoveryNotificationPending = false;
    }
}

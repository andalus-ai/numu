package tech.kayys.numou.construction.application.payment.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.payment.command.CreatePaymentCertificateCommand;
import tech.kayys.numou.construction.domain.payment.PaymentCertificate;
import tech.kayys.numou.construction.spi.payment.PaymentCertificateRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreatePaymentCertificateHandler implements CommandHandler<CreatePaymentCertificateCommand, PaymentCertificate> {
    private final PaymentCertificateRepository repository;
    private final EventPublisher eventPublisher;

    public CreatePaymentCertificateHandler(PaymentCertificateRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<PaymentCertificate> handle(CreatePaymentCertificateCommand command) {
        var cert = PaymentCertificate.create(command.contractId(), command.certificateNumber(), command.summary());
        return Uni.createFrom()
                .completionStage(repository.save(cert))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}

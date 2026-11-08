package tech.kayys.numou.advertising.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.advertising.application.AdvertisingAccountService;
import tech.kayys.numou.advertising.application.AdvertisingAuthorizationPort;
import tech.kayys.numou.advertising.application.AdvertisingPartyVerificationPort;
import tech.kayys.numou.advertising.spi.AdvertisingAccountStore;

import java.time.Clock;

/** CDI composition for the account/inventory application service. */
@ApplicationScoped
public class AdvertisingServiceProducer {
    @Produces
    @ApplicationScoped
    AdvertisingAccountService advertisingAccountService(AdvertisingAccountStore store,
                                                        AdvertisingAuthorizationPort authorization,
                                                        AdvertisingPartyVerificationPort partyVerification) {
        return new AdvertisingAccountService(store,authorization,partyVerification,Clock.systemUTC());
    }
}

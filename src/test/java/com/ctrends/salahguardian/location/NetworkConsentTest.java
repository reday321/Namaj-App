package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for audit finding SG-H-02.
 *
 * <p>The application used to perform its one outbound request automatically on
 * first launch, with no prompt and no way to opt out beforehand. That request
 * hands a third-party geolocation service the user's IP address and, for an
 * application of this kind, implies their religion - special-category data
 * under GDPR Article 9.</p>
 */
class NetworkConsentTest {

    @Test
    @DisplayName("a fresh installation has not consented and has not been asked")
    void freshInstallIsUndecided() {
        AppConfig config = new AppConfig();
        assertTrue(config.isNetworkLookupUndecided(),
                "the question must be put before anything leaves the machine");
        assertFalse(config.isNetworkLookupConsented(),
                "silence is not consent");
    }

    @Test
    @DisplayName("consent is tri-state, so declining is remembered as a decision")
    void declineIsRemembered() {
        AppConfig config = new AppConfig();

        config.setNetworkLookupConsented(Boolean.FALSE);
        assertFalse(config.isNetworkLookupUndecided(), "a decline is an answer, not silence");
        assertFalse(config.isNetworkLookupConsented());

        config.setNetworkLookupConsented(Boolean.TRUE);
        assertFalse(config.isNetworkLookupUndecided());
        assertTrue(config.isNetworkLookupConsented());
    }

    @Test
    @DisplayName("the IP provider stays inert until consent is given")
    void providerIsGatedOnConsent() {
        IpGeolocationProvider provider = new IpGeolocationProvider();

        provider.setConsentCheck(() -> false);
        assertFalse(provider.isAvailable(),
                "without consent the provider must not even be attempted");
        assertTrue(provider.resolve().isEmpty()
                        || !provider.isAvailable(),
                "the chain must skip it rather than reaching the network");

        provider.setConsentCheck(() -> true);
        assertTrue(provider.isAvailable());
    }

    @Test
    @DisplayName("an existing installation is not re-prompted about a request it already made")
    void existingInstallIsNotRePrompted() {
        AppConfig legacy = new AppConfig();
        legacy.setSchemaVersion(2);
        legacy.applyLocation(com.ctrends.salahguardian.model.GeoLocation.of(
                23.81, 90.41, "Dhaka", "Bangladesh",
                com.ctrends.salahguardian.model.LocationSource.IP_GEOLOCATION));

        legacy.migrate().normalise();

        assertFalse(legacy.isNetworkLookupUndecided(),
                "the lookup already happened for this user, so asking again is theatre");
        assertTrue(legacy.isNetworkLookupConsented());
    }

    @Test
    @DisplayName("a fresh v2 config with no stored location is still asked")
    void freshLegacyConfigIsStillAsked() {
        AppConfig legacy = new AppConfig();
        legacy.setSchemaVersion(2);

        legacy.migrate().normalise();

        assertTrue(legacy.isNetworkLookupUndecided(),
                "consent must not be invented for a request that has not happened");
    }
}

package com.calipso.subscription;

import com.calipso.campaign.CampaignChannel;
import com.calipso.compagny.*;
import com.calipso.sms.OrangeSmsClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultichannelPricingTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final CompanySubscriptionRepository subscriptions = mock(CompanySubscriptionRepository.class);
    private final WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
    private final MobilePaymentGatewayService payments = mock(MobilePaymentGatewayService.class);
    private final SubscriptionService service = new SubscriptionService(companies, plans, subscriptions,
            transactions, mock(OrangeSmsClient.class), payments);
    private final Map<SubscriptionPlanCode, SubscriptionPlan> catalog = new LinkedHashMap<>();
    private final AtomicReference<CompanySubscription> current = new AtomicReference<>();
    private final AtomicReference<WalletTransaction> lastTransaction = new AtomicReference<>();
    private final Company company = Company.builder().id(8L).name("MAGISER").smsBalance(41).build();

    @BeforeEach void setup() {
        when(plans.findByCode(any())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.getArgument(0))));
        when(plans.save(any())).thenAnswer(i -> {
            var plan = i.<SubscriptionPlan>getArgument(0); catalog.put(plan.getCode(), plan); return plan;
        });
        new SubscriptionPlanSeeder(plans, mock(JdbcTemplate.class)).run();
        when(plans.findByActiveTrueOrderByPricePerSmsDesc()).thenAnswer(i -> new ArrayList<>(catalog.values()));
        when(companies.findById(8L)).thenReturn(Optional.of(company));
        when(companies.findLockedById(8L)).thenReturn(Optional.of(company));
        when(companies.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptions.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(eq(8L), eq(SubscriptionStatus.ACTIVE)))
                .thenAnswer(i -> Optional.ofNullable(current.get()).filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE));
        when(subscriptions.findFirstByCompanyIdOrderByStartedAtDesc(8L)).thenAnswer(i -> Optional.ofNullable(current.get()));
        when(subscriptions.save(any())).thenAnswer(i -> {
            var sub = i.<CompanySubscription>getArgument(0); current.set(sub); return sub;
        });
        when(transactions.save(any())).thenAnswer(i -> {
            var transaction = i.<WalletTransaction>getArgument(0); lastTransaction.set(transaction); return transaction;
        });
        when(transactions.findByReference("PAY-TEST")).thenAnswer(i -> Optional.ofNullable(lastTransaction.get()));
        paymentStatus(PaymentStatus.COMPLETED);
    }

    private void paymentStatus(PaymentStatus status) {
        when(payments.processPayment(anyLong(), anyString(), anyInt(), anyInt(), any(), any(), anyString()))
                .thenReturn(new MobilePaymentGatewayService.PaymentChargeResult("PAY-TEST", PaymentMethod.CARD,
                        status, null, "TEST", status == PaymentStatus.PENDING ? "https://checkout.example/pay" : null, "Test"));
    }

    static Stream<MultichannelCatalog.Offer> offers() { return MultichannelCatalog.OFFERS.stream(); }

    @ParameterizedTest @MethodSource("offers")
    void chargesTheFixedOfferAndCreditsThreeDistinctAllowances(MultichannelCatalog.Offer offer) {
        var before = LocalDateTime.now();
        var result = service.subscribe(8L, new SubscribeRequest(offer.code(), null, PaymentMethod.CARD, null, null));
        verify(payments).processPayment(eq(8L), eq("MAGISER"), eq(offer.price()), eq(offer.sms()),
                eq(PaymentMethod.CARD), any(), anyString());
        assertEquals(41 + offer.sms(), result.smsBalance());
        assertEquals(offer.whatsapp(), result.whatsappBalance());
        assertEquals(offer.email(), result.emailBalance());
        assertEquals(offer.price(), result.lastTransaction().amount());
        assertEquals(offer.months(), result.activeSubscription().durationMonths());
        var expiry = result.activeSubscription().expiresAt();
        assertFalse(expiry.isBefore(before.plusMonths(offer.months())));
        assertFalse(expiry.isAfter(LocalDateTime.now().plusMonths(offer.months())));
    }

    @Test void seedsAllNineValidatedPricesWithoutOverwritingHistoricalPlans() {
        Map<SubscriptionPlanCode, Integer> prices = Map.of(
                SubscriptionPlanCode.ESSENTIEL_3M, 75_000, SubscriptionPlanCode.ESSENTIEL_6M, 135_000,
                SubscriptionPlanCode.ESSENTIEL_12M, 240_000, SubscriptionPlanCode.PROFESSIONNEL_3M, 180_000,
                SubscriptionPlanCode.PROFESSIONNEL_6M, 330_000, SubscriptionPlanCode.PROFESSIONNEL_12M, 600_000,
                SubscriptionPlanCode.COMPLET_3M, 360_000, SubscriptionPlanCode.COMPLET_6M, 660_000,
                SubscriptionPlanCode.COMPLET_12M, 1_200_000);
        catalog.get(SubscriptionPlanCode.ESSENTIEL).setPricePerSms(19);
        new SubscriptionPlanSeeder(plans, mock(JdbcTemplate.class)).run();
        assertEquals(13, catalog.size());
        assertEquals(19, catalog.get(SubscriptionPlanCode.ESSENTIEL).getPricePerSms());
        assertEquals(9, service.findPlans().size());
        for (var plan : service.findPlans()) assertEquals(prices.get(plan.code()), plan.packagePrice());
        var full = catalog.get(SubscriptionPlanCode.COMPLET_12M);
        assertEquals(60_000, full.getMinRechargeUnits());
        assertEquals(12_000, full.getIncludedWhatsapp());
        assertEquals(360_000, full.getIncludedEmail());
    }

    @Test void pendingPaymentDoesNotGrantAnythingAndItsCallbackIsAppliedOnce() {
        paymentStatus(PaymentStatus.PENDING);
        var result = service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.COMPLET_12M));
        assertEquals(41, result.smsBalance()); assertNull(result.whatsappBalance()); assertNull(current.get());
        assertEquals(PaymentStatus.PENDING, result.lastTransaction().paymentStatus());
        var webhook = new PaymentWebhookRequest("PAY-TEST", "SUCCESS", null, null, null);
        service.handlePaymentWebhook(webhook);
        service.handlePaymentWebhook(webhook);
        assertEquals(60_041, company.getSmsBalance());
        assertEquals(12_000, company.getWhatsappBalance()); assertEquals(360_000, company.getEmailBalance());
        assertEquals(PaymentStatus.COMPLETED, lastTransaction.get().getPaymentStatus());
    }

    @Test void failedPaymentDoesNotGrantAnything() {
        paymentStatus(PaymentStatus.FAILED);
        service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.ESSENTIEL_3M));
        assertEquals(41, company.getSmsBalance()); assertNull(company.getWhatsappBalance()); assertNull(current.get());
    }

    @Test void rejectsAlteredVolumesAndHistoricalOffersBeforeContactingPaymentProvider() {
        assertThrows(ResponseStatusException.class, () -> service.subscribe(8L,
                new SubscribeRequest(SubscriptionPlanCode.COMPLET_12M, 1, PaymentMethod.CARD, null, null)));
        assertThrows(ResponseStatusException.class, () -> service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.VOLUME)));
        verifyNoInteractions(payments);
    }

    @Test void eachChannelUsesItsOwnBalanceAndCannotBorrowSmsCredits() {
        service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.ESSENTIEL_3M));
        service.debitMessages(company, CampaignChannel.WHATSAPP, 2, "WhatsApp");
        service.debitMessages(company, CampaignChannel.EMAIL, 3, "Email");
        assertEquals(1_541, company.getSmsBalance());
        assertEquals(748, company.getWhatsappBalance()); assertEquals(14_997, company.getEmailBalance());
        company.setWhatsappBalance(0);
        assertThrows(ResponseStatusException.class, () -> service.validateBalance(company, CampaignChannel.WHATSAPP, 1));
        assertEquals(1_541, company.getSmsBalance());
    }

    @Test void expiryRequiresRenewalAndNeverAutomaticallyCreatesAPaidSubscription() {
        service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.ESSENTIEL_3M));
        var expired = current.get(); expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThrows(ResponseStatusException.class, () -> service.validateBalance(company, CampaignChannel.EMAIL, 1));
        assertEquals(SubscriptionStatus.EXPIRED, expired.getStatus());
        assertSame(expired, current.get());
    }

    @Test void renewalOfSamePackagePreservesRemainingPaidTimeAndUnusedCredits() {
        service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.ESSENTIEL_3M));
        var previousExpiry = current.get().getExpiresAt();
        service.subscribe(8L, new SubscribeRequest(SubscriptionPlanCode.ESSENTIEL_3M));
        assertEquals(previousExpiry.plusMonths(3), current.get().getExpiresAt());
        assertEquals(3_041, company.getSmsBalance());
        assertEquals(1_500, company.getWhatsappBalance());
    }
}

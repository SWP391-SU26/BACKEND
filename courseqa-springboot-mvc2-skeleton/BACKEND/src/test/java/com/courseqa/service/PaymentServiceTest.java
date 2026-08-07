package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.courseqa.config.VnpayProperties;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.PaymentOrder;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.model.entity.User;
import com.courseqa.repository.PaymentCallbackAuditRepository;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {
    private PaymentOrderRepository orders;
    private UserRepository users;
    private PlanService plans;
    private SubscriptionService subscriptions;
    private VnpayGatewayService gateway;
    private VnpaySettlementService settlement;
    private PaymentCallbackAuditService auditService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        orders = mock(PaymentOrderRepository.class);
        users = mock(UserRepository.class);
        plans = mock(PlanService.class);
        subscriptions = mock(SubscriptionService.class);
        gateway = mock(VnpayGatewayService.class);
        settlement = mock(VnpaySettlementService.class);
        auditService = mock(PaymentCallbackAuditService.class);
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TEST1234");
        properties.setHashSecret("test-secret");
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://example.test/return");
        properties.setIpnUrl("https://example.test/ipn");
        properties.setFrontendReturnUrl("http://localhost:5173/payment/result");
        service = new PaymentService(
                orders,
                mock(PaymentCallbackAuditRepository.class),
                users,
                plans,
                gateway,
                settlement,
                auditService,
                properties,
                subscriptions
        );
    }

    @Test
    void userPaymentsReturnsOnlyRequestedPageAndMapsOwnerDetails() {
        UUID userId = UUID.randomUUID();
        PaymentOrder order = paidOrder(userId);
        User user = new User();
        user.setUserId(userId);
        user.setEmail("student@example.com");
        user.setFullName("Student One");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(orders.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        PaymentDto.UserPaymentPageResponse result = service.userPayments(userId, "paid", 0, 20);

        assertEquals(1, result.totalElements);
        assertEquals(1, result.items.size());
        assertEquals(userId, result.items.get(0).userId);
        assertEquals("student@example.com", result.items.get(0).userEmail);
        assertEquals("PRO_PLUS", result.items.get(0).planCode);
        assertEquals("PAID", result.items.get(0).status);
        verify(orders).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void userPaymentsRejectsUnsupportedStatus() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.userPayments(UUID.randomUUID(), "refunded", 0, 20));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createOrderIsRejectedWhileAPlanIsStillActive() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserId(userId);
        user.setIsActive(true);
        SubscriptionPlan pro = new SubscriptionPlan();
        pro.setPlanId(UUID.randomUUID());
        pro.setPlanCode("PRO");
        pro.setPriceVnd(49_000L);
        pro.setDurationDays(30);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(plans.requireActive("PRO")).thenReturn(pro);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "A paid plan is already active."))
                .when(subscriptions).assertPurchaseAllowed(userId);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(userId, "PRO", "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void createOrderReturnsTheExistingPendingCheckoutInsteadOfRejectingTheUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserId(userId);
        user.setIsActive(true);
        SubscriptionPlan pro = new SubscriptionPlan();
        pro.setPlanId(UUID.randomUUID());
        pro.setPlanCode("PRO");
        pro.setPriceVnd(49_000L);
        pro.setDurationDays(30);
        PaymentOrder pending = paidOrder(userId);
        pending.setPlanCodeSnapshot("PRO");
        pending.setAmountVnd(49_000L);
        pending.setStatus("PENDING");
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(plans.requireActive("PRO")).thenReturn(pro);
        when(orders.findFirstByUserIdAndPlanCodeSnapshotAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                any(UUID.class), any(String.class), any(String.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(pending));
        when(gateway.createPaymentUrl(pending)).thenReturn("https://sandbox.vnpayment.vn/resume");

        PaymentDto.CreateOrderResponse response = service.createOrder(userId, "PRO", "127.0.0.1");

        assertEquals(pending.getPaymentOrderId(), response.orderId);
        assertEquals("https://sandbox.vnpayment.vn/resume", response.paymentUrl);
        assertEquals("PENDING", response.status);
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void verifiedReturnSettlesTheOrderWhenIpnIsUnavailable() {
        UUID userId = UUID.randomUUID();
        PaymentOrder order = paidOrder(userId);
        order.setStatus("PENDING");
        Map<String, String> raw = Map.of("vnp_SecureHash", "signed");
        Map<String, String> params = Map.of(
                "vnp_TxnRef", order.getVnpTxnRef(),
                "vnp_TmnCode", "TEST1234",
                "vnp_Amount", "9900000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"
        );
        when(gateway.verify(raw)).thenReturn(new VnpayGatewayService.CallbackVerification(true, params));
        when(orders.findByVnpTxnRef(order.getVnpTxnRef())).thenReturn(Optional.of(order));
        when(settlement.settle(params)).thenReturn(new VnpaySettlementService.SettlementResult(
                order, true, true, true, null, "00", "Confirm Success"));

        PaymentService.ReturnResult result = service.inspectReturn(raw, "127.0.0.1");

        assertTrue(result.valid());
        assertEquals(order.getPaymentOrderId(), result.orderId());
        verify(settlement).settle(params);
    }

    @Test
    void invalidReturnNeverSettlesTheOrder() {
        Map<String, String> raw = Map.of("vnp_SecureHash", "invalid");
        Map<String, String> params = Map.of("vnp_TxnRef", "PRO-invalid");
        when(gateway.verify(raw)).thenReturn(new VnpayGatewayService.CallbackVerification(false, params));

        PaymentService.ReturnResult result = service.inspectReturn(raw, "127.0.0.1");

        assertEquals(false, result.valid());
        assertEquals("INVALID_SIGNATURE", result.error());
        verify(settlement, never()).settle(any());
    }

    private static PaymentOrder paidOrder(UUID userId) {
        PaymentOrder order = new PaymentOrder();
        order.setPaymentOrderId(UUID.randomUUID());
        order.setUserId(userId);
        order.setVnpTxnRef("PROPLUS-20260805-test");
        order.setPlanCodeSnapshot("PRO_PLUS");
        order.setAmountVnd(99_000L);
        order.setDurationDays(30);
        order.setGateway("VNPAY");
        order.setStatus("PAID");
        order.setExpiresAt(LocalDateTime.now().plusDays(30));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}

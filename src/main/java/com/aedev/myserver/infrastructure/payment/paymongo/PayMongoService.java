package com.aedev.myserver.infrastructure.payment.paymongo;

import com.aedev.myserver.application.dto.subscription.CheckoutMetadata;
import com.aedev.myserver.domain.enums.PaymentMethodType;
import com.aedev.myserver.infrastructure.config.FrontendProperties;
import com.aedev.myserver.infrastructure.payment.paymongo.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Dedicated PayMongo integration service. This is the only class in the
 * application that talks to the PayMongo Checkout Sessions API --
 * controllers and business services never call PayMongo directly.
 */
@Service
public class PayMongoService {

    private static final String CHECKOUT_SESSIONS_PATH = "/v1/checkout_sessions";
    private static final String CURRENCY = "PHP";
    private static final List<String> PAYMENT_METHOD_TYPES =
            Stream.of(
                            PaymentMethodType.CARD,
                            PaymentMethodType.GCASH,
                            PaymentMethodType.PAYMAYA
                    )
                    .map(PaymentMethodType::getValue)
                    .toList();

    /**
     * Gives ObjectMapper#convertValue a concrete generic target
     * (Map<String, Object>) instead of the raw Map.class, which the
     * compiler cannot verify and which producers an unchecked-assignment
     * warning. TypeReference carries the full generic signature at
     * runtime via reflection on its own superclass, so Jackson builds an
     * actually-typed Map instead of a raw one.
     */
    private static final TypeReference<Map<String, Object>> METADATA_MAP_TYPE = new TypeReference<>() {
    };

    private final WebClient payMongoWebClient;
    private final FrontendProperties frontendProperties;
    private final ObjectMapper objectMapper;

    public PayMongoService(
            WebClient payMongoWebClient,
            FrontendProperties frontendProperties,
            ObjectMapper objectMapper
    ) {
        this.payMongoWebClient = payMongoWebClient;
        this.frontendProperties = frontendProperties;
        this.objectMapper = objectMapper;
    }

    public PayMongoCheckoutResult createCheckoutSession(
            boolean isChangedPlan,
            String planDisplayName,
            BigDecimal amount,
            CheckoutMetadata metadata
    ) {
        long amountInCentavos = amount
                .movePointRight(2)
                .longValueExact();

        PayMongoLineItem lineItem = new PayMongoLineItem(
                planDisplayName,
                amountInCentavos,
                CURRENCY,
                1
        );

        Map<String, Object> metadataMap = objectMapper.convertValue(metadata, METADATA_MAP_TYPE);
        PayMongoBilling billing = new PayMongoBilling(
                metadata.customerEmail(),
                metadata.customerName(),
                metadata.customerPhone()
        );

        PayMongoCheckoutAttributes attributes = new PayMongoCheckoutAttributes(
                billing,
                frontendProperties.url() + "/subscriptions/cancel?referenceNumber=" + metadata.referenceNumber(),
                frontendProperties.url() + "/subscriptions/success?referenceNumber=" + metadata.referenceNumber() + "&plan=" + metadata.packageId()
                + "&planName=" + planDisplayName + "&name=" + metadata.customerName() + "&email=" + metadata.customerEmail() + "&phone=" + metadata.customerPhone()
                + "&isChangedPlan=" + isChangedPlan,
                List.of(lineItem),
                PAYMENT_METHOD_TYPES,
                "Subscription - " + planDisplayName,
                metadataMap
        );

        PayMongoCheckoutRequestBody requestBody =
                PayMongoCheckoutRequestBody.wrap(attributes);

        PayMongoCheckoutResponse response = payMongoWebClient.post()
                .uri(CHECKOUT_SESSIONS_PATH)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PayMongoCheckoutResponse.class)
                .block();

        if (response == null || response.data() == null) {
            throw new PayMongoIntegrationException("PayMongo returned an empty checkout session response");
        }

        return new PayMongoCheckoutResult(
                response.data().attributes().checkoutUrl(),
                response.data().id()
        );
    }
}
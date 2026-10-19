package com.saas.product.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.product.extension.ecommerce.EcommerceExtension;
import com.saas.product.extension.fnb.FnbExtension;
import com.saas.product.extension.subscription.SubscriptionExtension;
import com.saas.product.spi.ProductExtension;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializes/deserializes {@link ProductExtension} instances to/from
 * plain Maps suitable for JSONB storage.
 *
 * To add a new extension:
 *  1. Implement ProductExtension
 *  2. Register its context key → Class mapping in REGISTRY below
 *  No other changes needed.
 */
@ApplicationScoped
public class ExtensionSerializer {

    private static final Logger LOG = Logger.getLogger(ExtensionSerializer.class);

    /**
     * Registry: context key → extension class.
     * Add new extension types here.
     */
    private static final Map<String, Class<? extends ProductExtension>> REGISTRY = Map.of(
            EcommerceExtension.CONTEXT,    EcommerceExtension.class,
            FnbExtension.CONTEXT,          FnbExtension.class,
            SubscriptionExtension.CONTEXT, SubscriptionExtension.class
    );

    @Inject
    ObjectMapper mapper;

    /**
     * Serialize all extensions to a Map<String, Object> for JSONB storage.
     */
    public Map<String, Object> serialize(Map<String, ProductExtension> extensions) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ProductExtension> entry : extensions.entrySet()) {
            try {
                // Convert to raw Map so Jackson can store it as JSONB
                Object raw = mapper.convertValue(entry.getValue(), Object.class);
                result.put(entry.getKey(), raw);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to serialize extension '%s'", entry.getKey());
            }
        }
        return result;
    }

    /**
     * Deserialize a raw JSONB Map back into typed ProductExtension instances.
     */
    public Map<String, ProductExtension> deserialize(Map<String, Object> raw) {
        Map<String, ProductExtension> result = new HashMap<>();
        if (raw == null) return result;

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String context    = entry.getKey();
            Class<? extends ProductExtension> clazz = REGISTRY.get(context);
            if (clazz == null) {
                LOG.warnf("Unknown extension context '%s' — skipping", context);
                continue;
            }
            try {
                ProductExtension ext = mapper.convertValue(entry.getValue(), clazz);
                result.put(context, ext);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to deserialize extension '%s'", context);
            }
        }
        return result;
    }
}

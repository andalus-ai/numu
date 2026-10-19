package tech.kayys.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;

@ApplicationScoped
@ServerEndpoint("/ws/payment-updates")
public class WebSocketService {
    
    private static Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    @OnOpen
    public void onOpen(Session session, @PathParam("merchantId") String merchantId) {
        sessions.put(merchantId, session);
    }
    
    @OnClose
    public void onClose(Session session, @PathParam("merchantId") String merchantId) {
        sessions.remove(merchantId);
    }
    
    public void sendPaymentUpdate(Payment payment, PaymentStatus previousStatus) {
        Session session = sessions.get(payment.merchantId);
        if (session != null && session.isOpen()) {
            try {
                WebhookResponse response = new WebhookResponse();
                response.transactionId = payment.transactionId;
                response.status = payment.status;
                response.previousStatus = previousStatus;
                response.merchantId = payment.merchantId;
                response.timestamp = LocalDateTime.now();
                response.eventType = "PAYMENT_STATUS_UPDATE";
                
                session.getBasicRemote().sendText(convertToJson(response));
            } catch (IOException e) {
                // Log error
            }
        }
    }
    
    private String convertToJson(WebhookResponse response) {
        // Implementation for JSON serialization
        return response.toString();
    }
}

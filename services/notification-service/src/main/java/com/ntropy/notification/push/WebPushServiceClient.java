package com.ntropy.notification.push;

import org.apache.http.HttpResponse;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

/** web-push 라이브러리(nl.martijndwars:web-push)를 실제로 호출하는 WebPushClient 구현체. */
@Component
@RequiredArgsConstructor
public class WebPushServiceClient implements WebPushClient {

    private final PushService pushService;

    @Override
    public int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
        Subscription subscription = new Subscription(endpoint, new Subscription.Keys(p256dh, auth));
        Notification notification = new Notification(subscription, payloadJson);
        HttpResponse response = pushService.send(notification);
        return response.getStatusLine().getStatusCode();
    }
}

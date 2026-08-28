package com.silentui;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationListener extends NotificationListenerService {

    public static NotificationListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        notifyService();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        notifyService();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        notifyService();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        notifyService();
    }

    public boolean isConnected() {
        return instance != null;
    }

    public List<StatusBarNotification> getActive() {
        StatusBarNotification[] arr = getActiveNotifications();
        if (arr == null) return new ArrayList<StatusBarNotification>();
        return new ArrayList<StatusBarNotification>(Arrays.asList(arr));
    }

    public void clearAll() {
        cancelAllNotifications();
        notifyService();
    }

    private void notifyService() {
        if (StatusBarService.instance != null) {
            StatusBarService.instance.onNotificationsChanged();
        }
    }
}

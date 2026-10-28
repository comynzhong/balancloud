package com.balancloud.mqtt;

import com.balancloud.mqtt.model.MqttLogEntry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MqttLogService {

    private static final int MAX_ENTRIES = 300;
    private static final int MAX_PAYLOAD_LENGTH = 5000;

    private final Deque<MqttLogEntry> entries = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized void record(String direction, String topic, String deviceNo, String payload) {
        String safePayload = payload == null ? "" : payload;
        if (safePayload.length() > MAX_PAYLOAD_LENGTH) {
            safePayload = safePayload.substring(0, MAX_PAYLOAD_LENGTH) + "...";
        }
        entries.addFirst(new MqttLogEntry(
                sequence.incrementAndGet(),
                LocalDateTime.now(),
                direction,
                topic,
                deviceNo,
                safePayload
        ));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized List<MqttLogEntry> list(String deviceNo, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<MqttLogEntry> result = new ArrayList<>(safeLimit);
        for (MqttLogEntry entry : entries) {
            if (!StringUtils.hasText(deviceNo) || deviceNo.equals(entry.deviceNo())) {
                result.add(entry);
                if (result.size() >= safeLimit) {
                    break;
                }
            }
        }
        return result;
    }

    public synchronized void clear() {
        entries.clear();
    }
}

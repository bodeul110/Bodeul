package com.example.bodeul.data.realtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class SupabaseRealtimeProtocol {
    private static final String TOPIC_PREFIX = "realtime:";

    private SupabaseRealtimeProtocol() {
    }

    static String join(String topic, String accessToken, String reference) throws JSONException {
        JSONObject broadcast = new JSONObject()
                .put("ack", false)
                .put("self", false);
        JSONObject presence = new JSONObject().put("enabled", false);
        JSONObject config = new JSONObject()
                .put("broadcast", broadcast)
                .put("presence", presence)
                .put("postgres_changes", new JSONArray())
                .put("private", true);
        JSONObject payload = new JSONObject()
                .put("config", config)
                .put("access_token", accessToken);
        return envelope(TOPIC_PREFIX + topic, "phx_join", payload, reference, reference)
                .toString();
    }

    static String heartbeat(String reference) throws JSONException {
        return envelope("phoenix", "heartbeat", new JSONObject(), reference, null).toString();
    }

    static boolean isSuccessfulJoin(String message, String expectedReference) {
        try {
            JSONObject envelope = new JSONObject(message);
            JSONObject payload = envelope.optJSONObject("payload");
            return "phx_reply".equals(envelope.optString("event"))
                    && expectedReference.equals(envelope.optString("ref"))
                    && payload != null
                    && "ok".equals(payload.optString("status"));
        } catch (JSONException exception) {
            return false;
        }
    }

    static boolean isJoinReply(String message, String expectedReference) {
        try {
            JSONObject envelope = new JSONObject(message);
            return "phx_reply".equals(envelope.optString("event"))
                    && expectedReference.equals(envelope.optString("ref"));
        } catch (JSONException exception) {
            return false;
        }
    }

    static boolean isStateEvent(String message, String expectedTopic) {
        try {
            JSONObject envelope = new JSONObject(message);
            String event = envelope.optString("event");
            return ("broadcast".equals(event) || "postgres_changes".equals(event))
                    && (TOPIC_PREFIX + expectedTopic).equals(envelope.optString("topic"));
        } catch (JSONException exception) {
            return false;
        }
    }

    private static JSONObject envelope(
            String topic,
            String event,
            JSONObject payload,
            String reference,
            String joinReference
    ) throws JSONException {
        JSONObject result = new JSONObject()
                .put("topic", topic)
                .put("event", event)
                .put("payload", payload)
                .put("ref", reference);
        if (joinReference != null) {
            result.put("join_ref", joinReference);
        }
        return result;
    }
}

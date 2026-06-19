package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.CompanionSession;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 동행 위치 공유 상태와 이력을 여러 화면에서 같은 기준으로 출력한다.
 */
public final class CompanionLocationDisplayHelper {
    private CompanionLocationDisplayHelper() {
    }

    public static String buildLiveSharingStatus(Context context, @Nullable CompanionSession session) {
        if (session == null) {
            return context.getString(R.string.live_location_status_inactive_empty);
        }
        if (session.isLiveLocationSharingActive()) {
            if (session.getLiveLocationSharingStartedAtMillis() > 0L) {
                return context.getString(
                        R.string.live_location_status_active_with_time,
                        formatSharedLocationTime(session.getLiveLocationSharingStartedAtMillis())
                );
            }
            return context.getString(R.string.live_location_status_active);
        }
        if (session.getSharedLocationUpdatedAtMillis() > 0L) {
            return context.getString(
                    R.string.live_location_status_inactive_with_time,
                    formatSharedLocationTime(session.getSharedLocationUpdatedAtMillis())
            );
        }
        return context.getString(R.string.live_location_status_inactive_empty);
    }

    public static String buildLocationHistory(
            Context context,
            @Nullable CompanionSession session,
            int maxEntries
    ) {
        List<CompanionLocationHistoryEntry> history = resolveHistoryEntries(session, maxEntries);
        if (!history.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < history.size(); index++) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(buildHistoryLine(context, history.get(index)));
            }
            return builder.toString();
        }
        return context.getString(R.string.live_location_history_empty);
    }

    public static List<CompanionLocationHistoryEntry> resolveHistoryEntries(
            @Nullable CompanionSession session,
            int maxEntries
    ) {
        List<CompanionLocationHistoryEntry> items = new ArrayList<>();
        if (session == null) {
            return items;
        }

        List<CompanionLocationHistoryEntry> history = session.getSharedLocationHistory();
        if (!history.isEmpty()) {
            int limit = Math.min(maxEntries, history.size());
            for (int index = 0; index < limit; index++) {
                items.add(history.get(index));
            }
            return items;
        }

        if (session.hasSharedLocationCoordinates() && session.getSharedLocationUpdatedAtMillis() > 0L) {
            items.add(new CompanionLocationHistoryEntry(
                    session.getSharedLatitude(),
                    session.getSharedLongitude(),
                    session.getLocationSummary(),
                    session.getSharedLocationUpdatedAtMillis()
            ));
        }
        return items;
    }

    public static String buildHistoryTimeLabel(CompanionLocationHistoryEntry entry) {
        return formatSharedLocationTime(entry.getCapturedAtMillis());
    }

    public static String buildHistoryValue(Context context, CompanionLocationHistoryEntry entry) {
        if (!TextUtils.isEmpty(entry.getSummary())) {
            return entry.getSummary();
        }
        return context.getString(
                R.string.live_location_history_coordinate_only,
                entry.getLatitude(),
                entry.getLongitude()
        );
    }

    public static String formatSharedLocationTime(long updatedAtMillis) {
        return new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
                .format(new Date(updatedAtMillis));
    }

    private static String buildHistoryLine(Context context, CompanionLocationHistoryEntry entry) {
        String timeText = formatSharedLocationTime(entry.getCapturedAtMillis());
        if (!TextUtils.isEmpty(entry.getSummary())) {
            return context.getString(
                    R.string.live_location_history_item,
                    timeText,
                    entry.getSummary()
            );
        }
        return context.getString(
                R.string.live_location_history_item_coordinate,
                timeText,
                entry.getLatitude(),
                entry.getLongitude()
        );
    }
}

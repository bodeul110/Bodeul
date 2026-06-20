package com.example.bodeul.ui.booking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingMeetingPointOption;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약 위치 선택 화면에서 지점 후보를 간단한 병원 지도 형태로 그려준다.
 */
public final class BookingLocationMapView extends View {
    public interface OnPointSelectedListener {
        void onPointSelected(BookingMeetingPointOption option);
    }

    private final Paint mapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF();
    private final List<BookingMeetingPointOption> pointOptions = new ArrayList<>();

    private String selectedPointId = "";
    private String highlightedPointId = "";
    private float markerRadius;
    @Nullable
    private OnPointSelectedListener onPointSelectedListener;

    public BookingLocationMapView(Context context) {
        this(context, null);
    }

    public BookingLocationMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mapPaint.setColor(ContextCompat.getColor(getContext(), R.color.bodeul_surface));
        surfacePaint.setColor(ContextCompat.getColor(getContext(), R.color.bodeul_outline));
        accentPaint.setColor(ContextCompat.getColor(getContext(), R.color.bodeul_soft_blue));
        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.bodeul_primary));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(12));
        markerRadius = dp(16);
        setClickable(true);
    }

    public void setPointOptions(List<BookingMeetingPointOption> pointOptions) {
        this.pointOptions.clear();
        if (pointOptions != null) {
            this.pointOptions.addAll(pointOptions);
        }
        invalidate();
    }

    public void setSelectedPointId(String selectedPointId) {
        this.selectedPointId = selectedPointId == null ? "" : selectedPointId.trim();
        invalidate();
    }

    public void setHighlightedPointId(String highlightedPointId) {
        this.highlightedPointId = highlightedPointId == null ? "" : highlightedPointId.trim();
        invalidate();
    }

    public void setOnPointSelectedListener(@Nullable OnPointSelectedListener listener) {
        this.onPointSelectedListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft() + dp(8);
        float top = getPaddingTop() + dp(8);
        float right = getWidth() - getPaddingRight() - dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(8);
        tempRect.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRect, dp(24), dp(24), mapPaint);

        // 병원 단지 느낌이 나도록 주요 영역을 단순 블록으로 나눈다.
        drawArea(canvas, left + dp(20), top + dp(28), left + dp(168), top + dp(148), accentPaint);
        drawArea(canvas, right - dp(168), top + dp(24), right - dp(20), top + dp(126), accentPaint);
        drawArea(canvas, left + dp(26), bottom - dp(114), left + dp(154), bottom - dp(26), surfacePaint);
        drawRoad(canvas, left + dp(40), bottom - dp(150), right - dp(40), bottom - dp(132));
        drawRoad(canvas, right - dp(118), top + dp(52), right - dp(100), bottom - dp(42));

        int markerNumber = 1;
        for (BookingMeetingPointOption option : pointOptions) {
            float x = left + (right - left) * option.getRelativeX();
            float y = top + (bottom - top) * option.getRelativeY();
            boolean selected = option.getId().equals(selectedPointId);
            boolean highlighted = option.getId().equals(highlightedPointId);
            if (highlighted && !selected) {
                markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.bodeul_secondary));
                canvas.drawCircle(x, y, markerRadius + dp(5), markerPaint);
            }
            markerPaint.setColor(ContextCompat.getColor(
                    getContext(),
                    selected ? R.color.bodeul_primary : R.color.bodeul_text_secondary
            ));
            canvas.drawCircle(x, y, selected ? markerRadius + dp(4) : markerRadius, markerPaint);
            canvas.drawText(String.valueOf(markerNumber), x, y + dp(4), textPaint);
            markerNumber++;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        BookingMeetingPointOption tappedOption = findNearestOption(event.getX(), event.getY());
        if (tappedOption != null) {
            performClick();
            if (onPointSelectedListener != null) {
                onPointSelectedListener.onPointSelected(tappedOption);
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Nullable
    private BookingMeetingPointOption findNearestOption(float touchX, float touchY) {
        float left = getPaddingLeft() + dp(8);
        float top = getPaddingTop() + dp(8);
        float right = getWidth() - getPaddingRight() - dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(8);
        float threshold = dp(36);
        BookingMeetingPointOption result = null;
        for (BookingMeetingPointOption option : pointOptions) {
            float x = left + (right - left) * option.getRelativeX();
            float y = top + (bottom - top) * option.getRelativeY();
            float dx = x - touchX;
            float dy = y - touchY;
            if ((dx * dx) + (dy * dy) <= threshold * threshold) {
                result = option;
                break;
            }
        }
        return result;
    }

    private void drawArea(Canvas canvas, float left, float top, float right, float bottom, Paint paint) {
        tempRect.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRect, dp(18), dp(18), paint);
    }

    private void drawRoad(Canvas canvas, float left, float top, float right, float bottom) {
        tempRect.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRect, dp(14), dp(14), surfacePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

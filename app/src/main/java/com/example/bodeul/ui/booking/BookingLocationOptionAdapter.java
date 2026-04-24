package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 위치 선택 후보 목록을 카드 형태로 렌더링한다.
 */
public final class BookingLocationOptionAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<BookingMeetingPointOption> options = new ArrayList<>();
    private String selectedPointId = "";

    public BookingLocationOptionAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void submitList(List<BookingMeetingPointOption> items) {
        options.clear();
        if (items != null) {
            options.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void setSelectedPointId(String selectedPointId) {
        this.selectedPointId = selectedPointId == null ? "" : selectedPointId.trim();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return options.size();
    }

    @Override
    public BookingMeetingPointOption getItem(int position) {
        return options.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_booking_location_option, parent, false);
            holder = new ViewHolder(
                    convertView.findViewById(R.id.cardBookingLocationOption),
                    convertView.findViewById(R.id.textBookingLocationOptionBadge),
                    convertView.findViewById(R.id.textBookingLocationOptionTitle),
                    convertView.findViewById(R.id.textBookingLocationOptionBody),
                    convertView.findViewById(R.id.textBookingLocationOptionPlace)
            );
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BookingMeetingPointOption option = getItem(position);
        boolean selected = option.getId().equals(selectedPointId);
        holder.card.setStrokeWidth(selected ? 3 : 1);
        holder.card.setChecked(selected);
        holder.textBadge.setText(parent.getContext().getString(
                selected
                        ? R.string.booking_location_option_badge_selected
                        : R.string.booking_location_option_badge_default
        ));
        holder.textTitle.setText(option.getTitle());
        holder.textBody.setText(option.getDescription());
        holder.textPlace.setText(option.getMeetingPlace());
        return convertView;
    }

    private static final class ViewHolder {
        private final MaterialCardView card;
        private final TextView textBadge;
        private final TextView textTitle;
        private final TextView textBody;
        private final TextView textPlace;

        private ViewHolder(
                MaterialCardView card,
                TextView textBadge,
                TextView textTitle,
                TextView textBody,
                TextView textPlace
        ) {
            this.card = card;
            this.textBadge = textBadge;
            this.textTitle = textTitle;
            this.textBody = textBody;
            this.textPlace = textPlace;
        }
    }
}

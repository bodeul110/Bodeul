package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalOption;

import java.util.ArrayList;
import java.util.List;

/**
 * 병원 선택 목록을 카드 형태로 렌더링한다.
 */
public final class BookingHospitalOptionAdapter extends BaseAdapter {
    private final LayoutInflater layoutInflater;
    private final List<BookingHospitalOption> options = new ArrayList<>();

    public BookingHospitalOptionAdapter(Context context) {
        this.layoutInflater = LayoutInflater.from(context);
    }

    public void submitList(List<BookingHospitalOption> items) {
        options.clear();
        if (items != null) {
            options.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return options.size();
    }

    @Override
    public BookingHospitalOption getItem(int position) {
        return options.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_booking_hospital_option, parent, false);
            viewHolder = new ViewHolder(
                    convertView.findViewById(R.id.textBookingHospitalOptionBadge),
                    convertView.findViewById(R.id.textBookingHospitalOptionName),
                    convertView.findViewById(R.id.textBookingHospitalOptionDepartments)
            );
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        BookingHospitalOption option = getItem(position);
        viewHolder.textBadge.setText(parent.getContext().getString(
                R.string.booking_hospital_selector_count_badge,
                option.getDepartmentCount()
        ));
        viewHolder.textName.setText(option.getHospitalName());
        viewHolder.textDepartments.setText(TextUtils.join(" · ", option.getDepartmentNames()));
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView textBadge;
        private final TextView textName;
        private final TextView textDepartments;

        private ViewHolder(
                TextView textBadge,
                TextView textName,
                TextView textDepartments
        ) {
            this.textBadge = textBadge;
            this.textName = textName;
            this.textDepartments = textDepartments;
        }
    }
}

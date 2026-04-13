package com.example.bodeul.ui.common;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;

/**
 * 아직 구현되지 않은 기능 화면에 공통 안내 UI를 제공하는 베이스 액티비티다.
 */
public abstract class FeaturePlaceholderActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feature_placeholder);
        setTitle(getTitleResId());

        // 자식 화면이 넘겨준 제목과 설명 리소스를 공통 플레이스홀더에 바인딩한다.
        TextView title = findViewById(R.id.featureTitle);
        TextView description = findViewById(R.id.featureDescription);
        title.setText(getTitleResId());
        description.setText(getDescriptionResId());
    }

    @StringRes
    // 플레이스홀더 상단에 표시할 기능 이름을 반환한다.
    protected abstract int getTitleResId();

    @StringRes
    // 아직 미구현인 기능 설명 문구를 반환한다.
    protected abstract int getDescriptionResId();
}

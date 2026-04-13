package com.example.bodeul;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * 단말 또는 에뮬레이터에서 실행되는 기본 계측 테스트다.
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // 앱 컨텍스트의 패키지명이 예상값과 일치하는지 확인한다.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.bodeul", appContext.getPackageName());
    }
}

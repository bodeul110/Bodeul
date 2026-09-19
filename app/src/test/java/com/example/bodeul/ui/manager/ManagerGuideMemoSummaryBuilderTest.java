package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ManagerGuideMemoSummaryBuilderTest {
    @Test
    public void keepsOrderMissingEntryNewlinesAndLongOriginalText() {
        String longBody = "첫 줄\n" + "긴 기록 ".repeat(80).trim();

        List<ManagerGuideMemoItem> items = ManagerGuideMemoSummaryBuilder.build(
                List.of(
                        new ManagerGuideMemoSummaryBuilder.Candidate("이동", ""),
                        new ManagerGuideMemoSummaryBuilder.Candidate("현장", longBody),
                        new ManagerGuideMemoSummaryBuilder.Candidate("복약", "약 변경 없음")),
                "작성된 메모 없음");

        assertEquals(3, items.size());
        assertEquals("이동", items.get(0).getTitle());
        assertEquals("작성된 메모 없음", items.get(0).getBody());
        assertTrue(items.get(0).isEmpty());
        assertEquals(longBody, items.get(1).getBody());
        assertFalse(items.get(1).isEmpty());
        assertEquals("복약", items.get(2).getTitle());
    }

    @Test
    public void sameOriginalTextIsRenderedOnceWithCombinedTitles() {
        List<ManagerGuideMemoItem> items = ManagerGuideMemoSummaryBuilder.build(
                List.of(
                        new ManagerGuideMemoSummaryBuilder.Candidate("보호자 전달", "동일한 원문"),
                        new ManagerGuideMemoSummaryBuilder.Candidate("현장 확인", " 동일한 원문 "),
                        new ManagerGuideMemoSummaryBuilder.Candidate("약국", "별도 원문")),
                "작성된 메모 없음");

        assertEquals(2, items.size());
        assertEquals("보호자 전달 · 현장 확인", items.get(0).getTitle());
        assertEquals("동일한 원문", items.get(0).getBody());
        assertEquals("별도 원문", items.get(1).getBody());
    }

    @Test
    public void rebuildingForAnotherSessionDoesNotKeepPreviousText() {
        List<ManagerGuideMemoItem> firstSession = ManagerGuideMemoSummaryBuilder.build(
                List.of(new ManagerGuideMemoSummaryBuilder.Candidate("현장", "첫 세션 기록")),
                "작성된 메모 없음");
        List<ManagerGuideMemoItem> secondSession = ManagerGuideMemoSummaryBuilder.build(
                List.of(new ManagerGuideMemoSummaryBuilder.Candidate("현장", "")),
                "작성된 메모 없음");

        assertEquals("첫 세션 기록", firstSession.get(0).getBody());
        assertEquals("작성된 메모 없음", secondSession.get(0).getBody());
        assertTrue(secondSession.get(0).isEmpty());
    }
}

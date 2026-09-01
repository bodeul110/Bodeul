begin;
set local role bodeul_migration;

do $$
begin
    if not bodeul.is_valid_guide_step_media_v1(
        '[{"code":"LEGACY_STEP","order":1,"title":"기존 단계","description":"기존 설명"}]'::jsonb
    ) then
        raise exception '기존 4필드 가이드 snapshot이 영상 메타데이터 검증에서 거부되었습니다.';
    end if;

    if not bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"VIDEO_STEP",
          "order":1,
          "title":"길안내",
          "description":"영상으로 이동 경로를 확인합니다.",
          "videoAssetId":"guide-video-fixture",
          "videoAssetVersion":"v1",
          "videoFallbackText":"영상 없이 단계 설명을 확인해 주세요."
        }]'::jsonb
    ) then
        raise exception '정상 영상 메타데이터가 거부되었습니다.';
    end if;

    if not bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"NULLABLE_VIDEO_STEP",
          "order":1,
          "title":"영상 없는 단계",
          "description":"기존 안내를 사용합니다.",
          "videoAssetId":null,
          "videoAssetVersion":null,
          "videoFallbackText":null
        }]'::jsonb
    ) then
        raise exception '명시적 null 영상 메타데이터가 거부되었습니다.';
    end if;

    if bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"MISSING_VERSION",
          "order":1,
          "title":"잘못된 단계",
          "description":"버전이 없습니다.",
          "videoAssetId":"guide-video-fixture"
        }]'::jsonb
    ) then
        raise exception '버전 없는 영상 자산 ID가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"MISSING_FALLBACK",
          "order":1,
          "title":"잘못된 단계",
          "description":"대체 안내가 없습니다.",
          "videoAssetId":"guide-video-fixture",
          "videoAssetVersion":"v1"
        }]'::jsonb
    ) then
        raise exception '대체 안내 없는 영상 메타데이터가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"INVALID_FALLBACK",
          "order":1,
          "title":"잘못된 단계",
          "description":"대체 안내 형식이 잘못되었습니다.",
          "videoFallbackText":42
        }]'::jsonb
    ) then
        raise exception '문자열이 아닌 영상 대체 안내가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_step_media_v1(
        '[{
          "code":"BLANK_FALLBACK",
          "order":1,
          "title":"잘못된 단계",
          "description":"대체 안내가 비어 있습니다.",
          "videoAssetId":"guide-video-fixture",
          "videoAssetVersion":"v1",
          "videoFallbackText":" "
        }]'::jsonb
    ) then
        raise exception '빈 영상 대체 안내가 허용되었습니다.';
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'bodeul.hospital_guides'::regclass
          and conname = 'ck_hospital_guides_step_media_shape'
          and convalidated
    ) then
        raise exception '병원 가이드 영상 메타데이터 제약이 검증 상태가 아닙니다.';
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'bodeul.companion_sessions'::regclass
          and conname = 'ck_companion_sessions_guide_media_shape'
          and convalidated
    ) then
        raise exception '동행 snapshot 영상 메타데이터 제약이 검증 상태가 아닙니다.';
    end if;
end;
$$;

rollback;

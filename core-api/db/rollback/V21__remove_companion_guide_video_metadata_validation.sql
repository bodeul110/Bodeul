begin;

alter table bodeul.companion_sessions
    drop constraint if exists ck_companion_sessions_guide_media_shape;

alter table bodeul.hospital_guides
    drop constraint if exists ck_hospital_guides_step_media_shape;

drop function if exists bodeul.is_valid_guide_step_media_v1(jsonb);

commit;

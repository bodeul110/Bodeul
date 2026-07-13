-- 개발 DB에서 V1 적용 자체를 되돌려야 할 때만 사용한다.
-- app_users 데이터가 생긴 뒤에는 먼저 백업하고 참조 테이블이 없는지 확인한다.
drop table if exists bodeul.app_users;

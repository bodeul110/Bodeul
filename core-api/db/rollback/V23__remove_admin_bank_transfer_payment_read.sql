begin;
set local role bodeul_migration;

-- 조회 계약만 제거하며 원장, 결제 이벤트, 관리자 접근 감사는 보존한다.
drop function bodeul.get_admin_bank_transfer_payment(uuid, uuid);

commit;

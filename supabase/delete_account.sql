-- SuperDialer: "계정 삭제" (설정 > 계정 > 계정 삭제)가 부르는 서버 함수.
--
-- 로그인한 사용자 "본인" 계정만 지운다 (auth.uid()). 먼저 이 계정이 가진 행을 지우고 마지막에 계정을 지운다.
-- 외래키에 ON DELETE CASCADE가 없어서 이 순서가 필요하다.
--
-- 적용 방법: Supabase 대시보드 > SQL Editor 에 이 파일 내용을 붙여넣고 Run.
-- 되돌리기:  drop function public.superdialer_delete_account();
--
-- 지우는 것: superdialer_scores(랭킹), superdialer_sync(동기화 스냅샷), checklist_data(체크리스트 노트), auth.users(계정)

create or replace function public.superdialer_delete_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not signed in';
  end if;

  delete from public.superdialer_scores where user_id = uid;
  delete from public.superdialer_sync where user_id = uid;
  delete from public.checklist_data where user_id = uid;
  delete from auth.users where id = uid;
end;
$$;

-- 로그인한 사용자만 부를 수 있다 (익명 키로는 불가)
revoke all on function public.superdialer_delete_account() from public, anon;
grant execute on function public.superdialer_delete_account() to authenticated;

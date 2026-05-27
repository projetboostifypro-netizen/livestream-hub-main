
REVOKE EXECUTE ON FUNCTION public.claim_session(text) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.check_session(text) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_block_user(uuid, text) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_unblock_user(uuid) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_delete_user(uuid) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_adjust_coins(uuid, integer, text) FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_list_users() FROM anon, public;
REVOKE EXECUTE ON FUNCTION public.admin_list_transactions(integer) FROM anon, public;

GRANT EXECUTE ON FUNCTION public.claim_session(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.check_session(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_block_user(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_unblock_user(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_delete_user(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_adjust_coins(uuid, integer, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_list_users() TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_list_transactions(integer) TO authenticated;

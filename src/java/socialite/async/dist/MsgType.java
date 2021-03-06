package socialite.async.dist;

public enum MsgType {
    REPORT_MYIDX,
    NOTIFY_INIT,
    CLEAR_DATA,
    CLEAR_DATA_DONE,
    MESSAGE_TABLE, //发送buffer table给worker
    REQUIRE_TERM_CHECK,//Master require all works to compute partial value
    TERM_CHECK_PARTIAL_VALUE, //worker向master发送Δ之和
    TERM_CHECK_FEEDBACK,//master accumulate partial value to determine terminate or not.
    EXIT
}

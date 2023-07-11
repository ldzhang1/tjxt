package com.tianji.learning.constants;

public interface RedisConstants {
    /**
     * 签到记录的Key的前缀：sign:uid:110:202301
     */
    String SIGN_RECORD_KET_PREFIX = "sign:uid:";

    /**
     * 积分排行榜key前缀   完整格式为  boards:年月
     */
    String POINTS_BOARD_KEY_PREFIX = "boards:";
}

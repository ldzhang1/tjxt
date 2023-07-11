package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import io.lettuce.core.BitFieldArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService{

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
        // 1.获取用户id
        Long userId = UserContext.getUser();
        // 2.拼接key
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyMM"));//得到“:年月”
        String key = RedisConstants.SIGN_RECORD_KET_PREFIX +userId.toString() +format;

        // 3.利用bitset命令  将签到记录保存到redis的bitmap结构中   需要校验是否已签到
        int offset = now.getDayOfMonth() - 1;
        Boolean setBit = redisTemplate.opsForValue().setBit(key, offset, true);
        if(setBit){
            //说明当前已经签到过了
            throw new BizIllegalException("不能重复签到");
        }
        // 4.就散连续签到天数
        int days = countSignDays(key, now.getDayOfMonth());

        // 5.计算连续签到  奖励积分
        int rewardPoints = 0;
        switch (days){
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }

        // 6.保存积分  发消息到mq
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));

        // 7.封装vo返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(days);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    /**
     * 计算连续签到多少天
     * @param key
     * @param dayOfMonth  本月第一天到今天的  天数
     * @return
     */
    private int countSignDays(String key, int dayOfMonth) {
        // 1.求本月第一天到今天所有签到数据  bitFiled  得到的是十进制
        // bitFiled key get u天数  0
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)){
            return 0;
        }
        Long num = bitField.get(0);// 本月第一天到今天的签到数据  拿到的十进制
        log.debug("num  {}",num);
        // 2.num转二进制  从后往前退共有多少个1   与运算&  右移一位
        int counter = 0;
        while ((num & 1) == 1){
            counter ++;
            num >>>= 1; // 无符号右移一位
        }
        return counter;
    }

    @Override
    public Byte[] querySignRecords() {
        // 1.获得当前登录用户
        Long userId = UserContext.getUser();
        // 2.拼接key
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyMM"));
        String key = RedisConstants.SIGN_RECORD_KET_PREFIX + userId + format;

        // 3.利用redis bitfield命令取得本月第一天到今天所有签到记录
        int dayOfMonth = now.getDayOfMonth();
        // bitfield key get u天数 0
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)){
            return new Byte[0];
        }
        Long num = bitField.get(0);
        int offset = dayOfMonth - 1;
        Byte[] arr = new Byte[dayOfMonth];
        while (offset >= 0){
            arr[offset] = (byte) (num & 1);
            offset--;
            num >>>= 1; // 无符号右移
        }
        return arr;
    }
}

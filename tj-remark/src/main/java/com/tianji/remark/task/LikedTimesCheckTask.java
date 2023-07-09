package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE"); // 业务类型
    private static final int MAX_BIZ_SIZE = 30; //任务每次取的biz数量,防止一次性往mq发送太多消息

    private final ILikedRecordService likedRecordService;

    // 2.每20秒执行一次  将redis中  业务类型  下面  某业务的点赞总数  发消息到mq
    // @Scheduled(cron = "0/20 * * * * ?")
    @Scheduled(fixedDelay = 20000) // 每隔20秒执行一次
    public void checkLikeTimes(){
        for (String bizType :BIZ_TYPES){
            likedRecordService.readLikedTimeAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }
}

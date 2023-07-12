package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    /*@Scheduled(cron = "0 0 3 1 * ?")*/  // 每月1号，凌晨3点执行
    // 学习xxljob使用
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        // 1.获取上月时间
        LocalDate time = LocalDate.now().minusMonths(1);
        // 2.查询赛季id
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (season == null){
            // 赛季不存在
            return;
        }
        // 3.创建表
        pointsBoardService.createPointBoardTableBySeason(season.getId());
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        // 1.获取上月时间
        LocalDate time = LocalDate.now().minusMonths(1);
        // 2.查询赛季信息
        PointsBoardSeason season = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息{}", season);
        if (season == null){
            // 赛季不存在
            return;
        }
        // 3.计算动态表名，并存入threadLocal
        String tableName = LearningConstants.POINTS_BOARD_TABLE_PREFIX + season.getId();
        log.debug("动态表名为{}", tableName);
        TableInfoContext.setInfo(tableName);

        // 4.分页获取redis上赛季积分排行榜数据

        String format = time.format(DateTimeFormatter.ofPattern("yyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;

        // 查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1; // 起始页，就是分片序号0 + 1
        int pageSize = 5;
        while (true) {
            log.debug("处理的是第{}页数据",pageNo);
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize); // 查redis
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4.持久化到数据库
            // 4.1.把排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页，跳过N个页，N就是分片数量
            pageNo += total;
        }
        // 任务结束，移除动态表名
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDate time = LocalDate.now().minusMonths(1);
        // 2.计算key
        String format = time.format(DateTimeFormatter.ofPattern("yyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 3.删除
        redisTemplate.unlink(key);
    }

}

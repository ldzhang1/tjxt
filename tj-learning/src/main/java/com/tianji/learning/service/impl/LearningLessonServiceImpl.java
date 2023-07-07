package com.tianji.learning.service.impl;

import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author ldzhang
 * @since 2023-07-05
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;
    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1.通过feign远程调用课程服务   得到课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)){
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.批量封装po实体类，填充过期时间
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量保存
        this.saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.分页查询
        // select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0, 5
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.远程调用课程服务，给vo中的课程名  封面  章节数赋值
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfos)){
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.1.把课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 4.封装VO返回
        ArrayList<LearningLessonVO> voList = new ArrayList<>();
        // 4.1.循环遍历，把LearningLesson的po转为vo
        for (LearningLesson record : records) {
            // 4.2.拷贝基础属性vo
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            // 4.3.获取课程信息，填充到vo
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());
            if (infoDTO != null){
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl(infoDTO.getCoverUrl());
                vo.setSections(infoDTO.getSectionNum());
            }
            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null){
            return null;
        }
        // 3.拷贝po基础属性到vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        // 4.查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null){
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)){
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        // 1.获取当前登录的用户id
        Long userId = UserContext.getUser();
        // 2.判断用户课表是否有该课程
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId).one();
        if (lesson == null){
            return null;
        }
        // 3.判断课程是否过期
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)){
            return null;
        }
        // 4.返回课表id
        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        // 1.获取当前登录的用户id
        Long userId = UserContext.getUser();
        // 2.判断用户课表是否有该课程
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId).one();
        if (lesson == null){
            return null;
        }
        // 3.po转vo
        LearningLessonVO learningLessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        return learningLessonVO;
    }

    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        // 1.获取当前登录用户
        if (userId == null) {
            userId = UserContext.getUser();
        }
        // 2.删除课程
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        // select count(1) from xx where course_id = #{cc} AND status in (0, 1, 2)
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN.getValue(),
                        LessonStatus.LEARNING.getValue(),
                        LessonStatus.FINISHED.getValue())
                .count();
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询课表中的指定课程有关的数据
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId).one();
        AssertUtils.isNotNull(lesson, "课程信息不存在！");

        // 3.修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }
    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        return queryWrapper;
    }


    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // TODO 2.查询积分
        // 3.查询本周学习计划总数
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");//查询哪些列
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        if (map != null &&map.get("plansTotal")!=null){
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
        // 4.查询本周 实际 已学习的小节信息
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        Integer weekFinishedPlanNum = recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
        // 5.查询课表数据
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }

        // 6.远程调用课程服务 获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfos)){
            throw new BizIllegalException("课程不存在");
        }
        // 将cInfo list结构转为map
        Map<Long, CourseSimpleInfoDTO> cInfosMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 7.查询学习记录表 本周 当前用户下 每一门课下 已学习的小节数
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id as lessonId", "count(*) as userId");
        rWrapper.eq("user_id", userId);
        rWrapper.eq("finished", true);
        rWrapper.between("finish_time", weekBeginTime, weekEndTime);
        rWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = recordMapper.selectList(rWrapper);

        Map<Long, Long> courseWeekFinishNumMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getId()));

        // 8.封装vo返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);

        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVo = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO infoDTO = cInfosMap.get(record.getCourseId());
            if(infoDTO != null){
                planVo.setCourseName(infoDTO.getName());
                planVo.setSections(infoDTO.getSectionNum());
            }
            planVo.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(), 0L).intValue());
            voList.add(planVo);
        }
        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }

}

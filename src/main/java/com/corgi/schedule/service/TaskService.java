package com.corgi.schedule.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.messages.RecommendCalculater;
import com.corgi.common.messages.TraceFollow;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiStatisticService;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.ActivityLike;
import com.corgi.user.entity.UserPosition;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class TaskService {
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Autowired
    private MQService mqService;

    public void countUserTrace(String date) {
        log.info("into count user trace...");
        List<HashMap> traces = corgiStatisticService.countUserTrace(date);
        log.info(traces + "...traces");
        Double total = corgiStatisticService.countTotalUserTrace(date);
        corgiStatisticService.addUserTraceSum(date, TraceFollow.TOTAL, total);
        if (!CollectionUtils.isEmpty(traces)) {
            for (HashMap trace : traces) {
                log.info("trace..." + trace);
                corgiStatisticService.addUserTraceSum(date, (String) trace.get("type"), Double.parseDouble(trace.get("time") + ""));
            }
        }
    }

    public void calculateRecommendActivity(){
        int page = 1;
        int pageSize = 1000;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String time = sdf.format(calendar.getTime());
        List<String> refreshedUsers = new ArrayList<>();
        do {
            List<ActivityLike> likes = corgiLikeService.getLikeByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(likes)) {
                break;
            }
            for (ActivityLike like : likes) {
                if (time.compareTo(like.getCtime()) > 0) {
                    return;
                }
                if (refreshedUsers.contains(like.getLikeUserId())) {
                    continue;
                }
                RecommendCalculater calculater = new RecommendCalculater();
                calculater.setUserId(like.getLikeUserId());
                log.info("recommend activity..." + calculater.getUserId());
                mqService.sendActivityCalculater(calculater);
                refreshedUsers.add(like.getLikeUserId());
            }
        } while (true);
    }

    public void calculateRecommend() {
        int page = 1;
        int pageSize = 1000;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -90);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String time = sdf.format(calendar.getTime());
        List<String> refreshedUsers = new ArrayList<>();
        do {
            List<UserProfile> profiles = corgiUserFollowService.getAllFollowUserByPage(page, pageSize);
            page++;
            if (CollectionUtils.isEmpty(profiles)) {
                break;
            }
            for (UserProfile profile : profiles) {
                if (time.compareTo(profile.getCreateTime()) > 0) {
                    return;
                }
                if (refreshedUsers.contains(profile.getUserId())) {
                    continue;
                }
                RecommendCalculater calculater = new RecommendCalculater();
                calculater.setUserId(profile.getUserId());
                log.info("recommend..." + calculater.getUserId());
                mqService.sendCalculater(calculater);
                refreshedUsers.add(profile.getUserId());
            }
        } while (true);
    }

}

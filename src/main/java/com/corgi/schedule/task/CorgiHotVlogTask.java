package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.ActivityLike;
import com.corgi.user.entity.CorgiVlogHot;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
public class CorgiHotVlogTask {
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiLikeService corgiLikeService;


    @Async
    @Scheduled(cron = "0 0 * * * *")
    //@Scheduled(fixedRate = 3600 * 1000)
    public void run() {
        int page = 1;
        int size = 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<String> activityList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 90);
        String hourAgo = sdf.format(calendar.getTime());
        boolean shouldContinue = true;
        CorgiVlogHot queryHot = new CorgiVlogHot();
        queryHot.setStatus(CorgiVlogHot.STATUS.OPEN);
        queryHot.setType(CorgiVlogHot.TYPE.AUTO);
        do {
            List<ActivityLike> likeList = corgiLikeService.getLikeByPage(page, size);
            if (CollectionUtils.isEmpty(likeList)) {
                break;
            }
            for (ActivityLike like : likeList) {
                if (hourAgo.compareTo(like.getCtime()) > 0) {
                    shouldContinue = false;
                    break;
                }
                String activityId = like.getActivityId();
                if (activityList.contains(activityId)) {
                    continue;
                }
                activityList.add(activityId);
                queryHot.setActivityId(activityId);
                List<CorgiVlogHot> tmpList = corgiVlogService.getHotVlog(queryHot, 1, 1);
                Long likeCount = corgiLikeService.countActivityLike(activityId);
                if (tmpList.size() > 0) {
                    CorgiVlogHot hot = tmpList.get(0);
                    if (likeCount * 10 > hot.getExpectView()) {
                        CorgiVlogHot updateHot = new CorgiVlogHot();
                        updateHot.setId(hot.getId());
                        updateHot.setLikeCount(likeCount.intValue());
                        updateHot.setExpectView(likeCount.intValue() * 10);
                        corgiVlogService.updateHotVlog(updateHot);
                    }
                } else {
                    CorgiVlogHot addHot = new CorgiVlogHot();
                    addHot.setActivityId(activityId);
                    addHot.setExpectView(likeCount.intValue() * 10);
                    addHot.setLikeCount(likeCount.intValue());
                    addHot.setType(CorgiVlogHot.TYPE.AUTO);
                    corgiVlogService.addHotVlog(addHot);
                }

            }
            page++;
        } while (shouldContinue);

    }

}

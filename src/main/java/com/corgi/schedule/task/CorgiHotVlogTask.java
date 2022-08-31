package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.ActivityLike;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.CorgiVlogHot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiHotVlogTask {
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;


    @Async
    @Scheduled(cron = "0 0/1 * * * *")
    //@Scheduled(fixedRate = 3600 * 1000)
    public void run() {
        int page = 1;
        int size = 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<String> activityList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -5);
        String hourAgo = sdf.format(calendar.getTime());
        boolean shouldContinue = true;
        CorgiVlogHot queryHot = new CorgiVlogHot();
        queryHot.setStatus(CorgiVlogHot.STATUS.OPEN);
        queryHot.setType(CorgiVlogHot.TYPE.AUTO);
        do {
            List<ActivityLike> likeList = corgiLikeService.getLikeByPage(page, size);
            log.info("like size:{} ", likeList.size());
            if (CollectionUtils.isEmpty(likeList)) {
                break;
            }
            for (ActivityLike like : likeList) {
                log.info("like:{} ,hourAgo:{} ,likeTime:{} ,result:{} ", like.getActivityId(), hourAgo, like.getCtime(), hourAgo.compareTo(like.getCtime()));
                if (hourAgo.compareTo(like.getCtime()) > 0) {
                    shouldContinue = false;
                    break;
                }

                String activityId = like.getActivityId();
                if (activityList.contains(activityId)) {
                    continue;
                }
                activityList.add(activityId);
                CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
                if (!CorgiActivity.CAT_IMAGE.equals(activity.getCategory())
                        && !CorgiActivity.CAT_VIDEO.equals(activity.getCategory())
                        && !CorgiActivity.CAT_PAYING.equals(activity.getCategory())
                        && !CorgiActivity.CAT_TEXT.equals(activity.getCategory())) {
                    continue;
                }

                Long totalCount = corgiLikeService.countActivityLike(activityId);
                corgiActivityService.updateByColumn(activityId, "likeCount", totalCount + "");

                Integer likeCount = corgiLikeService.countRealActivityLike(activityId);
                queryHot.setActivityId(activityId);
                List<CorgiVlogHot> tmpList = corgiVlogService.getHotVlog(queryHot, 1, 1);
                Integer expectView = this.getExpectView(likeCount);
                if (tmpList.size() > 0) {
                    CorgiVlogHot hot = tmpList.get(0);
                    if (expectView > hot.getExpectView()) {
                        CorgiVlogHot updateHot = new CorgiVlogHot();
                        updateHot.setId(hot.getId());
                        updateHot.setLikeCount(likeCount);
                        updateHot.setExpectView(expectView);
                        corgiVlogService.updateHotVlog(updateHot);
                    }
                } else if (likeCount >= 4) {
                    CorgiVlogHot addHot = new CorgiVlogHot();
                    addHot.setActivityId(activityId);
                    addHot.setExpectView(expectView);
                    addHot.setLikeCount(likeCount);
                    addHot.setType(CorgiVlogHot.TYPE.AUTO);
                    corgiVlogService.addHotVlog(addHot);
                }
                queryHot.setType(CorgiVlogHot.TYPE.MANUAL);
                List<CorgiVlogHot> manualList = corgiVlogService.getHotVlog(queryHot, 1, 10);
                if (!CollectionUtils.isEmpty(manualList)) {
                    for (CorgiVlogHot hot : manualList) {
                        CorgiVlogHot updateHot = new CorgiVlogHot();
                        updateHot.setId(hot.getId());
                        updateHot.setLikeCount(likeCount);
                        corgiVlogService.updateHotVlog(updateHot);
                    }
                }
                queryHot.setType(CorgiVlogHot.TYPE.AUTO);
            }
            page++;
        } while (shouldContinue);

    }

    private Integer getExpectView(Integer likeCount) {
        if (likeCount == null || likeCount < 0) {
            return 0;
        }
        Integer expectView = new Double(Math.pow(likeCount, 1.5) * 10 + 500).intValue();
        return expectView;
    }

}

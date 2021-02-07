package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.CorgiVlogHot;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @author tairanliu
 */
@Component
public class CorgiHotVlogTask {
    @Reference
    private CorgiVlogService corgiVlogService;


    @Async
    @Scheduled(cron = "0 0 * * * *")
    //@Scheduled(fixedRate = 3600 * 1000)
    public void run() {
        int page = 1;
        int size = 1000;
        CorgiVlogHot query = new CorgiVlogHot();
        query.setType(CorgiVlogHot.TYPE.AUTO);
        query.setStatus(CorgiVlogHot.STATUS.OPEN);
        do {
            List<CorgiVlogHot> hotList = corgiVlogService.getHotVlog(query, page, size);
            if (CollectionUtils.isEmpty(hotList)) {
                break;
            }
            for (CorgiVlogHot hot : hotList) {
                Integer likeCount = hot.getLikeCount();
                if (likeCount * 10 > hot.getExpectView()) {
                    CorgiVlogHot update = new CorgiVlogHot();
                    update.setId(hot.getId());
                    update.setExpectView(likeCount * 10);
                    corgiVlogService.updateHotVlog(update);
                }
            }
            page++;
        } while (true);

    }

}

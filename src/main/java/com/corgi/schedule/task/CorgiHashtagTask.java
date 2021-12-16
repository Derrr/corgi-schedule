package com.corgi.schedule.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.CorgiHashtag;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

/**
 * @author tairanliu
 */
@Component
@Slf4j
public class CorgiHashtagTask {
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiVlogService corgiVlogService;

    @Async
    @Scheduled(cron = "0 0 * * * *")
    public void hourRefresh() {
        List<CorgiHashtag> hashtagList = corgiToolService.searchHashtag("", "");
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        calendar.add(Calendar.DATE, -1);
        if (hashtagList != null) {
            for (CorgiHashtag hashtag : hashtagList) {
                String time = hashtag.getCtime();
                if (sdf.format(calendar.getTime()).compareTo(time) > 0) {
                    continue;
                }
                Long viewCount = corgiVlogService.countHashtagView(hashtag.getHashtagId());
                hashtag.setViewCount(viewCount);
                corgiToolService.updateHashtag(hashtag);
            }
        }
    }

    @Async
    @Scheduled(cron = "0 0 3 * * *")
    public void dayRefresh() {
        List<CorgiHashtag> hashtagList = corgiToolService.searchHashtag("", "");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -30);

        Calendar calendar2 = Calendar.getInstance();
        calendar2.add(Calendar.DATE, -1);
        if (hashtagList != null) {
            for (CorgiHashtag hashtag : hashtagList) {
                String time = hashtag.getCtime();
                if (sdf.format(calendar.getTime()).compareTo(time) > 0 || sdf.format(calendar2.getTime()).compareTo(time) < 0) {
                    continue;
                }
                Long viewCount = corgiVlogService.countHashtagView(hashtag.getHashtagId());
                hashtag.setViewCount(viewCount);
                corgiToolService.updateHashtag(hashtag);
            }
        }
    }

    @Async
    @Scheduled(cron = "0 0 3 * * TUE")
    public void weedRefresh() {
        List<CorgiHashtag> hashtagList = corgiToolService.searchHashtag("", "");
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        calendar.add(Calendar.DATE, -30);
        if (hashtagList != null) {
            for (CorgiHashtag hashtag : hashtagList) {
                String time = hashtag.getCtime();
                if (sdf.format(calendar.getTime()).compareTo(time) < 0) {
                    continue;
                }
                Long viewCount = corgiVlogService.countHashtagView(hashtag.getHashtagId());
                hashtag.setViewCount(viewCount);
                corgiToolService.updateHashtag(hashtag);
            }
        }
    }

}

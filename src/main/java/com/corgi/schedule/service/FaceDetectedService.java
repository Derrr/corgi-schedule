package com.corgi.schedule.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.cloudauth.model.v20190307.DetectFaceAttributesRequest;
import com.aliyuncs.cloudauth.model.v20190307.DetectFaceAttributesResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiPic;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.entity.UserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.Random;
import java.util.UUID;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class FaceDetectedService {
    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    String REGION_ID = "cn-shanghai";

    private static final String NO_FACE = "no_face";

    private IAcsClient managementClient;

    private Random random = new Random(System.currentTimeMillis());

    @Reference
    private CorgiPicService corgiPicService;

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public CorgiPic checkFace(CorgiPic pic, String sourceId) {
        log.info("pics = " + pic.getPicUrl());

        DetectFaceAttributesRequest request = new DetectFaceAttributesRequest();
        request.setRegionId("cn-hangzhou");
        request.setMaterialValue(pic.getPicUrl());
        pic.setDataId(getDataId());
        pic.setStatus(CorgiPic.NORMAL);

        try {
            DetectFaceAttributesResponse response = managementClient.getAcsResponse(request);
            DetectFaceAttributesResponse.Data data = response.getData();
            log.info(JSONObject.toJSONString(data));
            if (CollectionUtils.isEmpty(data.getFaceInfos())) {
                pic.setStatus(NO_FACE);
                addCheckPic(pic, sourceId, CheckPic.AVATAR);
                return pic;
            }
            for (DetectFaceAttributesResponse.Data.FaceAttributesDetectInfo detectInfo : data.getFaceInfos()) {
                if (!"None".equals(detectInfo.getFaceAttributes().getFacetype())) {
                    return pic;
                }
            }
            pic.setStatus(NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        } catch (ServerException e) {
            log.error(e.getMessage(), e);
            pic.setStatus(NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
            pic.setStatus(NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        }
        return pic;
    }

    private void addCheckPic(CorgiPic corgiPic, String sourceId, String type) {
        CheckPic checkPic = new CheckPic();
        BeanUtils.copyProperties(corgiPic, checkPic);
        checkPic.setUserId(sourceId);
        checkPic.setType(type);
        corgiPicService.addCheckPic(checkPic);
    }

    private String getDataId() {
        return UUID.randomUUID().toString() + random.nextInt(100);
    }

}

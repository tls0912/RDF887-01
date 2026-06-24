package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.OcrVerificationRepository;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import com.czkuo.rdf88701.infra.mapper.OcrVerificationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class OcrVerificationRepositoryImpl implements OcrVerificationRepository {

    private final OcrVerificationMapper ocrVerificationMapper;

    public OcrVerificationRepositoryImpl(OcrVerificationMapper ocrVerificationMapper) {
        this.ocrVerificationMapper = ocrVerificationMapper;
    }

    @Override
    public Optional<OcrVerification> findById(Long id) {
        return Optional.ofNullable(ocrVerificationMapper.selectById(id));
    }

    @Override
    public boolean save(OcrVerification entity) {
        return ocrVerificationMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(OcrVerification entity) {
        return ocrVerificationMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ocrVerificationMapper.deleteById(id) > 0;
    }

    @Override
    public List<OcrVerification> findAll() {
        return ocrVerificationMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<OcrVerification> findByContainerMainId(Long containerMainId) {
        QueryWrapper<OcrVerification> qw = new QueryWrapper<>();
        qw.eq("container_main_id", containerMainId).last("LIMIT 1");
        return Optional.ofNullable(ocrVerificationMapper.selectOne(qw));
    }

    @Override
    public Optional<OcrVerification> findLatestByContainerMainIdAndRefContainerId(Long containerMainId, Long refContainerId) {
        QueryWrapper<OcrVerification> qw = new QueryWrapper<>();
        qw.eq("container_main_id", containerMainId)
                .eq("ref_container_id", refContainerId)
                // 取最新一筆：你若有 created_time 就用 created_time；沒有就用 id desc 也行
                .orderByDesc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(ocrVerificationMapper.selectOne(qw));
    }

    @Override
    public Optional<OcrVerification> findByS073Tid(String s073Tid) {
        QueryWrapper<OcrVerification> qw = new QueryWrapper<>();
        qw.eq("s073_tid", s073Tid)
                .last("LIMIT 1");
        return Optional.ofNullable(ocrVerificationMapper.selectOne(qw));
    }

    @Override
    public List<OcrVerification> findPendingManualDecisions(int limit) {
        QueryWrapper<OcrVerification> qw = new QueryWrapper<>();
        qw.eq("state", "ACTIVE")
                .eq("manual_decision", "PENDING")
                .orderByAsc("created_time")
                .last("LIMIT " + limit);
        return ocrVerificationMapper.selectList(qw);
    }

    @Override
    public boolean updateAutoFields(OcrVerification e) {
        if (e.getId() == null) return false;

        UpdateWrapper<OcrVerification> uw = new UpdateWrapper<>();
        uw.eq("id", e.getId())
                // 參考站點 / 容器
                .set("ref_site", e.getRefSite())
                .set("ref_container_id", e.getRefContainerId())
                // OCR 字串
                .set("curr_ocr_text1", e.getCurrOcrText1())
                .set("curr_ocr_text2", e.getCurrOcrText2())
                .set("ref_ocr_text1", e.getRefOcrText1())
                .set("ref_ocr_text2", e.getRefOcrText2())
                // 比對結果
                .set("part_match", e.getPartMatch())
                .set("ocr1_match", e.getOcr1Match())
                .set("ocr2_match", e.getOcr2Match())
                .set("bad_ocr", e.getBadOcr())
                .set("local_pass", e.getLocalPass())
                // 更新時間
                .set("updated_time", e.getUpdatedTime());

        // 不要在這裡 set manual_decision / manual_by / manual_time / final_result / s073_* 等人工相關欄位
        return ocrVerificationMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateS073Fields(OcrVerification e) {
        if (e.getId() == null) return false;

        UpdateWrapper<OcrVerification> uw = new UpdateWrapper<>();
        uw.eq("id", e.getId())
                // S073 TID & 狀態 & 結果碼
                .set("s073_tid", e.getS073Tid())
                .set("s073_status", e.getS073Status())
                .set("s073_result_code", e.getS073ResultCode())

                // S073 retry 控制欄位
                .set("s073_sent_time", e.getS073SentTime())
                .set("s073_retry_count", e.getS073RetryCount())
                .set("s073_last_retry_time", e.getS073LastRetryTime())
                .set("s073_next_retry_time", e.getS073NextRetryTime())

                // 影像路徑
                .set("curr_back_one_light_path", e.getCurrBackOneLightPath())
                .set("curr_back_three_light_path", e.getCurrBackThreeLightPath())
                .set("curr_front_one_light_path", e.getCurrFrontOneLightPath())
                .set("curr_front_three_light_path", e.getCurrFrontThreeLightPath())
                .set("ref_back_one_light_path", e.getRefBackOneLightPath())
                .set("ref_back_three_light_path", e.getRefBackThreeLightPath())
                .set("ref_front_one_light_path", e.getRefFrontOneLightPath())
                .set("ref_front_three_light_path", e.getRefFrontThreeLightPath())
                // 更新時間
                .set("updated_time", e.getUpdatedTime());

        // 同樣不要動 manualDecision / finalResult
        return ocrVerificationMapper.update(null, uw) > 0;
    }
}

package com.czkuo.rdf88701.application.service.zip;

import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.CancelDispatchOrder.CancelDispatchOrderPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CancelDispatchOrder.CancelDispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQueryPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.WipInfoUpdate.WipInfoUpdatePrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.WipInfoUpdate.WipInfoUpdateSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.checktimer.CheckTimerPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.checktimer.CheckTimerSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Header;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.infra.zip.ZipHeaders;
import com.czkuo.rdf88701.infra.zip.ZipHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ZipStockerCommandService
 * <p>
 * MCS → ZIP 指令發送邏輯：
 * - 組 Primary（Header: Direction=Primary, Sender=MCS）
 * - 呼叫 ZIP WebAPI，回傳對方 Secondary（Root<T>）
 * <p>
 * 新增便捷方法：
 * - queryAllSlots()：StatusQuery(Type=3, Name="*")
 * - queryPorts(...)：StatusQuery(Type=4)
 * - queryDispatchStatus()：StatusQuery(Type=5)
 * - queryInventory()：StatusQuery(Type=6)
 * - sendDispatchOrderSingle(...)：單筆派貨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZipStockerCommandService {

    private static final String SENDER = "MCS";
    private final ZipHttpClient client;

    /**
     * CheckTimer：MCS 校時到 ZIP
     */
    public Root<CheckTimerSecondaryBody> sendCheckTimer(
            ZipTarget target, int year, int mon, int day, int hour, int minute, int second) {

        var ti = new CheckTimerPrimaryBody.TimerInfo();
        ti.setYear(year);
        ti.setMon(mon);
        ti.setDay(day);
        ti.setHour(hour);
        ti.setMinute(minute);
        ti.setSecond(second);

        var body = new CheckTimerPrimaryBody();
        body.setTimerInfo(ti);

        Root<CheckTimerPrimaryBody> req = wrap("CheckTimer", body);
        return client.post(target, "/CheckTimer", req, CheckTimerSecondaryBody.class);
    }

    /**
     * DispatchOrder：出貨命令（Magazines + 選填 STK_PORT）
     */
    public Root<DispatchOrderSecondaryBody> sendDispatchOrder(
            ZipTarget target, List<String> magazines, String stkPort) {

        var body = new DispatchOrderPrimaryBody();
        body.setMagazines(magazines);
        body.setStkPort(stkPort);

        Root<DispatchOrderPrimaryBody> req = wrap("DispatchOrder", body);
        return client.post(target, "/DispatchOrder", req, DispatchOrderSecondaryBody.class);
    }

    /**
     * DispatchOrder（單筆懶人版）
     */
    public Root<DispatchOrderSecondaryBody> sendDispatchOrderSingle(
            ZipTarget target, String magazine, String stkPort) {
        return sendDispatchOrder(target, List.of(magazine), stkPort);
    }

    /**
     * CancelDispatchOrder：取消出貨命令
     */
    public Root<CancelDispatchOrderSecondaryBody> sendCancelDispatchOrder(
            ZipTarget target, List<String> magazines) {

        var body = new CancelDispatchOrderPrimaryBody();
        body.setMagazines(magazines);

        Root<CancelDispatchOrderPrimaryBody> req = wrap("CancelDispatchOrder", body);
        return client.post(target, "/CancelDispatchOrder", req, CancelDispatchOrderSecondaryBody.class);
    }

    /**
     * PortLockUnlock：Port 鎖定/解鎖（cmd: 1=Lock, 2=Unlock）
     */
    public Root<PortLockUnlockSecondaryBody> sendPortLockUnlock(
            ZipTarget target, String portName, int cmd) {

        var ci = new PortLockUnlockPrimaryBody.CmdInfo();
        ci.setName(portName);
        ci.setCmd(cmd);

        var body = new PortLockUnlockPrimaryBody();
        body.setCmdInfos(List.of(ci));

        Root<PortLockUnlockPrimaryBody> req = wrap("PortLockUnlock", body);
        return client.post(target, "/PortLockUnlock", req, PortLockUnlockSecondaryBody.class);
    }

    // -------------------- StatusQuery：通用與便捷 --------------------

    /**
     * StatusQuery（可送多筆 QueryInfos）
     */
    public Root<StatusQuerySecondaryBody> sendStatusQuery(
            ZipTarget target, List<StatusQueryPrimaryBody.QueryInfo> queries) {

        var body = new StatusQueryPrimaryBody();
        body.setQueryInfos(queries);

        Root<StatusQueryPrimaryBody> req = wrap("StatusQuery", body);
        return client.post(target, "/StatusQuery", req, StatusQuerySecondaryBody.class);
    }

    /**
     * StatusQuery（單筆懶人版）
     */
    public Root<StatusQuerySecondaryBody> sendStatusQuery(
            ZipTarget target, StatusQueryPrimaryBody.QueryInfo query) {
        return sendStatusQuery(target, List.of(query));
    }

    /**
     * 便捷：查詢所有儲格（Type=3, Name="*"）—給 monitor 每 30s 掃描用
     */
    public Root<StatusQuerySecondaryBody> queryAllSlots(ZipTarget target) {
        return sendStatusQuery(target, q(3, "*"));
    }

    /**
     * 便捷：查詢指定 Port 狀態（Type=4）
     */
    public Root<StatusQuerySecondaryBody> queryPorts(ZipTarget target, String... portNames) {
        List<StatusQueryPrimaryBody.QueryInfo> list = new ArrayList<>();
        Arrays.stream(portNames).forEach(p -> list.add(q(4, p)));
        return sendStatusQuery(target, list);
    }

    /**
     * 便捷：查詢派貨命令是否存在（Type=5）
     */
    public Root<StatusQuerySecondaryBody> queryDispatchStatus(ZipTarget target) {
        return sendStatusQuery(target, q(5, "*"));
    }

    /**
     * 便捷：查詢庫存水位（Type=6）
     */
    public Root<StatusQuerySecondaryBody> queryInventory(ZipTarget target) {
        return sendStatusQuery(target, q(6, "*"));
    }

    // -------------------- 工具區 --------------------

    /**
     * 建立單筆 QueryInfo
     */
    private StatusQueryPrimaryBody.QueryInfo q(int type, String name) {
        var qi = new StatusQueryPrimaryBody.QueryInfo();
        qi.setType(type);
        qi.setName(name);
        return qi;
    }

    /**
     * 共用包 Header：Direction=Primary, Sender=MCS
     */
    private <T> Root<T> wrap(String eventName, T body) {
        Root<T> r = new Root<>();
        Header h = ZipHeaders.of(eventName, "Primary", SENDER);
        r.setHeader(h);
        r.setBody(body);
        return r;
    }

    public Root<WipInfoUpdateSecondaryBody> sendWipInfoUpdate(
            ZipTarget target, String rawWipName, String carrierId, String lotId) {

        var body = new WipInfoUpdatePrimaryBody();
        var msg = new WipInfoUpdatePrimaryBody.Message();
        msg.setWipName(rawWipName);
        msg.setCarrierId(carrierId);
        msg.setLotId(lotId);
        body.setMessage(msg);
        Root<WipInfoUpdatePrimaryBody> req = wrap("WipInfoUpdate", body);
        return client.post(target, "/WipInfoUpdate", req, WipInfoUpdateSecondaryBody.class);
    }
}

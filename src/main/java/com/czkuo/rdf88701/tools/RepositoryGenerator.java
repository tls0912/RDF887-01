package com.czkuo.rdf88701.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Repository + RepositoryImpl 自動生成工具
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class RepositoryGenerator {

    private static final String ROOT_PACKAGE = "com.czkuo.rdf88701";
    private static final String DOMAIN_REPOSITORY_PATH = "/src/main/java/com/czkuo/rdf88701/domain/repository/";
    private static final String INFRA_REPOSITORY_IMPL_PATH = "/src/main/java/com/czkuo/rdf88701/infra/repository/impl/";

    private static final List<String> ENTITY_LIST = Arrays.asList(
//            "ContainerData",
//            "ContainerDataHistory",
//            "ContainerMain",
//            "ContainerMainHistory",
//            "LocationPoint",
//            "GripperTask",
//            "GripperTaskHistory",
//            "GripperStatusTransition",
//            "GripperRequest",
//            "GripperRequestHistory",
//            "GripperAnomalyLog",
//            "ProductData",
//            "ProductDataHistory",
//            "ProductMain",
//            "ProductMainHistory"

//            "crane_task_follow_up_record"
//            "auto_walk_config"
//            "location_reservation_record",
//            "location_reservation_history"
//            "working_beam",
//            "working_beam_request",
//            "working_beam_request_history",
//            "working_beam_task",
//            "working_beam_task_history",
//            "working_beam_control_range"

//            "transfer",
//            "transfer_request",
//            "transfer_request_history",
//            "transfer_task",
//            "transfer_task_history"

//            "mqtt_message_log"

//            "mqtt_connection_state",
//            "mqtt_connection_log"

//            "mqtt_event_log",
//            "mqtt_event_status_log"

//            "infrared",
//            "infrared_request",
//            "infrared_request_history",
//            "infrared_task",
//            "infrared_task_history"

//            "labeling_info"

//            "container_attr"

//            "safety_device_type",
//            "safety_device_type",
//            "safety_point",
//            "safety_status_snapshot",
//            "safety_event_log"

//            "hmi_display_task"

//            "door_access_info"

//            "start_access_info"

//            "strapping_precheck_result"

//            "alarm_item",
//            "alarm_item_log"

//            "mqtt_inbox",
//            "mqtt_inbox_status_log"

//            "robot_in_r007",
//            "robot_r007_task"
//            "robot_r007_task_hist",
//            "robot_r007_feedback"

//            "robot_in_r008",
//            "robot_r008_task"

//            "robot_in_r029",
//            "robot_in_r029_lot"
//            "robot_r029_task",
//            "r029_output_item"

//            "robot_in_r031",
//            "robot_r031_task"

//            "site_bidir_route"

//            "ocr_device",
//            "ocr_task",
//            "ocr_alarm"

//            "tool_catalog",
//            "tool_limit_override",
//            "tool_status"

//            "strapping_log"

//            "l005_session"

//            "s072_session"

//            "image_asset"

//            "ocr_verification"

//            "button_log"

            "tt_signal_def",
            "tt_record",
            "tt_record_item"
    );

    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");

        for (String tableName : ENTITY_LIST) {
            String entityName = toPascalCase(tableName);  // 將 snake_case → PascalCase
            createRepositoryInterface(projectPath, entityName);
            createRepositoryImpl(projectPath, entityName);
        }
    }

    private static void createRepositoryInterface(String projectPath, String entity) {
        String interfaceName = entity + "Repository";
        String filePath = projectPath + DOMAIN_REPOSITORY_PATH + interfaceName + ".java";

        String content = String.format("""
                package %s.domain.repository;

                import %s.infra.entity.%s;
                import java.util.List;
                import java.util.Optional;

                public interface %s {

                    Optional<%s> findById(Long id);

                    boolean save(%s entity);

                    boolean update(%s entity);

                    boolean deleteById(Long id);

                    List<%s> findAll();
                }
                """,
                ROOT_PACKAGE, ROOT_PACKAGE, entity, interfaceName, entity, entity, entity, entity);

        writeFile(filePath, content);
    }

    private static void createRepositoryImpl(String projectPath, String entity) {
        String repositoryName = entity + "Repository";
        String implName = entity + "RepositoryImpl";
        String mapperName = entity + "Mapper";

        String filePath = projectPath + INFRA_REPOSITORY_IMPL_PATH + implName + ".java";

        String content = String.format("""
                package %s.infra.repository.impl;

                import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
                import %s.domain.repository.%s;
                import %s.infra.entity.%s;
                import %s.infra.mapper.%s;
                import org.springframework.stereotype.Repository;

                import java.util.List;
                import java.util.Optional;

                @Repository
                public class %s implements %s {

                    private final %s %s;

                    public %s(%s %s) {
                        this.%s = %s;
                    }

                    @Override
                    public Optional<%s> findById(Long id) {
                        return Optional.ofNullable(%s.selectById(id));
                    }

                    @Override
                    public boolean save(%s entity) {
                        return %s.insert(entity) > 0;
                    }

                    @Override
                    public boolean update(%s entity) {
                        return %s.updateById(entity) > 0;
                    }

                    @Override
                    public boolean deleteById(Long id) {
                        return %s.deleteById(id) > 0;
                    }

                    @Override
                    public List<%s> findAll() {
                        return %s.selectList(new QueryWrapper<>());
                    }
                }
                """,
                ROOT_PACKAGE, ROOT_PACKAGE, repositoryName, ROOT_PACKAGE, entity, ROOT_PACKAGE, mapperName,
                implName, repositoryName,
                mapperName, lowerFirst(mapperName),
                implName, mapperName, lowerFirst(mapperName), lowerFirst(mapperName), lowerFirst(mapperName),
                entity, lowerFirst(mapperName),
                entity, lowerFirst(mapperName),
                entity, lowerFirst(mapperName),
                lowerFirst(mapperName),
                entity, lowerFirst(mapperName)
        );

        writeFile(filePath, content);
    }

    private static void writeFile(String filePath, String content) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs(); // 確保資料夾存在
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }
            System.out.println("生成成功：" + filePath);
        } catch (IOException e) {
            throw new RuntimeException("寫檔錯誤：" + filePath, e);
        }
    }

    private static String lowerFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    private static String toPascalCase(String snake) {
        StringBuilder result = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }
        return result.toString();
    }
}

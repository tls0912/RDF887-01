package com.czkuo.rdf88701.tools;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class CodeGenerator {

    public static void main(String[] args) {
        FastAutoGenerator.create(
                        "jdbc:mysql://localhost:3306/rdf887_01?useSSL=false&serverTimezone=Asia/Taipei",
                        "root",
                        "1192"
                )
                .globalConfig(builder -> {
                    builder.author("czkuo")
                            .disableOpenDir()
                            .outputDir(System.getProperty("user.dir") + "/src/main/java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.czkuo.rdf88701.infra")
                            .entity("entity")
                            .mapper("mapper")
                            .pathInfo(Collections.singletonMap(
                                    OutputFile.xml,
                                    System.getProperty("user.dir") + "/src/main/resources/mapper"
                            ));
                })
                .strategyConfig(builder -> {
                    builder.addInclude(
//                                    "container_data",
//                                    "container_data_history",
//                                    "container_main",
//                                    "container_main_history",
//                                    "location_point",
//                                    "location_tracking",
//                                    "location_flow",
//                                    "crane_task",
//                                    "crane_task_history",
//                                    "crane_request",
//                                    "crane_request_history",
//                                    "gripper_task",
//                                    "gripper_task_history",
//                                    "gripper_status_transition",
//                                    "gripper_request",
//                                    "gripper_request_history",
//                                    "gripper_anomaly_log",
//                                    "product_data",
//                                    "product_data_history",
//                                    "product_main",
//                                    "product_main_history",
//
//                                      "roles",
//                                      "permissions",
//                                      "role_permissions",
//                                      "users"

//                                      "crane_task_follow_up_record"
//                                      "auto_walk_config"
//                                "location_reservation_record",
//                                "location_reservation_history"
//                                    "working_beam",
//                                    "working_beam_request",
//                                    "working_beam_request_history",
//                                    "working_beam_task",
//                                    "working_beam_task_history",
//                                    "working_beam_control_range"
//                            "transfer",
//                            "transfer_request",
//                            "transfer_request_history",
//                            "transfer_task",
//                            "transfer_task_history"

//                            "mqtt_message_log"

//                            "mqtt_connection_state",
//                            "mqtt_connection_log"
//
//                            "mqtt_event_log",
//                            "mqtt_event_status_log"

//                            "infrared",
//                            "infrared_request",
//                            "infrared_request_history",
//                            "infrared_task",
//                            "infrared_task_history"

//                            "labeling_info"

//                            "container_attr"

//                            "safety_device_type",
//                            "safety_device_type",
//                            "safety_point",
//                            "safety_status_snapshot",
//                            "safety_event_log"

//                            "hmi_display_task"

//                            "door_access_info"

//                            "start_access_info"

//                            "strapping_precheck_result"

//                            "alarm_item",
//                            "alarm_item_log"

//                            "mqtt_inbox",
//                            "mqtt_inbox_status_log"

//                            "robot_in_r007",
//                            "robot_r007_task"
//                            "robot_r007_task_hist",
//                            "robot_r007_feedback"

//                            "robot_in_r008",
//                            "robot_r008_task"

//                            "robot_in_r029",
//                            "robot_in_r029_lot"
//                            "robot_r029_task",
//                            "r029_output_item"

//                            "robot_in_r031",
//                            "robot_r031_task"

//                            "site_bidir_route"

//                            "ocr_device",
//                            "ocr_task",
//                            "ocr_alarm"

//                            "tool_catalog",
//                            "tool_limit_override",
//                            "tool_status"

//                            "strapping_log"

//                            "l005_session"

//                            "s072_session"

//                            "image_asset"

//                            "ocr_verification"

//                            "button_log"

                            "tt_signal_def",
                            "tt_record",
                            "tt_record_item"

                            )
                            .addTablePrefix("t_", "sys_")

                            .entityBuilder()
                            .enableLombok()
                            .enableFileOverride()
                            .naming(NamingStrategy.underline_to_camel)
                            .columnNaming(NamingStrategy.underline_to_camel)
                            .disableSerialVersionUID()

                            .mapperBuilder()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .enableFileOverride()
                            .enableMapperAnnotation()

                            .serviceBuilder()
                            .enableFileOverride()
                            .disable()

                            .controllerBuilder()
                            .enableFileOverride()
                            .disable();
                })
                .templateConfig(builder -> {
                    builder
                            .service(null)       //  不產生 service
                            .serviceImpl(null)   //  不產生 serviceImpl
                            .controller(null);   //  不產生 controller
                })
                .templateEngine(new VelocityTemplateEngine())
                .execute();
    }
}

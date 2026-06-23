package com.czkuo.rdf88701.application.dto.command;

import com.czkuo.rdf88701.common.enums.EntryType;
import com.czkuo.rdf88701.common.enums.ExitType;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/**
 * DTOs for LocationAccounting operations (Entry / Exit / Transfer)
 */
public class LocationAccountingCommand {

    @Data
    public static class EntryRequest {
        @NotNull
        private Long containerMainId;

        @NotNull
        private Long locationPointId;

        @NotNull
        private EntryType entryType;

        private String operator;
        private Long sourceTaskId;
    }

    @Data
    public static class ExitRequest {
        @NotNull
        private Long containerMainId;

        @NotNull
        private ExitType exitType;

        private String operator;
    }

    @Data
    public static class TransferRequest {
        @NotNull
        private Long containerMainId;

        @NotNull
        private Long toLocationPointId;

        @NotNull
        private EntryType entryType;

        @NotNull
        private ExitType exitType;

        private String operator;
        private Long sourceTaskId;
    }

    /**
     * 外部呼叫專用（以 containerCode / locationName 為主）
     */
    @Data
    public static class EntryByCodeRequest {
        @NotNull
        private String containerCode;

        @NotNull
        private String locationName;

        @NotNull
        private EntryType entryType;

        private String operator;
        private Long sourceTaskId;
    }
}
